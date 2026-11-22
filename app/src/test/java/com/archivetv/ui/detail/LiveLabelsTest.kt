package com.archivetv.ui.detail

import com.archivetv.domain.LiveInfo
import com.archivetv.domain.Recording
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveLabelsTest {
    private fun rec(source: String?, rating: Double?, reviews: Int) = Recording(
        identifier = "x", title = "t", date = "1977-05-08", showPart = "", venue = null, city = null,
        source = source, rating = rating, reviews = reviews, downloads = 0, artistId = "a", artistName = "A",
    )

    @Test fun concertLineSkipsBlankParts() {
        assertThat(concertLine(LiveInfo("a", "1977-05-08", "", "Barton Hall", "Ithaca, NY")))
            .isEqualTo("1977-05-08 · Barton Hall · Ithaca, NY")
        assertThat(concertLine(LiveInfo("a", "1970-01-03", "early", null, " ")))
            .isEqualTo("1970-01-03 (Early Show)")
    }

    @Test fun recordingLabels() {
        val long = "x".repeat(80)
        assertThat(recordingHeadline(rec(long, null, 0))).hasLength(60)
        assertThat(recordingHeadline(rec(null, null, 0))).isEqualTo("Recording")
        assertThat(recordingHeadline(rec("  ", null, 0))).isEqualTo("Recording")
        assertThat(recordingRating(rec("s", 4.93, 29))).isEqualTo("★ 4.9 · 29 reviews")
        assertThat(recordingRating(rec("s", 5.0, 1))).isEqualTo("★ 5.0 · 1 review")
        assertThat(recordingRating(rec("s", 0.0, 1))).isNull()
        assertThat(recordingRating(rec("s", null, 0))).isNull()
    }
}
