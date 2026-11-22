package com.archivetv.ui.search

import com.archivetv.data.BrowsePage
import com.archivetv.data.RecentSearches
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.ScopeKind
import com.archivetv.domain.SearchScope
import com.archivetv.domain.Artist
import com.archivetv.ui.live.concert
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
private fun page(vararg items: ArchiveItem, total: Int = items.size) = BrowsePage(items.toList(), total)

class SearchViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun submitFillsWatchAndListen() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> page(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> page(ArchiveItem("book", "Book", isAudio = true)) },
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
            searchVideo = { _, _ -> page(ArchiveItem("film", "Film")) },
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
            searchVideo = { _, _ -> page(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> page(ArchiveItem("book", "Book", isAudio = true)) },
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
            searchVideo = { _, _ -> page(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> page(ArchiveItem("book", "Book", isAudio = true)) },
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

    @Test fun totalsComeFromTheSearchCount() = runTest {
        val vm = SearchViewModel(
            searchVideo = { _, _ -> page(ArchiveItem("film", "Film"), total = 1234) },
            searchAudio = { _, _ -> page(total = 0) },
        )
        vm.search("night")
        assertThat(vm.state.value.watchTotal).isEqualTo(1234)
        assertThat(vm.state.value.listenTotal).isEqualTo(0)
    }

    @Test fun videoScopeRunsOnlyTheVideoSearch() = runTest {
        val tv = SearchScope("tv", "TV", ScopeKind.VIDEO, listOf("classic_tv"))
        val asked = mutableListOf<String>()
        val vm = SearchViewModel(
            searchVideo = { scope, _ -> asked += "video:" + scope.id; page(ArchiveItem("ep", "Ep")) },
            searchAudio = { _, _ -> asked += "audio"; page() },
            searchLiveArtists = { asked += "live"; emptyList() },
            scopes = listOf(SearchScope.All, tv, SearchScope.Everything),
        )
        vm.onQueryChange("lucy")
        vm.setScope(tv)
        assertThat(asked).containsExactly("video:tv")
        assertThat(vm.state.value.watch).hasSize(1)
    }

    @Test fun typingSearchesAfterAPauseAndIsNotRemembered() = runTest {
        var calls = 0
        val vm = SearchViewModel(
            searchVideo = { _, _ -> calls++; page(ArchiveItem("film", "Film")) },
            searchAudio = { _, _ -> page() },
        )
        vm.onQueryChange("ni")
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 100)
        vm.onQueryChange("nig")
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 100)
        assertThat(calls).isEqualTo(0)
        advanceTimeBy(200)
        assertThat(calls).isEqualTo(1)
        assertThat(vm.state.value.searched).isEqualTo("nig")
        assertThat(vm.state.value.recent).isEmpty()
        vm.rememberQuery()
        assertThat(vm.state.value.recent).containsExactly("nig")
    }

    @Test fun oneLetterDoesNotSearch() = runTest {
        var calls = 0
        val vm = SearchViewModel(searchVideo = { _, _ -> calls++; page() }, searchAudio = { _, _ -> page() })
        vm.onQueryChange("n")
        advanceTimeBy(SEARCH_DEBOUNCE_MS * 2)
        assertThat(calls).isEqualTo(0)
    }

    @Test fun newerSearchCancelsOlder() = runTest {
        val slow = CompletableDeferred<BrowsePage>()
        val vm = SearchViewModel(
            searchVideo = { _, q -> if (q == "old") slow.await() else page(ArchiveItem("new", "New")) },
            searchAudio = { _, _ -> page() },
        )
        vm.search("old")
        vm.search("new")
        slow.complete(page(ArchiveItem("old", "Old")))
        assertThat(vm.state.value.watch.map { it.identifier }).containsExactly("new")
        assertThat(vm.state.value.searched).isEqualTo("new")
    }

    @Test fun explicitSearchIsRememberedNewestFirst() = runTest {
        val store = RecentSearches.InMemory(listOf("Night", "dog"))
        val vm = SearchViewModel(searchVideo = { _, _ -> page() }, searchAudio = { _, _ -> page() }, recentStore = store)
        assertThat(vm.state.value.recent).containsExactly("Night", "dog").inOrder()
        vm.search("night")
        assertThat(vm.state.value.recent).containsExactly("night", "dog").inOrder()
    }

    @Test fun noResultsOnlyAfterACompletedEmptySearch() = runTest {
        val vm = SearchViewModel(searchVideo = { _, _ -> page() }, searchAudio = { _, _ -> page() })
        assertThat(vm.state.value.noResults).isFalse()
        vm.search("zzzz")
        assertThat(vm.state.value.noResults).isTrue()
        assertThat(vm.state.value.error).isNull()
    }
}
