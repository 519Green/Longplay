package com.archivetv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    fun start(url: String, resumeFromMs: Long) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (resumeFromMs > 0) player.seekTo(resumeFromMs)
        player.playWhenReady = true
    }

    fun positionMs(): Long = player.currentPosition
    fun durationMs(): Long = player.duration.coerceAtLeast(0L)
    fun release() = player.release()
}
