package com.archivetv.playback

import com.archivetv.domain.ItemKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueOpsTest {
    /** [n] files of item [id], file indexes starting at [from]. */
    private fun e(id: String, n: Int, from: Int = 0) = (from until from + n).map {
        QueueEntry(id, "$id$it.mp3", "$id $it", "Item $id", 60.0, ItemKind.SPOKEN, false, it, from + n)
    }

    private val empty = emptyList<QueueEntry>()

    @Test fun playOnEmptyQueueInsertsAndPlaysFirst() {
        val files = e("a", 3)
        val plan = QueueOps.play(empty, -1, files, 0L)
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(0, files))
        assertThat(plan.playIndex).isEqualTo(0)
    }

    @Test fun playReplacesCurrentRunKeepsRest() {
        val queue = e("a", 3) + e("b", 1)
        val c = e("c", 2)
        val plan = QueueOps.play(queue, 1, c, 42_000L)
        assertThat(plan.edits).containsExactly(QueueEdit.Replace(0, 3, c))
        assertThat(plan.playIndex).isEqualTo(0)
        assertThat(plan.startPositionMs).isEqualTo(42_000L)
        assertThat(plan.clear).isFalse()
    }

    @Test fun playNextInsertsAfterCurrentFile() {
        val queue = e("a", 3)
        val b = e("b", 1)
        val plan = QueueOps.playNext(queue, 0, b)
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(1, b))
        assertThat(plan.playIndex).isNull()
    }

    @Test fun playNextOnEmptyStartsPlayback() {
        val plan = QueueOps.playNext(empty, -1, e("b", 1))
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(0, e("b", 1)))
        assertThat(plan.playIndex).isEqualTo(0)
    }

    @Test fun addToQueueOnEmptyStartsPlayback() {
        val plan = QueueOps.addToQueue(empty, -1, e("b", 2))
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(0, e("b", 2)))
        assertThat(plan.playIndex).isEqualTo(0)
    }

    @Test fun addToQueueWhilePausedOnlyAdds() {
        val plan = QueueOps.addToQueue(e("a", 2), 0, e("b", 1))
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(2, e("b", 1)))
        assertThat(plan.playIndex).isNull()
    }

    @Test fun addToQueueSkipsFilesAlreadyUpNext() {
        val queue = e("a", 1) + e("b", 2)
        val plan = QueueOps.addToQueue(queue, 0, e("b", 3))
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(3, e("b", 3).drop(2)))
    }

    @Test fun addToQueueOfOnlyQueuedFilesChangesNothing() {
        val queue = e("a", 1) + e("b", 2)
        val plan = QueueOps.addToQueue(queue, 0, e("b", 2))
        assertThat(plan.edits).isEmpty()
        assertThat(plan.playIndex).isNull()
    }

    @Test fun removePlayingRunMovesToNextRun() {
        val queue = e("a", 2) + e("b", 1)
        val plan = QueueOps.removeRun(queue, 1, Run(0, 2, "a"))
        assertThat(plan.edits).containsExactly(QueueEdit.Remove(0, 2))
        assertThat(plan.playIndex).isEqualTo(0)
    }

    @Test fun removeOnlyRunClearsQueue() {
        val plan = QueueOps.removeRun(e("a", 2), 0, Run(0, 2, "a"))
        assertThat(plan.clear).isTrue()
        assertThat(plan.edits).isEmpty()
        assertThat(plan.playIndex).isNull()
    }

    @Test fun removeLaterRunKeepsPlaying() {
        val queue = e("a", 2) + e("b", 1)
        val plan = QueueOps.removeRun(queue, 0, Run(2, 3, "b"))
        assertThat(plan.edits).containsExactly(QueueEdit.Remove(2, 3))
        assertThat(plan.playIndex).isNull()
    }

    @Test fun upNextGroupsConsecutiveFiles() {
        val queue = e("a", 2) + e("b", 1) + e("a", 1, from = 2)
        assertThat(QueueOps.upNext(queue, 0))
            .containsExactly(Run(1, 2, "a"), Run(2, 3, "b"), Run(3, 4, "a")).inOrder()
    }

    @Test fun upNextOmitsEmptyRemainder() {
        val queue = e("a", 1) + e("b", 2)
        assertThat(QueueOps.upNext(queue, 0)).containsExactly(Run(1, 3, "b"))
        assertThat(QueueOps.upNext(queue, 2)).isEmpty()
    }

    @Test fun replaceAllClearsThenPlays() {
        val files = e("m", 3)
        val plan = QueueOps.replaceAll(files)
        assertThat(plan.clear).isTrue()
        assertThat(plan.edits).containsExactly(QueueEdit.Insert(0, files))
        assertThat(plan.playIndex).isEqualTo(0)
    }

    @Test fun trimHistoryKeepsTwenty() {
        assertThat(QueueOps.trimHistory(25)).isEqualTo(QueueEdit.Remove(0, 5))
        assertThat(QueueOps.trimHistory(20)).isNull()
        assertThat(QueueOps.trimHistory(10)).isNull()
    }
}
