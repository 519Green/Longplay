package com.archivetv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
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
 * Our own D-pad control bar replaces the Media3 controller (rules in PlayerKeys.kt): the title
 * while the bar shows, "Up next" in the last 20 s of an episode, Left/Right seek without opening
 * the bar, media keys act at once, Back closes the caption list, then the bar, then the player,
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
    // Shown at the start, as the stock controller did, so the title and position are visible.
    var barVisible by remember { mutableStateOf(true) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var captionsOpen by remember { mutableStateOf(false) }
    // Bumped on every key while the bar shows; restarts the hide timer.
    var lastKeyAt by remember { mutableIntStateOf(0) }
    // Bumped to move focus to Play/Pause once the bar is on screen.
    var focusPlayRequests by remember { mutableIntStateOf(1) }
    var bar by remember { mutableStateOf(BarState(0L, 0L, false, BarButtons(false, false))) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    // Key codes whose KeyDown we handled; their KeyUp is consumed too.
    val consumed = remember { mutableSetOf<Int>() }
    var index by remember { mutableIntStateOf(0) }
    var upNext by remember { mutableStateOf<String?>(null) }
    var seekText by remember { mutableStateOf<String?>(null) }
    var seeks by remember { mutableIntStateOf(0) }
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

    val multiFile = files.size > 1

    // Bar contents, refreshed while it shows.
    LaunchedEffect(barVisible) {
        while (isActive && barVisible) {
            val p = controller.player
            playbackState = p.playbackState
            bar = BarState(
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = controller.durationMs(),
                isPlaying = p.isPlaying,
                buttons = barButtons(files.size, controller.captionLabels().size),
            )
            delay(250)
        }
    }
    // Hides 5 s after the last key, only while the picture is moving.
    LaunchedEffect(barVisible, lastKeyAt, bar.isPlaying, playbackState, captionsOpen) {
        if (barVisible && !captionsOpen && autoHides(bar.isPlaying, playbackState)) {
            delay(5_000)
            barVisible = false
        }
    }
    // Focus follows the bar: Play/Pause when it opens, the screen itself when it closes,
    // so keys keep reaching the handler below.
    LaunchedEffect(barVisible, focusPlayRequests, failure) {
        if (failure != null) return@LaunchedEffect
        val target = if (barVisible) playPauseFocus else rootFocus
        if (!barVisible) seekBarFocused = false
        repeat(10) {
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    fun showBar(focusPlay: Boolean) {
        val wasHidden = !barVisible
        barVisible = true
        lastKeyAt++
        if (focusPlay || wasHidden) focusPlayRequests++
    }

    fun seek(forward: Boolean) {
        controller.seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
        val step = if (forward) "+${SEEK_STEP_MS / 1000} s" else "−${SEEK_STEP_MS / 1000} s"
        seekText = "$step  ${clockText(controller.player.currentPosition)} / ${clockText(controller.durationMs())}"
        seeks++
    }

    fun act(action: KeyAction, keyCode: Int) {
        val p = controller.player
        val mediaKey = KeyEvent.isMediaSessionKey(keyCode)
        when (action) {
            KeyAction.SEEK_BACK, KeyAction.SEEK_FORWARD -> {
                seek(action == KeyAction.SEEK_FORWARD)
                if (mediaKey) showBar(focusPlay = false) else if (barVisible) lastKeyAt++
            }
            KeyAction.PLAY_PAUSE -> {
                if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause() else p.play()
                showBar(focusPlay = false)
            }
            KeyAction.PLAY -> { p.play(); showBar(focusPlay = false) }
            KeyAction.PAUSE -> { p.pause(); showBar(focusPlay = false) }
            KeyAction.NEXT -> { p.seekToNext(); showBar(focusPlay = false) }
            KeyAction.PREVIOUS -> { p.seekToPrevious(); showBar(focusPlay = false) }
            KeyAction.SHOW_BAR -> showBar(focusPlay = true)
            KeyAction.CLOSE_CAPTIONS -> { captionsOpen = false; focusPlayRequests++ }
            KeyAction.HIDE_BAR -> barVisible = false
            KeyAction.EXIT -> onExit()
            KeyAction.PASS -> Unit
        }
    }

    fun back() = act(keyAction(KeyEvent.KEYCODE_BACK, barVisible, seekBarFocused, captionsOpen), KeyEvent.KEYCODE_BACK)

    // Back follows the same order as the other keys: caption list, then bar, then leave.
    // Normally the key handler below takes Back; this covers the moment nothing has focus.
    BackHandler(enabled = failure == null) { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                if (failure != null) return@onPreviewKeyEvent false
                // Taken here because Compose spends an unhandled Back on clearing focus, which
                // swallowed the first press. Acted on at key-up, so the release cannot reach the
                // screen underneath once the player has left.
                if (code == KeyEvent.KEYCODE_BACK) {
                    if (e.type == KeyEventType.KeyUp) back()
                    return@onPreviewKeyEvent true
                }
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        val action = keyAction(code, barVisible, seekBarFocused, captionsOpen)
                        if (action == KeyAction.PASS) {
                            if (barVisible) lastKeyAt++
                            false
                        } else {
                            consumed += code
                            act(action, code)
                            true
                        }
                    }
                    KeyEventType.KeyUp -> consumed.remove(code)
                    else -> false
                }
            }
            .focusable(),
    ) {
        val f = failure
        if (f != null) {
            PlayerError(f, onBack = onExit)
            return@Box
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = false
                    // The Compose screen around it takes every key.
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    // Captions follow the system caption settings (size, colors).
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    keepScreenOn = true
                }
            },
            onRelease = { view -> view.player = null },
        )
        AnimatedVisibility(
            visible = barVisible && title.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
            TitleBar(title, labels.getOrNull(index)?.takeIf { multiFile })
        }
        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            PlayerControls(
                state = bar,
                playPauseFocus = playPauseFocus,
                onSeekBarFocus = { seekBarFocused = it },
                onPlayPause = { act(KeyAction.PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER) },
                onPrevious = { controller.player.seekToPrevious(); lastKeyAt++ },
                onNext = { controller.player.seekToNext(); lastKeyAt++ },
                onCaptions = { captionsOpen = true },
            )
        }
        seekText?.let { text ->
            OverlayChip(text, Modifier.align(Alignment.TopCenter).padding(top = Dimens.OverscanVertical + 8.dp))
        }
        upNext?.let { text ->
            OverlayChip(
                text,
                Modifier
                    .align(Alignment.BottomEnd)
                    // Above the control bar while it shows.
                    .padding(end = Dimens.OverscanHorizontal, bottom = if (barVisible) 200.dp else 48.dp),
            )
        }
        if (captionsOpen) {
            CaptionPicker(
                labels = controller.captionLabels(),
                selected = controller.selectedCaption(),
                onPick = { controller.selectCaption(it) },
                onDismiss = { act(KeyAction.CLOSE_CAPTIONS, KeyEvent.KEYCODE_BACK) },
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
