package com.archivetv.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.BrowsePage
import com.archivetv.data.BrowseSort
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.archivetv.domain.FacetGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FacetChip(val label: String, val count: Int, val selected: Boolean)

data class BrowseUiState(
    val title: String,
    val sort: BrowseSort = BrowseSort.POPULAR,
    val facets: List<FacetChip> = emptyList(),
    val items: List<ArchiveItem> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Music leaves offer Play all / Shuffle. */
    val playAll: Boolean = false,
    /** Server results paged through so far; above items.size when the rights filter drops docs. */
    val fetched: Int = 0,
) {
    val endReached: Boolean get() = total != null && (items.size >= total || fetched >= total)
    /** Nothing to show: no matches, or every match was dropped by the rights filter. */
    val isEmpty: Boolean get() = endReached && items.isEmpty()
}

/**
 * One catalog leaf as a paged grid. Sort and the selected facet both restart
 * from page 1; facet counts are fetched once (rows=0) and chips below the
 * group's minCount are hidden.
 */
class BrowseViewModel(
    private val leaf: CategoryNode.Leaf,
    private val facetGroup: FacetGroup?,
    private val render: (CategoryQuery, CategoryQuery?) -> String,
    private val browse: suspend (query: String, sort: BrowseSort, page: Int, rows: Int) -> BrowsePage,
    private val count: suspend (String) -> Int,
    private val pageSize: Int = 40,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState(title = leaf.title, playAll = leaf.playAll))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var selectedFacet: String? = null
    private var nextPage = 1
    /** Pages in a row that the rights filter emptied; fetching stops after [MAX_EMPTY_PAGES]. */
    private var emptyPages = 0
    private var loadJob: Job? = null

    init {
        restart()
        loadFacetCounts()
    }

    fun setSort(sort: BrowseSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        restart()
    }

    fun toggleFacet(label: String) {
        selectedFacet = if (selectedFacet == label) null else label
        _state.update { s -> s.copy(facets = s.facets.map { it.copy(selected = it.label == selectedFacet) }) }
        restart()
    }

    /** The first [limit] results for the current sort and chip, for Play all / Shuffle. */
    suspend fun itemsForPlayAll(limit: Int = 50): List<ArchiveItem> =
        browse(currentQuery(), _state.value.sort, 1, limit).items

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.error != null) return
        loadPage()
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        if (_state.value.items.isEmpty()) restart() else loadPage()
    }

    private fun restart() {
        loadJob?.cancel()
        nextPage = 1
        emptyPages = 0
        _state.update { it.copy(items = emptyList(), total = null, error = null, loading = false, fetched = 0) }
        loadPage()
    }

    private fun loadPage() {
        val query = currentQuery()
        val sort = _state.value.sort
        val page = nextPage
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            runCatching { browse(query, sort, page, pageSize) }
                .onSuccess { result ->
                    nextPage = page + 1
                    // A random order can repeat an item from an earlier page; the grid keys cards by identifier.
                    val onScreen = _state.value.items.mapTo(HashSet()) { it.identifier }
                    val added = result.items.filter { it.identifier !in onScreen }.distinctBy { it.identifier }
                    emptyPages = if (added.isEmpty()) emptyPages + 1 else 0
                    val fetched = if (emptyPages >= MAX_EMPTY_PAGES) result.total else minOf(page * pageSize, result.total)
                    _state.update {
                        it.copy(items = it.items + added, total = result.total, loading = false, fetched = fetched)
                    }
                    // A page the filter emptied adds no cards, so the grid would never ask for more.
                    if (added.isEmpty() && !_state.value.endReached) loadPage()
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load. Select to retry.") } }
        }
    }

    private companion object { const val MAX_EMPTY_PAGES = 5 }

    private fun currentQuery(): String {
        val facet = facetGroup?.facets?.firstOrNull { it.label == selectedFacet }
        return render(leaf.query, facet?.query)
    }

    private fun loadFacetCounts() {
        val group = facetGroup ?: return
        viewModelScope.launch {
            val counted = group.facets.map { f ->
                async { f.label to runCatching { count(render(leaf.query, f.query)) }.getOrDefault(0) }
            }.awaitAll()
            val chips = counted
                .filter { (_, n) -> n >= group.minCount }
                .map { (label, n) -> FacetChip(label, n, selected = label == selectedFacet) }
            _state.update { it.copy(facets = chips) }
        }
    }
}
