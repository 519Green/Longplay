package com.archivetv.ui.nowplaying

import com.archivetv.domain.ItemKind
import com.archivetv.playback.QueueEntry
import com.archivetv.playback.Run

/**
 * One Up next row: a single file shows its title ("Suspense · The Hitchhiker");
 * a longer run shows a count ("Gunsmoke - Single Episodes · 3 episodes"),
 * "N more ..." when it is the rest of the item playing now.
 */
fun runLabel(run: Run, queue: List<QueueEntry>, current: Int): String {
    val files = queue.subList(run.start, run.endExclusive)
    val first = files.first()
    if (files.size == 1) return "${first.itemTitle} · ${first.title}"
    val noun = when {
        first.kind == ItemKind.MUSIC -> "tracks"
        first.isLibriVox -> "chapters"
        else -> "episodes"
    }
    val remainder = current in queue.indices && run.start == current + 1 && queue[current].identifier == run.identifier
    return "${first.itemTitle} · ${files.size} ${if (remainder) "more " else ""}$noun"
}

/** 1_264_000 ms -> "21:04"; 3_906_000 ms -> "1:05:06". */
fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
