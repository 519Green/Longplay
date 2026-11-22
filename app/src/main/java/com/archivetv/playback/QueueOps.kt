package com.archivetv.playback

import com.archivetv.domain.ItemKind

/** One file in the audio queue. [fileIndex]/[fileCount] locate it inside its item. */
data class QueueEntry(
    val identifier: String,
    val fileName: String,
    val title: String,
    val itemTitle: String,
    val durationSec: Double?,
    val kind: ItemKind,
    val isLibriVox: Boolean,
    val fileIndex: Int,
    val fileCount: Int,
)

/** One playlist change, applied in order with Player.addMediaItems/removeMediaItems/replaceMediaItems. */
sealed interface QueueEdit {
    data class Insert(val at: Int, val entries: List<QueueEntry>) : QueueEdit
    data class Remove(val from: Int, val toExclusive: Int) : QueueEdit
    data class Replace(val from: Int, val toExclusive: Int, val entries: List<QueueEntry>) : QueueEdit
}

/**
 * What a queue command does: optionally clear the playlist first, apply [edits]
 * in order, then, if [playIndex] is set, seek there (to [startPositionMs]) and play.
 */
data class QueuePlan(
    val edits: List<QueueEdit>,
    val playIndex: Int?,
    val startPositionMs: Long = 0L,
    val clear: Boolean = false,
)

/** Consecutive queue files of one item, `[start, endExclusive)`. Shown as one row in Up next. */
data class Run(val start: Int, val endExclusive: Int, val identifier: String)

/**
 * Queue commands as pure functions over the current playlist. `current` is the
 * playing (or paused) index, -1 when the queue is empty. Files before `current`
 * are history; they stay so Previous works (trimmed by [trimHistory]).
 */
object QueueOps {
    fun runs(queue: List<QueueEntry>): List<Run> = runsIn(queue, 0, queue.size)

    /** Runs after [current]; the rest of the current item counts as the first run. */
    fun upNext(queue: List<QueueEntry>, current: Int): List<Run> = runsIn(queue, current + 1, queue.size)

    /** Replaces the item that is playing (or paused) with [files] and starts them. */
    fun play(queue: List<QueueEntry>, current: Int, files: List<QueueEntry>, startPositionMs: Long): QueuePlan {
        if (current !in queue.indices) return QueuePlan(listOf(QueueEdit.Insert(0, files)), 0, startPositionMs, clear = queue.isNotEmpty())
        val run = runs(queue).first { current in it.start until it.endExclusive }
        return QueuePlan(listOf(QueueEdit.Replace(run.start, run.endExclusive, files)), run.start, startPositionMs)
    }

    /** Inserts [files] right after the current file. Starts playback only if the queue was empty. */
    fun playNext(queue: List<QueueEntry>, current: Int, files: List<QueueEntry>): QueuePlan {
        if (current !in queue.indices) return startFresh(queue, files)
        return QueuePlan(listOf(QueueEdit.Insert(current + 1, files)), null)
    }

    /**
     * Appends [files] that are not already at or after the current file.
     * Starts playback only if the queue was empty.
     */
    fun addToQueue(queue: List<QueueEntry>, current: Int, files: List<QueueEntry>): QueuePlan {
        if (current !in queue.indices) return startFresh(queue, files)
        val ahead = queue.subList(current, queue.size).map { it.identifier to it.fileName }.toSet()
        val fresh = files.filter { (it.identifier to it.fileName) !in ahead }
        if (fresh.isEmpty()) return QueuePlan(emptyList(), null)
        return QueuePlan(listOf(QueueEdit.Insert(queue.size, fresh)), null)
    }

    /** Removes [run]. If it held the current file, playback moves to what follows; nothing follows means clear. */
    fun removeRun(queue: List<QueueEntry>, current: Int, run: Run): QueuePlan {
        val holdsCurrent = current in run.start until run.endExclusive
        if (holdsCurrent && run.endExclusive >= queue.size) return QueuePlan(emptyList(), null, clear = true)
        return QueuePlan(
            listOf(QueueEdit.Remove(run.start, run.endExclusive)),
            playIndex = if (holdsCurrent) run.start else null,
        )
    }

    /** Play all / Shuffle: the queue becomes [files], from the first. */
    fun replaceAll(files: List<QueueEntry>): QueuePlan =
        QueuePlan(listOf(QueueEdit.Insert(0, files)), 0, clear = true)

    /**
     * The files one item adds to Play all / Shuffle: a whole album or a
     * two-sided 78, but only the first file of a compilation (more than
     * [maxFiles]), so one V-Disc set does not fill the queue.
     */
    fun forPlayAll(files: List<QueueEntry>, maxFiles: Int = 30): List<QueueEntry> =
        if (files.size > maxFiles) files.take(1) else files

    /** Drops history older than the [keep] files before [current]. */
    fun trimHistory(current: Int, keep: Int = 20): QueueEdit.Remove? =
        if (current > keep) QueueEdit.Remove(0, current - keep) else null

    private fun startFresh(queue: List<QueueEntry>, files: List<QueueEntry>) =
        QueuePlan(listOf(QueueEdit.Insert(0, files)), 0, clear = queue.isNotEmpty())

    private fun runsIn(queue: List<QueueEntry>, from: Int, to: Int): List<Run> {
        val out = mutableListOf<Run>()
        var start = from.coerceAtLeast(0)
        while (start < to) {
            var end = start + 1
            while (end < to && queue[end].identifier == queue[start].identifier) end++
            out += Run(start, end, queue[start].identifier)
            start = end
        }
        return out
    }
}
