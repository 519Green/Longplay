package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveMusicTest {
    private fun rec(
        id: String,
        rating: Double? = null,
        reviews: Int = 0,
        downloads: Int = 0,
        date: String = "1977-05-08",
        artistId: String = "GratefulDead",
        showPart: String = "",
    ) = Recording(
        identifier = id, title = "$artistId Live at Somewhere on $date", date = date, showPart = showPart,
        venue = "Venue $id", city = "City $id", source = null, rating = rating, reviews = reviews,
        downloads = downloads, artistId = artistId, artistName = artistId,
    )

    @Test
    fun dateOfTakesIsoPrefix() {
        assertThat(dateOf("1977-05-26T00:00:00Z")).isEqualTo("1977-05-26")
        assertThat(dateOf("1977-05-08")).isEqualTo("1977-05-08")
        assertThat(dateOf(null)).isNull()
        assertThat(dateOf("1977")).isNull()
    }

    @Test
    fun showPartDetectsEarlyAndLate() {
        assertThat(showPart("Grateful Dead Live at Fillmore East on 1970-01-03 (Early Show)")).isEqualTo("early")
        assertThat(showPart("Grateful Dead Live at Fillmore East on 1970-01-03 (Late)")).isEqualTo("late")
        assertThat(showPart("Grateful Dead Live at Fillmore East on 1970-01-03 (late show)")).isEqualTo("late")
        assertThat(showPart("Grateful Dead Live at Barton Hall on 1977-05-08")).isEqualTo("")
    }

    @Test
    fun artistIdSkipsHousekeeping() {
        assertThat(artistIdOf(listOf("GratefulDead", "etree", "stream_only"))).isEqualTo("GratefulDead")
        assertThat(artistIdOf(listOf("fav-bob", "etree", "moe"))).isEqualTo("moe")
        assertThat(artistIdOf(listOf("etree", "stream_only"))).isNull()
    }

    @Test
    fun bestPrefersWellReviewedHighRating() {
        val sbd = rec("sbd", 4.78, 300, 1_464_039)
        val aud1 = rec("aud1", 4.62, 108, 90_000)
        val matrix = rec("matrix", 4.93, 29, 200_000)
        val aud2 = rec("aud2", 5.0, 6, 58_000)
        val zero = rec("zero", 0.0, 1, 70_000)
        val ranked = bestOf(listOf(sbd, aud1, matrix, aud2, zero))
        assertThat(ranked.first()).isEqualTo(matrix)
        assertThat(ranked.map { it.identifier }).containsExactly("matrix", "sbd", "aud2", "aud1", "zero").inOrder()
        assertThat(score(zero)).isWithin(1e-9).of(4.499)
        assertThat(score(rec("none"))).isWithin(1e-9).of(4.499)
    }

    @Test
    fun bestBreaksTiesByDownloads() {
        val low = rec("low", downloads = 10)
        val high = rec("high", downloads = 500)
        assertThat(bestOf(listOf(low, high)).map { it.identifier }).containsExactly("high", "low").inOrder()
    }

    @Test
    fun groupsByArtistDateAndShowPart() {
        val concerts = groupConcerts(
            listOf(
                rec("gd1", 4.0, 10, 5),
                rec("moe1", artistId = "moe"),
                rec("gd2", 4.9, 40, 5),
                rec("gd3"),
                rec("early", date = "1970-01-03", showPart = "early"),
                rec("late", date = "1970-01-03", showPart = "late"),
            ),
        )
        assertThat(concerts).hasSize(4)
        val gd = concerts.first()
        assertThat(gd.artistId).isEqualTo("GratefulDead")
        assertThat(gd.date).isEqualTo("1977-05-08")
        assertThat(gd.recordingCount).isEqualTo(3)
        assertThat(gd.best.identifier).isEqualTo("gd2")
        assertThat(gd.venue).isEqualTo("Venue gd2")
        assertThat(gd.city).isEqualTo("City gd2")
        assertThat(concerts.map { it.showPart }).containsExactly("", "", "early", "late").inOrder()
    }

    @Test
    fun yearCountsCountDistinctDates() {
        assertThat(yearCounts(listOf("1978-01-01", "1977-05-08", "1977-05-08", "1977-05-09")))
            .containsExactly(YearCount(1977, 2), YearCount(1978, 1)).inOrder()
    }

    @Test
    fun jumpLetterAndSearchKeyIgnorePunctuation() {
        assertThat(jumpLetter("...And Stars Collide")).isEqualTo('A')
        assertThat(searchKey("& the #'s")).isEqualTo("the s")
        assertThat(jumpLetter("& the #'s")).isEqualTo('T')
        assertThat(jumpLetter("'68 Youth Riot")).isEqualTo('#')
        assertThat(jumpLetter("moe.")).isEqualTo('M')
        assertThat(searchKey("...And Stars Collide")).contains(searchKey("and stars"))
    }
}
