package com.archivetv.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.archivetv.ArchiveTvApp
import com.archivetv.MainActivity
import com.archivetv.data.db.QueueStateEntity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Intent extra that asks MainActivity to open Now Playing. */
const val EXTRA_OPEN_NOW_PLAYING = "open_now_playing"

/**
 * Plays the audio queue in the background. The player's playlist is the
 * queue: it is mirrored to Room on every change and restored (paused) when
 * the service starts, and each file's position is saved for resume.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as ArchiveTvApp).container

    private var persistJob: Job? = null
    private var tickJob: Job? = null
    private var idleJob: Job? = null
    private var lastIndex = C.INDEX_UNSET

    /** Real durations reported by the player, by media id (indices shift as the queue changes). */
    private val durations = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_NOW_PLAYING, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .build()
        scope.launch { restore() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            playableItems(mediaItems)?.let { Futures.immediateFuture(it.toMutableList()) }
                ?: Futures.immediateFailedFuture(UnsupportedOperationException("item has no playable URI"))
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = lastIndex
            lastIndex = player.currentMediaItemIndex
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previous != C.INDEX_UNSET) saveFinished(previous)
            persistState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            lastIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex else C.INDEX_UNSET
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) schedulePersist()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            tickJob?.cancel()
            idleJob?.cancel()
            if (isPlaying) {
                tickJob = scope.launch {
                    while (isActive) {
                        delay(10_000)
                        saveCurrent()
                        persistState()
                    }
                }
            } else {
                saveCurrent()
                persistState()
                // Paused for a long time: let the service go; the queue stays in Room.
                idleJob = scope.launch {
                    delay(30 * 60_000L)
                    if (!player.isPlaying) stopSelf()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                saveFinished(player.currentMediaItemIndex)
                player.clearMediaItems()
                persistNow()
                stopSelf()
            }
        }

        /**
         * A file that will not play (often a taken-down upload): skip it rather
         * than stall, keeping play/pause as it was. Other errors (network) stay
         * on the file; Play prepares it again.
         */
        override fun onPlayerError(error: PlaybackException) {
            if (skipsToNextFile(error.errorCode) && player.hasNextMediaItem()) {
                val wasPlaying = player.playWhenReady
                player.seekToNextMediaItem()
                player.prepare()
                player.playWhenReady = wasPlaying
            } else {
                player.pause()
            }
        }
    }

    private suspend fun restore() {
        val dao = container.queueDao
        val (entries, state) = withContext(Dispatchers.IO) { dao.entries() to dao.state() }
        // A command may have filled the player while Room was being read; it wins.
        if (entries.isEmpty() || state == null || state.currentIndex < 0 || player.mediaItemCount > 0) return
        val items = entries.map { it.toEntry().toMediaItem() }
        player.setMediaItems(items, state.currentIndex.coerceIn(items.indices), state.positionMs)
        player.prepare()
    }

    private fun knownDurationMs(item: MediaItem, entry: QueueEntry): Long =
        durations[item.mediaId]?.takeIf { it > 0 }
            ?: entry.durationSec?.let { (it * 1000).toLong() }
            ?: 0L

    private fun saveCurrent() {
        val item = player.currentMediaItem ?: return
        val entry = item.toQueueEntry() ?: return
        val reported = player.duration
        if (reported != C.TIME_UNSET && reported > 0) durations[item.mediaId] = reported
        save(entry, player.currentPosition, knownDurationMs(item, entry))
    }

    /** Marks the file at [index] as played to the end. */
    private fun saveFinished(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        val entry = item.toQueueEntry() ?: return
        val known = knownDurationMs(item, entry).takeIf { it > 0 } ?: 1L
        save(entry, known, known)
    }

    private fun save(entry: QueueEntry, positionMs: Long, durationMs: Long) {
        scope.launch(Dispatchers.IO + NonCancellable) {
            container.libraryRepository.saveResume(
                entry.identifier, entry.fileName, entry.fileIndex, entry.fileCount,
                positionMs, durationMs, entry.kind.name.lowercase(),
            )
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1_000)
            persistNow()
        }
    }

    private fun persistNow() {
        val (entries, state) = snapshot()
        scope.launch(Dispatchers.IO + NonCancellable) { container.queueDao.replaceAll(entries, state) }
    }

    /** Where the queue is (index and position) without rewriting its rows. */
    private fun persistState() {
        val state = snapshotState()
        scope.launch(Dispatchers.IO + NonCancellable) { container.queueDao.saveState(state) }
    }

    private fun snapshotState() = QueueStateEntity(
        currentIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex else -1,
        positionMs = player.currentPosition,
    )

    private fun snapshot() =
        (0 until player.mediaItemCount)
            .mapNotNull { player.getMediaItemAt(it).toQueueEntry() }
            .mapIndexed { i, e -> e.toEntity(i) } to snapshotState()

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val (entries, state) = snapshot()
        val item = player.currentMediaItem
        val entry = item?.toQueueEntry()
        val position = player.currentPosition
        runCatching {
            runBlocking(Dispatchers.IO) {
                container.queueDao.replaceAll(entries, state)
                if (item != null && entry != null) {
                    container.libraryRepository.saveResume(
                        entry.identifier, entry.fileName, entry.fileIndex, entry.fileCount,
                        position, knownDurationMs(item, entry), entry.kind.name.lowercase(),
                    )
                }
            }
        }
        session?.release()
        session = null
        player.removeListener(listener)
        player.release()
        scope.cancel()
        super.onDestroy()
    }
}
