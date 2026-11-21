package com.archivetv.data

import com.archivetv.data.api.MetadataFileDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayableFileSelectorTest {
    private val sel = PlayableFileSelector()

    @Test fun prefersMp4OverOtherFormats() {
        val files = listOf(
            MetadataFileDto(name = "movie.ogv", format = "Ogg Video"),
            MetadataFileDto(name = "movie.mp4", format = "h.264"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.mp4")
    }

    @Test fun ignoresNonVideoAndDerivativeArtifacts() {
        val files = listOf(
            MetadataFileDto(name = "thumb.jpg", format = "JPEG"),
            MetadataFileDto(name = "movie.mp4", format = "h.264"),
            MetadataFileDto(name = "movie.gif", format = "Animated GIF"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.mp4")
    }

    @Test fun fallsBackToOtherWebPlayableWhenNoMp4() {
        val files = listOf(MetadataFileDto(name = "movie.webm", format = "WebM"))
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.webm")
    }

    @Test fun prefersFullSizeH264Over512kbRegardlessOfOrder() {
        val files = listOf(
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4", height = listOf("240")),
            MetadataFileDto(name = "film.ogv", format = "Ogg Video", height = listOf("304")),
            MetadataFileDto(name = "film.mp4", format = "h.264", height = listOf("480")),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film.mp4")
    }

    @Test fun prefersOriginalMpeg4Over512kb() {
        val files = listOf(
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4"),
            MetadataFileDto(name = "film-original.mp4", format = "MPEG4"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film-original.mp4")
    }

    @Test fun prefers512kbMp4OverOgg() {
        val files = listOf(
            MetadataFileDto(name = "film.ogv", format = "Ogg Video"),
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film_512kb.mp4")
    }

    @Test fun withinSameFormatPrefersTallerVideo() {
        val files = listOf(
            MetadataFileDto(name = "sd.mp4", format = "h.264", height = listOf("480")),
            MetadataFileDto(name = "hd.mp4", format = "h.264", height = listOf("720")),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("hd.mp4")
    }

    @Test fun returnsNullWhenNoPlayableFile() {
        val files = listOf(MetadataFileDto(name = "scan.pdf", format = "PDF"))
        assertThat(sel.select("id1", files)).isNull()
    }
}
