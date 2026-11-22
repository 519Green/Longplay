package com.archivetv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.data.episodeLabels
import com.archivetv.data.isResumable
import com.archivetv.data.startPoint
import com.archivetv.domain.PlayableFile
import com.archivetv.playback.PlayerController
import com.archivetv.playback.SEEK_STEP_MS
import com.archivetv.ui.theme.Dimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Why the player is not playing, and how to try again (null: nothing to retry). */
private class PlayerFailure(val message: String, val retry: (() -> Unit)?)

/**
 * Plays every file of [identifier] as one playlist. [startIndex] picks the
 * file (from an episode row or Next Up); null means "where I left off".
 * Positions are saved per file every 10 s, on file completion, and on exit.
 *
 * Above the Media3 controller (research spec 2.8): the title while the
 * controls show, "Up next" in the last 20 s of an episode, Left/Right seek
 * without opening the controls, Back hides the controls before it exits,
 * and an error panel with Retry replaces the old silent exit.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(identifier: String, startIndex: Int?, onExit: () -> Unit, fromStart: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as ArchiveTvApp
    val library = app.container.libraryRepository
    val controller = remember { PlayerController(context) }
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<PlayableFile>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<PlayerFailure?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var upNext by remember { mutableStateOf<String?>(null) }
    var seekText by remember { mutableStateOf<String?>(null) }
    var seeks by remember { mutableIntStateOf(0) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    // Last known real duration per file index, reported by the player.
    val durations = remember { mutableMapOf<Int, Long>() }

    fun save(index: Int, positionMs: Long, durationMs: Long) {
        val f = files.getOrNull(index) ?: return
        scope.launch { library.saveResume(identifier, f.fileName, index, files.size, positionMs, durationMs) }
    }

    fun saveCurrent(blocking: Boolean = false) {
        val f = files.getOrNull(controller.currentIndex()) ?: return
        val index = controller.currentIndex()
        val write = suspend {
            library.saveResume(
                identifier, f.fileName, index, files.size, controller.positionMs(), controller.durationMs(),
            )
        }
        if (blocking) runCatching { runBlocking { write() } } else scope.launch { write() }
    }

    DisposableEffect(Unit) {
        controller.onFileCompleted = { index ->
            val known = durations[index]?.takeIf { it > 0 }
                ?: files.getOrNull(index)?.durationSec?.let { (it * 1000).toLong() }
                ?: 1L
            save(index, known, known)
        }
        controller.onPlaylistEnded = { onExit() }
        controller.onError = { e ->
            saveCurrent()
            failure = PlayerFailure(playbackErrorText(e.errorCode)) {
                failure = null
                controller.retry()
            }
        }
        onDispose {
            saveCurrent(blocking = true)
            controller.release()
        }
    }

    LaunchedEffect(identifier, loadAttempt) {
        // Video and the audio queue never play together; audio stays paused afterwards.
        app.container.audio.pause()
        val detail = try {
            app.container.archiveRepository.detail(identifier)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (detail == null) {
            failure = PlayerFailure("Couldn't load this item from Archive.org.") {
                failure = null
                loadAttempt++
            }
            return@LaunchedEffect
        }
        val list = detail.files
        if (list.isEmpty()) {
            failure = PlayerFailure("This item has no video file that plays here.", retry = null)
            return@LaunchedEffect
        }
        title = detail.item.title
        files = list
        val names = list.map { it.fileName }
        val progress = library.progress(identifier, names)
        val (index, positionMs) = if (fromStart) {
            (startIndex?.takeIf { it in list.indices } ?: 0) to 0L
        } else if (startIndex != null && startIndex in list.indices) {
            val saved = progress[names[startIndex]]
            startIndex to (saved?.takeIf { isResumable(it.positionMs, it.durationMs) }?.positionMs ?: 0L)
        } else {
            startPoint(names, progress).let { it.index to it.positionMs }
        }
        controller.start(list, index, positionMs)
        while (isActive) {
            delay(10_000)
            durations[controller.currentIndex()] = controller.durationMs()
            saveCurrent()
        }
    }

    // Episode names without the show name they all share.
    val labels = remember(files) {
        if (files.size > 1) episodeLabels(files.map { it.title }) else files.map { it.title }
    }
    LaunchedEffect(labels) {
        if (labels.isEmpty()) return@LaunchedEffect
        while (isActive) {
            index = controller.currentIndex()
            upNext = upNextText(index, controller.positionMs(), controller.durationMs(), labels)
            delay(500)
        }
    }
    LaunchedEffect(seeks) {
        delay(1_500)
        seekText = null
    }

    // Back first hides the controls (research spec 2.8); with them hidden it leaves.
    BackHandler(enabled = failure == null && controlsVisible) { playerView?.hideController() }

    val multiFile = files.size > 1
    val hasCaptions = files.any { it.subtitles.isNotEmpty() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val f = failure
        if (f != null) {
            PlayerError(f, onBack = onExit)
            return@Box
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TvPlayerView(ctx).apply {
                    player = controller.player
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = false
                    controllerShowTimeoutMs = 5_000
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    // Captions follow the system caption settings (size, colors).
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE },
                    )
                    onSeek = { forward ->
                        val p = controller.player
                        val step = if (forward) "+${SEEK_STEP_MS / 1000} s" else "−${SEEK_STEP_MS / 1000} s"
                        seekText = "$step  ${clockText(p.currentPosition)} / ${clockText(controller.durationMs())}"
                        seeks++
                    }
                    keepScreenOn = true
                    requestFocus()
                    playerView = this
                }
            },
            update = { view ->
                view.setShowNextButton(multiFile)
                view.setShowPreviousButton(multiFile)
                view.setShowSubtitleButton(hasCaptions)
            },
            onRelease = { view ->
                view.player = null
                playerView = null
            },
        )
        AnimatedVisibility(
            visible = controlsVisible && title.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
            TitleBar(title, labels.getOrNull(index)?.takeIf { multiFile })
        }
        seekText?.let { text ->
            OverlayChip(text, Modifier.align(Alignment.TopCenter).padding(top = Dimens.OverscanVertical + 8.dp))
        }
        upNext?.let { text ->
            OverlayChip(
                text,
                Modifier
                    .align(Alignment.BottomEnd)
                    // Above the controller's seek bar while it shows.
                    .padding(end = Dimens.OverscanHorizontal, bottom = if (controlsVisible) 140.dp else 48.dp),
            )
        }
    }
}

@Composable
private fun TitleBar(title: String, episode: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .padding(
                start = Dimens.OverscanHorizontal,
                end = Dimens.OverscanHorizontal,
                top = Dimens.OverscanVertical,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverlayChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 520.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun PlayerError(failure: PlayerFailure, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(failure) {
        // The player view held focus and has just left the screen.
        repeat(10) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.OverscanHorizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Can't play this", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(failure.message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            failure.retry?.let { retry ->
                Button(onClick = retry, modifier = Modifier.focusRequester(first)) { Text("Retry") }
            }
            Button(
                onClick = onBack,
                modifier = if (failure.retry == null) Modifier.focusRequester(first) else Modifier,
            ) { Text("Back") }
        }
    }
}

/**
 * Left/Right seek straight away while the controls are hidden; the stock
 * view would only open the controls on the first press. With the controls
 * showing, the keys move between buttons as usual.
 */
@SuppressLint("ViewConstructor")
@OptIn(UnstableApi::class)
private class TvPlayerView(context: Context) : PlayerView(context) {
    var onSeek: ((forward: Boolean) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> true
            KeyEvent.KEYCODE_DPAD_LEFT -> false
            else -> null
        }
        val p = player
        if (forward == null || p == null || isControllerFullyVisible) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (forward) p.seekForward() else p.seekBack()
            onSeek?.invoke(forward)
        }
        return true
    }
}
