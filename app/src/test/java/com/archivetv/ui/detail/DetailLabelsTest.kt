package com.archivetv.ui.detail

import com.archivetv.domain.ItemKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetailLabelsTest {
    @Test fun audiobookListIsChapters() {
        assertThat(listHeader(ItemKind.SPOKEN, isLibriVox = true, count = 12)).isEqualTo("Chapters · 12")
    }

    @Test fun radioListIsEpisodes() {
        assertThat(listHeader(ItemKind.SPOKEN, isLibriVox = false, count = 973)).isEqualTo("Episodes · 973")
    }

    @Test fun musicListIsTracks() {
        assertThat(listHeader(ItemKind.MUSIC, isLibriVox = false, count = 12)).isEqualTo("Tracks · 12")
    }

    @Test fun videoListStaysEpisodes() {
        assertThat(listHeader(ItemKind.VIDEO, isLibriVox = false, count = 21)).isEqualTo("Episodes · 21")
    }
}
