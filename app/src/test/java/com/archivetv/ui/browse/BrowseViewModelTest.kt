package com.archivetv.ui.browse

import com.archivetv.data.BrowsePage
import com.archivetv.data.BrowseSort
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.archivetv.domain.Facet
import com.archivetv.domain.FacetGroup
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
class BrowseViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val leaf = CategoryNode.Leaf("noir", "Film Noir", CategoryQuery(collections = listOf("Film_Noir")), "decade")
    private val f40 = Facet("1940s", CategoryQuery(yearFrom = 1940, yearTo = 1949))
    private val f50 = Facet("1950s", CategoryQuery(yearFrom = 1950, yearTo = 1959))
    private val f20 = Facet("1920s", CategoryQuery(yearFrom = 1920, yearTo = 1929))
    private val group = FacetGroup("decade", listOf(f20, f40, f50), minCount = 5)

    /** Fake renderer: leaf id plus facet label, so assertions can see what was asked. */
    private fun render(q: CategoryQuery, facet: CategoryQuery?): String =
        "noir" + (facet?.let { "+" + it.yearFrom } ?: "")

    private data class Call(val query: String, val sort: BrowseSort, val page: Int)
    private val calls = mutableListOf<Call>()

    private fun items(prefix: String, n: Int) = (1..n).map { ArchiveItem("$prefix$it", "T$prefix$it") }

    private var lastRows = 0

    private fun vm(
        total: Int = 5,
        counts: Map<String, Int> = mapOf("noir+1920" to 2, "noir+1940" to 485, "noir+1950" to 508),
        leaf: CategoryNode.Leaf = this.leaf,
    ) =
        BrowseViewModel(
            leaf = leaf,
            facetGroup = group,
            render = ::render,
            browse = { q, sort, page, rows ->
                calls += Call(q, sort, page)
                lastRows = rows
                val start = (page - 1) * rows
                BrowsePage(items("$q-p$page-", minOf(rows, total - start).coerceAtLeast(0)), total)
            },
            count = { q -> counts.getValue(q) },
            pageSize = 2,
        )

    @Test fun itemsRepeatedOnALaterPageAreDropped() = runTest {
        // A random order can repeat an item across pages; the grid keys cards by identifier.
        val pages = mapOf(1 to listOf("a", "b"), 2 to listOf("b", "c"))
        val model = BrowseViewModel(
            leaf = leaf, facetGroup = null, render = ::render,
            browse = { _, _, page, _ ->
                BrowsePage(pages.getValue(page).map { ArchiveItem(it, "T$it") }, 4)
            },
            count = { 0 }, pageSize = 2,
        )
        model.loadMore()
        assertThat(model.state.value.items.map { it.identifier }).containsExactly("a", "b", "c").inOrder()
    }

    @Test fun firstPageLoadsWithTotal() = runTest {
        val model = vm()
        val s = model.state.value
        assertThat(s.title).isEqualTo("Film Noir")
        assertThat(s.total).isEqualTo(5)
        assertThat(s.items).hasSize(2)
        assertThat(calls.single()).isEqualTo(Call("noir", BrowseSort.POPULAR, 1))
    }

    @Test fun loadMoreAppendsUntilTotal() = runTest {
        val model = vm()
        model.loadMore(); model.loadMore(); model.loadMore(); model.loadMore()
        assertThat(model.state.value.items).hasSize(5)
        assertThat(calls.map { it.page }).containsExactly(1, 2, 3).inOrder()
        assertThat(model.state.value.endReached).isTrue()
    }

    @Test fun facetChipsShowCountsAndHideBelowMinimum() = runTest {
        val chips = vm().state.value.facets
        assertThat(chips.map { it.label }).containsExactly("1940s", "1950s").inOrder()
        assertThat(chips.map { it.count }).containsExactly(485, 508).inOrder()
        assertThat(chips.none { it.selected }).isTrue()
    }

    @Test fun selectingFacetReloadsFromPageOneAndTogglesOff() = runTest {
        val model = vm()
        model.loadMore()
        model.toggleFacet("1940s")
        assertThat(calls.last()).isEqualTo(Call("noir+1940", BrowseSort.POPULAR, 1))
        assertThat(model.state.value.items.first().identifier).startsWith("noir+1940-p1-")
        assertThat(model.state.value.facets.single { it.selected }.label).isEqualTo("1940s")

        model.toggleFacet("1940s")
        assertThat(calls.last()).isEqualTo(Call("noir", BrowseSort.POPULAR, 1))
        assertThat(model.state.value.facets.none { it.selected }).isTrue()
    }

    @Test fun changingSortReloadsKeepingFacet() = runTest {
        val model = vm()
        model.toggleFacet("1950s")
        model.setSort(BrowseSort.OLDEST)
        assertThat(calls.last()).isEqualTo(Call("noir+1950", BrowseSort.OLDEST, 1))
        assertThat(model.state.value.sort).isEqualTo(BrowseSort.OLDEST)
    }

    @Test fun failedFirstPageSetsError() = runTest {
        val model = BrowseViewModel(
            leaf, null, ::render,
            browse = { _, _, _, _ -> error("offline") },
            count = { 0 },
        )
        assertThat(model.state.value.error).isNotNull()
        assertThat(model.state.value.facets).isEmpty()
    }

    @Test fun playAllUsesCurrentSortAndFacet() = runTest {
        val model = vm(total = 120)
        model.setSort(BrowseSort.OLDEST)
        model.toggleFacet("1940s")
        val items = model.itemsForPlayAll()
        assertThat(calls.last()).isEqualTo(Call("noir+1940", BrowseSort.OLDEST, 1))
        assertThat(lastRows).isEqualTo(50)
        assertThat(items).hasSize(50)
    }

    @Test fun playAllFlagFollowsLeaf() = runTest {
        assertThat(vm().state.value.playAll).isFalse()
        assertThat(vm(leaf = leaf.copy(playAll = true)).state.value.playAll).isTrue()
    }

    // The rights filter can drop some or all docs of a page while numFound stays as the server reported it.
    private fun filteredVm(total: Int, kept: (page: Int) -> Int) =
        BrowseViewModel(
            leaf = leaf,
            facetGroup = null,
            render = ::render,
            browse = { q, sort, page, _ ->
                calls += Call(q, sort, page)
                BrowsePage(items("p$page-", kept(page)), total)
            },
            count = { 0 },
            pageSize = 2,
        )

    @Test fun aPageFilteredToNothingLoadsTheNextOne() = runTest {
        val s = filteredVm(total = 6) { page -> if (page == 1) 0 else 2 }.state.value
        assertThat(calls.map { it.page }).containsExactly(1, 2).inOrder()
        assertThat(s.items).hasSize(2)
        assertThat(s.endReached).isFalse()
    }

    @Test fun everyPageFilteredToNothingEndsEmpty() = runTest {
        val s = filteredVm(total = 4) { 0 }.state.value
        assertThat(calls.map { it.page }).containsExactly(1, 2).inOrder()
        assertThat(s.endReached).isTrue()
        assertThat(s.isEmpty).isTrue()
    }

    @Test fun shortPagesKeepPagingUntilTheServerRunsOut() = runTest {
        val model = filteredVm(total = 4) { 1 }
        assertThat(model.state.value.endReached).isFalse()
        model.loadMore()
        assertThat(model.state.value.items).hasSize(2)
        assertThat(model.state.value.endReached).isTrue()
        assertThat(model.state.value.isEmpty).isFalse()
    }
}
