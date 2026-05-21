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

    @Test fun returnsNullWhenNoPlayableFile() {
        val files = listOf(MetadataFileDto(name = "scan.pdf", format = "PDF"))
        assertThat(sel.select("id1", files)).isNull()
    }
}
