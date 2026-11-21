package com.archivetv.data

import com.archivetv.domain.ItemKind
import com.archivetv.domain.itemKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItemKindTest {
    @Test fun videoWhenVideoFilesPresent() {
        assertThat(itemKind(listOf("georgeblood"), hasVideo = true)).isEqualTo(ItemKind.VIDEO)
    }

    @Test fun musicForMusicCollections() {
        assertThat(itemKind(listOf("78rpm", "audio_music"), hasVideo = false)).isEqualTo(ItemKind.MUSIC)
        assertThat(itemKind(listOf("hifidelity_potpourri", "hifidelity"), hasVideo = false)).isEqualTo(ItemKind.MUSIC)
    }

    @Test fun spokenOtherwise() {
        assertThat(itemKind(listOf("librivoxaudio"), hasVideo = false)).isEqualTo(ItemKind.SPOKEN)
        assertThat(itemKind(listOf("oldtimeradio"), hasVideo = false)).isEqualTo(ItemKind.SPOKEN)
    }
}
