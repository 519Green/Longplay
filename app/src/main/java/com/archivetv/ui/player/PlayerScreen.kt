package com.archivetv.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.archivetv.ArchiveTvApp
import com.archivetv.playback.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(identifier: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ArchiveTvApp
    val controller = remember { PlayerController(context) }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    LaunchedEffect(identifier) {
        val detail = runCatching { app.container.archiveRepository.detail(identifier) }
            .getOrNull()
        val url = detail?.playable?.url
        if (url == null) { onExit(); return@LaunchedEffect }
        val resume = app.container.libraryRepository.resumePosition(identifier)
        controller.start(url, resume)
        while (isActive) {
            delay(10_000)
            app.container.libraryRepository.saveResume(
                identifier, controller.positionMs(), controller.durationMs())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Persist final position synchronously-ish on exit.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    app.container.libraryRepository.saveResume(
                        identifier, controller.positionMs(), controller.durationMs())
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { PlayerView(it).apply { player = controller.player } },
    )
}
