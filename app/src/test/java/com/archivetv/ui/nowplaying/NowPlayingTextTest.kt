package com.archivetv.ui.nowplaying

import com.archivetv.domain.ItemKind
import com.archivetv.playback.QueueEntry
import com.archivetv.playback.Run
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingTextTest {
    private fun files(id: String, itemTitle: String, n: Int, kind: ItemKind = ItemKind.SPOKEN, librivox: Boolean = false) =
        (0 until n).map { QueueEntry(id, "$id$it.mp3", "Title $it", itemTitle, null, kind, librivox, it, n) }

    @Test fun remainderOfCurrentBookCountsMoreChapters() {
        val queue = files("holmes", "The Adventures of Sherlock Holmes", 11, librivox = true)
        assertThat(runLabel(Run(1, 11, "holmes"), queue, current = 0))
            .isEqualTo("The Adventures of Sherlock Holmes · 10 more chapters")
    }

    @Test fun singleFileShowsItsTitle() {
        val queue = files("a", "A", 1) + QueueEntry("suspense", "h.mp3", "The Hitchhiker", "Suspense", null, ItemKind.SPOKEN, false, 4, 88)
        assertThat(runLabel(Run(1, 2, "suspense"), queue, current = 0)).isEqualTo("Suspense · The Hitchhiker")
    }

    @Test fun laterRunCountsEpisodes() {
        val queue = files("a", "A", 1) + files("gunsmoke", "Gunsmoke - Single Episodes", 3)
        assertThat(runLabel(Run(1, 4, "gunsmoke"), queue, current = 0)).isEqualTo("Gunsmoke - Single Episodes · 3 episodes")
    }

    @Test fun musicSingleShowsTrack() {
        val queue = files("a", "A", 1) +
            QueueEntry("pw", "c.mp3", "Charleston", "Paul Whiteman and His Orchestra - 1925", null, ItemKind.MUSIC, false, 1, 10)
        assertThat(runLabel(Run(1, 2, "pw"), queue, current = 0))
            .isEqualTo("Paul Whiteman and His Orchestra - 1925 · Charleston")
    }

    @Test fun musicRunCountsTracks() {
        val queue = files("a", "A", 1) + files("lp", "Moonlight Sonata", 7, kind = ItemKind.MUSIC)
        assertThat(runLabel(Run(1, 8, "lp"), queue, current = 0)).isEqualTo("Moonlight Sonata · 7 tracks")
    }

    @Test fun clockFormats() {
        assertThat(clock(1_264_000)).isEqualTo("21:04")
        assertThat(clock(3_906_000)).isEqualTo("1:05:06")
        assertThat(clock(0)).isEqualTo("0:00")
    }
}
