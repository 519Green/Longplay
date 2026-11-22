package com.archivetv.ui.live

import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import com.archivetv.domain.Recording
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

fun concert(
    id: String,
    date: String = "1977-05-08",
    venue: String? = "Barton Hall",
    city: String? = "Ithaca, NY",
    showPart: String = "",
    count: Int = 1,
    artist: String = "Some Band",
): Concert {
    val r = Recording(
        identifier = id, title = "$artist Live at $venue on $date", date = date, showPart = showPart,
        venue = venue, city = city, source = null, rating = null, reviews = 0, downloads = 0,
        artistId = "SomeBand", artistName = artist,
    )
    return Concert("SomeBand", artist, date, showPart, venue, city, r, count)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveMusicViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun rowsLoadInParallelInOrder() = runTest {
        var asked: Pair<Int, Int>? = null
        val vm = LiveMusicViewModel(
            today = 11 to 21,
            thisDay = { m, d -> asked = m to d; listOf(concert("a")) },
            topRated = { listOf(concert("b")) },
            mostPlayed = { listOf(concert("c")) },
            popularArtists = { listOf(Artist("x", "X")) },
        )
        val rows = vm.rows.value
        assertThat(rows.map { it.title })
            .containsExactly("This Day in History · Nov 21", "Top Rated Shows", "Most Played Shows", "Popular Artists")
            .inOrder()
        assertThat(asked).isEqualTo(11 to 21)
        assertThat((rows[0] as LiveRow.Concerts).state).isEqualTo(Load.Done(listOf(concert("a"))))
        assertThat((rows[3] as LiveRow.Artists).state).isEqualTo(Load.Done(listOf(Artist("x", "X"))))
    }

    @Test fun failedRowCanBeRetried() = runTest {
        var calls = 0
        val vm = LiveMusicViewModel(
            today = 3 to 5,
            thisDay = { _, _ -> listOf(concert("a")) },
            topRated = { if (calls++ == 0) error("down") else listOf(concert("b")) },
            mostPlayed = { emptyList() },
            popularArtists = { emptyList() },
        )
        assertThat(vm.rows.value[0].title).isEqualTo("This Day in History · Mar 5")
        assertThat((vm.rows.value[1] as LiveRow.Concerts).state).isEqualTo(Load.Failed)
        vm.retry(1)
        assertThat((vm.rows.value[1] as LiveRow.Concerts).state).isEqualTo(Load.Done(listOf(concert("b"))))
    }

    @Test fun concertSubtitleFormats() {
        assertThat(concertSubtitle(concert("a", date = "1970-01-03", venue = "Fillmore East", showPart = "early")))
            .isEqualTo("1970-01-03 (Early Show) · Fillmore East")
        assertThat(concertSubtitle(concert("a", showPart = "late", venue = null, city = null)))
            .isEqualTo("1977-05-08 (Late Show)")
        assertThat(concertSubtitle(concert("a", venue = null))).isEqualTo("1977-05-08 · Ithaca, NY")
        assertThat(concertSubtitle(concert("a", venue = "Barton Hall"), withCity = true))
            .isEqualTo("1977-05-08 · Barton Hall · Ithaca, NY")
    }

    @Test fun concertCardOpensBestRecording() {
        val item = concert("best-id").toCardItem()
        assertThat(item.identifier).isEqualTo("best-id")
        assertThat(item.title).isEqualTo("Some Band")
        assertThat(item.isAudio).isTrue()
    }
}
