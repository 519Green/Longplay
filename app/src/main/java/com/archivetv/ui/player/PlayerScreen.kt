package com.archivetv.ui.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.archivetv.ArchiveTvApp
import com.archivetv.data.isResumable
import com.archivetv.data.startPoint
import com.archivetv.domain.PlayableFile
import com.archivetv.playback.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Plays every file of [identifier] as one playlist. [startIndex] picks the
 * file (from an episode row or Next Up); null means "where I left off".
 * Positions are saved per file every 10 s, on file completion, and on exit.
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
        onDispose {
            saveCurrent(blocking = true)
            controller.release()
        }
    }

    LaunchedEffect(identifier) {
        // Video and the audio queue never play together; audio stays paused afterwards.
        app.container.audio.pause()
        val detail = runCatching { app.container.archiveRepository.detail(identifier) }.getOrNull()
        val list = detail?.files.orEmpty()
        if (list.isEmpty()) { onExit(); return@LaunchedEffect }
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

    val multiFile = files.size > 1
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = controller.player
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = false
                controllerShowTimeoutMs = 5_000
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(Color.BLACK)
                setShutterBackgroundColor(Color.BLACK)
                keepScreenOn = true
                requestFocus()
            }
        },
        update = { view ->
            view.setShowNextButton(multiFile)
            view.setShowPreviousButton(multiFile)
        },
    )
}
