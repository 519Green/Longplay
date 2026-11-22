package com.archivetv.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.archivetv.data.ItemDetail
import com.archivetv.data.db.ResumeEntity
import com.archivetv.data.episodeLabels
import com.archivetv.data.isResumable
import com.archivetv.data.startPoint
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.ItemKind
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the audio player is doing; null when the queue is empty. */
data class NowPlaying(
    val queue: List<QueueEntry>,
    val currentIndex: Int,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    val current: QueueEntry? get() = queue.getOrNull(currentIndex)
}

/**
 * The app's handle on [PlaybackService]: a MediaController plus the queue
 * commands, which come from [QueueOps] so their rules are unit-tested.
 * Controls are no-ops until [connect] has finished.
 */
class AudioConnection(
    private val context: Context,
    private val resolve: suspend (identifier: String) -> ItemDetail,
    private val progress: suspend (identifier: String, fileNames: List<String>) -> Map<String, ResumeEntity>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var playAllJob: Job? = null
    private var queue: List<QueueEntry> = emptyList()

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) readQueue(player)
            publish()
        }
    }

    /** Binds to the service (creating it, which restores the saved queue). Safe to call repeatedly. */
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            if (future !== f) { c.release(); return@addListener }
            controller = c
            c.addListener(listener)
            readQueue(c)
            publish()
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (controller?.isPlaying == true) publish()
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Unbinds; playback continues in the service. */
    fun disconnect() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    fun pause() { controller?.pause() }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val end = c.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, end))
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }

    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Plays [identifier] in place of the item now playing. With [fileIndex] it
     * starts at that file (resuming it if part-way), else where the listener left off.
     * Returns false when the item has no audio to play.
     */
    suspend fun play(identifier: String, fileIndex: Int? = null): Boolean {
        val (entries, start, position) = filesFrom(identifier, fileIndex) ?: return false
        val c = awaitController() ?: return false
        execute(c, QueueOps.play(queue, current(c), entries.drop(start), position))
        return true
    }

    /** Queues the item from where the listener left off, or just [fileIndex], right after the current file. */
    suspend fun playNext(identifier: String, fileIndex: Int? = null): Boolean {
        val files = queuedFiles(identifier, fileIndex) ?: return false
        val c = awaitController() ?: return false
        execute(c, QueueOps.playNext(queue, current(c), files))
        return true
    }

    /** As [playNext], but at the end of the queue. */
    suspend fun addToQueue(identifier: String, fileIndex: Int? = null): Boolean {
        val files = queuedFiles(identifier, fileIndex) ?: return false
        val c = awaitController() ?: return false
        execute(c, QueueOps.addToQueue(queue, current(c), files))
        return true
    }

    /**
     * Play all / Shuffle for a music leaf: the queue becomes these items (at
     * most 50). Playback starts with the first that resolves; the rest are
     * resolved four at a time and appended in order.
     */
    fun playAll(items: List<ArchiveItem>, shuffle: Boolean) {
        playAllJob?.cancel()
        val ordered = (if (shuffle) items.shuffled() else items).take(50).map { it.identifier }
        playAllJob = scope.launch {
            val c = awaitController() ?: return@launch
            var rest = ordered
            while (rest.isNotEmpty()) {
                val first = rest.first()
                rest = rest.drop(1)
                val files = allFiles(first) ?: continue
                execute(c, QueueOps.replaceAll(files))
                break
            }
            for (batch in rest.chunked(4)) {
                val resolved = batch.map { id -> async { allFiles(id) } }.awaitAll().filterNotNull().flatten()
                if (resolved.isNotEmpty()) c.addMediaItems(resolved.map { it.toMediaItem() })
            }
        }
    }

    fun removeRun(run: Run) {
        val c = controller ?: return
        execute(c, QueueOps.removeRun(queue, current(c), run))
    }

    private fun current(c: Player) = if (c.mediaItemCount > 0) c.currentMediaItemIndex else -1

    private fun execute(c: Player, plan: QueuePlan) {
        c.apply(plan)
        readQueue(c)
        QueueOps.trimHistory(current(c))?.let { c.removeMediaItems(it.from, it.toExclusive) }
        readQueue(c)
        publish()
    }

    private suspend fun entriesOf(identifier: String): List<QueueEntry>? {
        val detail = runCatching { resolve(identifier) }.getOrNull() ?: return null
        if (detail.kind == ItemKind.VIDEO || detail.files.isEmpty()) return null
        val labels = episodeLabels(detail.files.map { it.title })
        return detail.files.mapIndexed { i, f ->
            QueueEntry(
                identifier = identifier,
                fileName = f.fileName,
                title = labels[i],
                itemTitle = detail.item.title,
                durationSec = f.durationSec,
                kind = detail.kind,
                isLibriVox = detail.isLibriVox,
                fileIndex = i,
                fileCount = detail.files.size,
            )
        }
    }

    private suspend fun allFiles(identifier: String): List<QueueEntry>? =
        entriesOf(identifier)?.let(QueueOps::forPlayAll)

    /** Entries, the index to start at, and the position in that file. */
    private suspend fun filesFrom(identifier: String, fileIndex: Int?): Triple<List<QueueEntry>, Int, Long>? {
        val entries = entriesOf(identifier) ?: return null
        val names = entries.map { it.fileName }
        val saved = runCatching { progress(identifier, names) }.getOrDefault(emptyMap())
        if (fileIndex != null && fileIndex in entries.indices) {
            val e = saved[names[fileIndex]]
            val pos = e?.takeIf { isResumable(it.positionMs, it.durationMs) }?.positionMs ?: 0L
            return Triple(entries, fileIndex, pos)
        }
        val sp = startPoint(names, saved)
        return Triple(entries, sp.index, sp.positionMs)
    }

    private suspend fun queuedFiles(identifier: String, fileIndex: Int?): List<QueueEntry>? {
        val (entries, start, _) = filesFrom(identifier, fileIndex) ?: return null
        return if (fileIndex != null) listOf(entries[start]) else entries.drop(start)
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        connect()
        val f = future ?: return null
        return suspendCancellableCoroutine { cont ->
            f.addListener({
                // connect()'s own listener runs first and sets controller.
                cont.resume(controller ?: runCatching { f.get() }.getOrNull())
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun readQueue(p: Player) {
        queue = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).toQueueEntry() }
    }

    private fun publish() {
        val c = controller
        _state.value = if (c == null || queue.isEmpty()) null else NowPlaying(
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: queue.getOrNull(c.currentMediaItemIndex)?.durationSec?.let { (it * 1000).toLong() }
                ?: 0L,
        )
    }
}

/** Applies a [QueuePlan] to a player (see [QueuePlan] for the order). */
internal fun Player.apply(plan: QueuePlan) {
    if (plan.clear) clearMediaItems()
    for (edit in plan.edits) when (edit) {
        is QueueEdit.Insert -> addMediaItems(edit.at, edit.entries.map { it.toMediaItem() })
        is QueueEdit.Remove -> removeMediaItems(edit.from, edit.toExclusive)
        is QueueEdit.Replace -> replaceMediaItems(edit.from, edit.toExclusive, edit.entries.map { it.toMediaItem() })
    }
    plan.playIndex?.let { index ->
        if (index in 0 until mediaItemCount) {
            seekTo(index, plan.startPositionMs)
            prepare()
            play()
        }
    }
}
