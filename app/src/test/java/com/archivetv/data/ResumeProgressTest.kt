package com.archivetv.data

import com.archivetv.data.db.ResumeEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumeProgressTest {
    @Test fun fractionIsPositionOverDuration() {
        assertThat(resumeFraction(2500L, 10000L)).isWithin(0.001f).of(0.25f)
    }

    @Test fun fractionZeroWhenDurationUnknown() {
        assertThat(resumeFraction(2500L, 0L)).isEqualTo(0f)
    }

    @Test fun nearEndIsNotResumable() {
        assertThat(isResumable(9600L, 10000L)).isFalse()
    }

    @Test fun midpointIsResumable() {
        assertThat(isResumable(5000L, 10000L)).isTrue()
    }

    private fun row(id: String, file: String, pos: Long, dur: Long, at: Long, index: Int = 0, count: Int = 1, kind: String = "video") =
        ResumeEntity(id, file, pos, dur, at, index, count, kind)

    @Test fun continueWatchingIgnoresAudioRows() {
        val rows = listOf(
            row("book", "ch1.mp3", 60_000, 600_000, 3, kind = "spoken"),
            row("film", "f.mp4", 60_000, 600_000, 2),
        )
        assertThat(continueWatching(rows).map { it.identifier }).containsExactly("film")
    }

    @Test fun nextUpIgnoresAudioRows() {
        val rows = listOf(
            row("otr", "e1.mp3", 600_000, 600_000, 3, index = 0, count = 5, kind = "spoken"),
            row("show", "e1.mp4", 600_000, 600_000, 2, index = 0, count = 5),
        )
        assertThat(nextUp(rows).map { it.identifier }).containsExactly("show")
    }

    @Test fun continueListeningShowsResumableSpoken() {
        val rows = listOf(row("book", "ch3.mp3", 300_000, 600_000, 3, index = 2, count = 12, kind = "spoken"))
        assertThat(continueListening(rows))
            .containsExactly(ListenCard("book", fileIndex = 2, fileCount = 12, progress = 0.5f, isNext = false))
    }

    @Test fun continueListeningShowsNextFileWhenFinished() {
        val rows = listOf(row("otr", "e5.mp3", 600_000, 600_000, 3, index = 4, count = 973, kind = "spoken"))
        assertThat(continueListening(rows))
            .containsExactly(ListenCard("otr", fileIndex = 5, fileCount = 973, progress = null, isNext = true))
    }

    @Test fun continueListeningIgnoresMusicVideoAndLastFileFinished() {
        val rows = listOf(
            row("lp", "t1.mp3", 60_000, 200_000, 5, index = 0, count = 9, kind = "music"),
            row("film", "f.mp4", 60_000, 600_000, 4),
            row("done", "last.mp3", 600_000, 600_000, 3, index = 4, count = 5, kind = "spoken"),
        )
        assertThat(continueListening(rows)).isEmpty()
    }
}
