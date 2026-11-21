package com.archivetv.ui.search

import com.archivetv.domain.ArchiveItem
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
        val vm = SearchViewModel(searchVideo = { _, _ -> error("down") }, searchAudio = { _, _ -> error("down") })
        vm.search("sherlock")
        assertThat(vm.state.value.error).isEqualTo("Search failed. Try again.")
    }
}
