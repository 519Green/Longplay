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
) {
    val endReached: Boolean get() = total != null && items.size >= total
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
    private val _state = MutableStateFlow(BrowseUiState(title = leaf.title))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var selectedFacet: String? = null
    private var nextPage = 1
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
        _state.update { it.copy(items = emptyList(), total = null, error = null, loading = false) }
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
                    _state.update {
                        it.copy(items = it.items + result.items, total = result.total, loading = false)
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load. Select to retry.") } }
        }
    }

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
