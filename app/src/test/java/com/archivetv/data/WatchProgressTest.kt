package com.archivetv.data

import com.archivetv.data.db.ResumeEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchProgressTest {
    private fun r(
        id: String, file: String, pos: Long, dur: Long, at: Long, index: Int = 0, count: Int = 1,
    ) = ResumeEntity(id, file, pos, dur, at, index, count)

    @Test fun finishedAtNinetyFivePercent() {
        assertThat(isFinished(9_500L, 10_000L)).isTrue()
        assertThat(isFinished(9_400L, 10_000L)).isFalse()
        assertThat(isFinished(9_500L, 0L)).isFalse()
    }

    @Test fun continueWatchingUsesLatestEntryPerItem() {
        val entries = listOf(
            r("show", "e2.mp4", 3_000_000L, 6_000_000L, at = 30, index = 1, count = 5),
            r("show", "e1.mp4", 6_000_000L, 6_000_000L, at = 20, index = 0, count = 5),
            r("film", "film.mp4", 60_000L, 600_000L, at = 10),
        )
        val cw = continueWatching(entries)
        assertThat(cw.map { it.identifier }).containsExactly("show", "film").inOrder()
        assertThat(cw[0].progress).isWithin(0.001f).of(0.5f)
    }

    @Test fun continueWatchingSkipsItemsWhoseLatestFileIsFinishedOrBarelyStarted() {
        val entries = listOf(
            r("done", "a.mp4", 600_000L, 600_000L, at = 3),
            r("barely", "b.mp4", 2_000L, 600_000L, at = 2),
        )
        assertThat(continueWatching(entries)).isEmpty()
    }

    @Test fun nextUpWhenLatestEpisodeFinishedAndMoreRemain() {
        val entries = listOf(
            r("show", "e3.mp4", 600_000L, 600_000L, at = 30, index = 2, count = 5),
            r("show", "e2.mp4", 600_000L, 600_000L, at = 20, index = 1, count = 5),
            r("last", "e5.mp4", 600_000L, 600_000L, at = 10, index = 4, count = 5),
            r("mid", "e1.mp4", 300_000L, 600_000L, at = 5, index = 0, count = 5),
        )
        val next = nextUp(entries)
        assertThat(next.map { it.identifier to it.nextIndex }).containsExactly("show" to 3)
        assertThat(next.single().fileCount).isEqualTo(5)
    }

    @Test fun progressByFileMapsLegacyRowToFirstFile() {
        val entries = listOf(
            r("show", "", 1_000_000L, 2_000_000L, at = 1),
            r("show", "e2.mp4", 500_000L, 2_000_000L, at = 2, index = 1, count = 3),
        )
        val byFile = progressByFile(entries, listOf("e1.mp4", "e2.mp4", "e3.mp4"))
        assertThat(byFile.keys).containsExactly("e1.mp4", "e2.mp4")
        assertThat(byFile.getValue("e1.mp4").positionMs).isEqualTo(1_000_000L)
    }

    @Test fun startPointResumesLatestUnfinishedFile() {
        val files = listOf("e1.mp4", "e2.mp4", "e3.mp4")
        val progress = mapOf(
            "e1.mp4" to r("s", "e1.mp4", 600_000L, 600_000L, at = 1, index = 0, count = 3),
            "e2.mp4" to r("s", "e2.mp4", 120_000L, 600_000L, at = 2, index = 1, count = 3),
        )
        assertThat(startPoint(files, progress)).isEqualTo(StartPoint(1, 120_000L, StartKind.RESUME))
    }

    @Test fun startPointMovesToNextAfterFinishedFile() {
        val files = listOf("e1.mp4", "e2.mp4")
        val progress = mapOf("e1.mp4" to r("s", "e1.mp4", 600_000L, 600_000L, at = 1, count = 2))
        assertThat(startPoint(files, progress)).isEqualTo(StartPoint(1, 0L, StartKind.NEXT))
    }

    @Test fun startPointFromBeginningWhenNothingOrAllWatched() {
        val files = listOf("e1.mp4", "e2.mp4")
        assertThat(startPoint(files, emptyMap())).isEqualTo(StartPoint(0, 0L, StartKind.BEGIN))
        val done = mapOf("e2.mp4" to r("s", "e2.mp4", 600_000L, 600_000L, at = 1, index = 1, count = 2))
        assertThat(startPoint(files, done)).isEqualTo(StartPoint(0, 0L, StartKind.BEGIN))
    }

    @Test fun progressByFilePrefersRealRowOverLegacy() {
        val entries = listOf(
            r("show", "", 1_000L, 2_000_000L, at = 1),
            r("show", "e1.mp4", 900_000L, 2_000_000L, at = 2),
        )
        val byFile = progressByFile(entries, listOf("e1.mp4"))
        assertThat(byFile.getValue("e1.mp4").positionMs).isEqualTo(900_000L)
    }
}
