package com.archivetv.ui.search

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.Artist
import com.archivetv.ui.live.concert
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun submitFillsWatchAndListen() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> listOf(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> listOf(ArchiveItem("book", "Book", isAudio = true)) },
        )
        vm.search("sherlock")
        val s = vm.state.value
        assertThat(s.watch.map { it.identifier }).containsExactly("film")
        assertThat(s.listen.map { it.identifier }).containsExactly("book")
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNull()
    }

    @Test fun failedAudioSearchStillShowsVideo() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> listOf(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> error("down") },
        )
        vm.search("sherlock")
        val s = vm.state.value
        assertThat(s.watch).hasSize(1)
        assertThat(s.listen).isEmpty()
        assertThat(s.error).isNull()
    }

    @Test fun bothFailingShowsError() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> error("down") },
            searchAudio = { _, _ -> error("down") },
            searchLiveArtists = { error("down") },
            searchLiveConcerts = { error("down") },
        )
        vm.search("sherlock")
        assertThat(vm.state.value.error).isEqualTo("Search failed. Try again.")
    }

    @Test fun liveResultsAloneAreNotAnError() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> error("down") },
            searchAudio = { _, _ -> error("down") },
            searchLiveArtists = { listOf(Artist("x", "X")) },
            searchLiveConcerts = { error("down") },
        )
        vm.search("x")
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.liveArtists).hasSize(1)
    }

    @Test fun liveSectionFilled() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> listOf(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> listOf(ArchiveItem("book", "Book", isAudio = true)) },
            searchLiveArtists = { listOf(Artist("SomeBand", "Some Band")) },
            searchLiveConcerts = { listOf(concert("show1")) },
        )
        vm.search("some band")
        val s = vm.state.value
        assertThat(s.watch).hasSize(1)
        assertThat(s.listen).hasSize(1)
        assertThat(s.liveArtists.map { it.id }).containsExactly("SomeBand")
        assertThat(s.liveConcerts.map { it.best.identifier }).containsExactly("show1")
    }

    @Test fun liveFailureLeavesOthers() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> listOf(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> listOf(ArchiveItem("book", "Book", isAudio = true)) },
            searchLiveArtists = { error("down") },
            searchLiveConcerts = { error("down") },
        )
        vm.search("sherlock")
        val s = vm.state.value
        assertThat(s.watch).hasSize(1)
        assertThat(s.listen).hasSize(1)
        assertThat(s.liveArtists).isEmpty()
        assertThat(s.liveConcerts).isEmpty()
        assertThat(s.error).isNull()
    }
}
