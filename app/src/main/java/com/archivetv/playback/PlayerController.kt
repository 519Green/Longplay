package com.archivetv.playback

import android.content.Context
import androidx.annotation.OptIn
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.archivetv.domain.PlayableFile
import com.archivetv.ui.player.nextVideoSessionId

@OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        // Left/Right on the remote and the controller's rewind/fast-forward.
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    /**
     * The video's own media session, so the remote's media keys reach the video and not the
     * background audio session (which is paused while a video plays). Released with the player.
     */
    val session: MediaSession = MediaSession.Builder(context, player).setId(nextVideoSessionId()).build()

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

    /** Moves the position by [deltaMs], kept within the file. */
    fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (duration == C.TIME_UNSET) target else target.coerceAtMost(duration))
    }

    /** Labels of the current file's caption tracks, in track order. */
    fun captionLabels(): List<String> = textTracks().map { it.label }.distinct()

    /** The label of the caption track showing now, or null when captions are off. */
    fun selectedCaption(): String? = textTracks().firstOrNull { it.selected }?.label

    /** Shows the caption track labelled [trackLabel]; null turns captions off. */
    fun selectCaption(trackLabel: String?) {
        val builder = player.trackSelectionParameters.buildUpon()
        val match = trackLabel?.let { label -> textTracks().firstOrNull { it.label == label } }
        if (match == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(match.group, match.index))
        }
        player.trackSelectionParameters = builder.build()
    }

    private class TextTrack(
        val group: androidx.media3.common.TrackGroup,
        val index: Int,
        val label: String,
        val selected: Boolean,
    )

    private fun textTracks(): List<TextTrack> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                val format = g.getTrackFormat(i)
                TextTrack(g.mediaTrackGroup, i, format.label ?: format.language ?: "Captions", g.isTrackSelected(i))
            }
        }

    fun release() {
        player.removeListener(listener)
        session.release()
        player.release()
    }
}

/** Remote Left/Right and the rewind/fast-forward buttons move this far. */
const val SEEK_STEP_MS = 10_000L
