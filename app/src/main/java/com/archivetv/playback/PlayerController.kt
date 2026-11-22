package com.archivetv.playback

import android.content.Context
import androidx.annotation.OptIn
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.archivetv.domain.PlayableFile

@OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        // Left/Right on the remote and the controller's rewind/fast-forward.
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    /** Called when playback moves past a file on its own (it played to the end). */
    var onFileCompleted: ((index: Int) -> Unit)? = null

    /** Called once the last file in the playlist has ended. */
    var onPlaylistEnded: (() -> Unit)? = null

    /** Called when playback stops on an error; [retry] picks up where it stopped. */
    var onError: ((PlaybackException) -> Unit)? = null

    private var currentIndex = 0

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = currentIndex
            currentIndex = player.currentMediaItemIndex
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) onFileCompleted?.invoke(previous)
        }

        override fun onPlayerError(error: PlaybackException) {
            onError?.invoke(error)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                onFileCompleted?.invoke(player.currentMediaItemIndex)
                onPlaylistEnded?.invoke()
            }
        }
    }

    init {
        player.addListener(listener)
    }

    /** Plays [files] as one playlist from [startIndex], seeking to [resumeFromMs] in it. */
    fun start(files: List<PlayableFile>, startIndex: Int, resumeFromMs: Long) {
        val items = files.map { f ->
            MediaItem.Builder()
                .setUri(f.url)
                .setMediaId(f.fileName)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(f.title).build())
                // Side-loaded captions start off; the controller's captions button picks one.
                .setSubtitleConfigurations(
                    f.subtitles.map { sub ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                            .setMimeType(if (sub.webVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage(sub.language)
                            .setLabel(sub.label)
                            .setId(sub.fileName)
                            .build()
                    },
                )
                .build()
        }
        currentIndex = startIndex
        player.setMediaItems(items, startIndex, resumeFromMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    /** After an error: prepares again, which resumes at the position it stopped. */
    fun retry() {
        player.prepare()
        player.playWhenReady = true
    }

    fun currentIndex(): Int = player.currentMediaItemIndex
    fun positionMs(): Long = player.currentPosition
    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

/** Remote Left/Right and the rewind/fast-forward buttons move this far. */
const val SEEK_STEP_MS = 10_000L
