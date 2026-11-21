package com.archivetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.ParallelRows
import com.archivetv.ui.common.RowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val continueWatching: List<ArchiveItem> = emptyList(),
    val favorites: List<ArchiveItem> = emptyList(),
    val rows: List<ContentRow> = emptyList(),
    val hero: ArchiveItem? = null,
)

/**
 * The hero is the first item of the first row (in config order) that is not
 * failed or empty, so it does not jump when a later row finishes first.
 */
class HomeViewModel(
    configuredRows: List<BrowseRow>,
    loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
    private val lookupItems: suspend (List<String>) -> Map<String, ArchiveItem>,
    continueWatching: Flow<List<ArchiveItem>>,
    favorites: Flow<List<ArchiveItem>>,
) : ViewModel() {
    private val rows = ParallelRows(viewModelScope, configuredRows, loadRow)

    private val _state = MutableStateFlow(HomeUiState(rows = rows.state.value))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Titles for Continue Watching, which stores only identifiers locally. */
    private val knownItems = mutableMapOf<String, ArchiveItem>()

    init {
        viewModelScope.launch {
            continueWatching.collect { items ->
                val missing = items.map { it.identifier }.filter { it !in knownItems }
                if (missing.isNotEmpty()) {
                    runCatching { lookupItems(missing) }.onSuccess { knownItems.putAll(it) }
                }
                val named = items.map { cw ->
                    knownItems[cw.identifier]
                        ?.let { cw.copy(title = it.title, year = it.year) } ?: cw
                }
                _state.update { it.copy(continueWatching = named) }
            }
        }
        viewModelScope.launch {
            favorites.collect { fav -> _state.update { it.copy(favorites = fav) } }
        }
        viewModelScope.launch {
            rows.state.collect { list -> _state.update { it.copy(rows = list, hero = heroFrom(list)) } }
        }
        rows.start()
    }

    fun retry(index: Int) = rows.retry(index)

    private fun heroFrom(rows: List<ContentRow>): ArchiveItem? {
        for (row in rows) {
            when (val st = row.state) {
                RowState.Loading -> return null
                is RowState.Loaded -> if (st.items.isNotEmpty()) return st.items.first()
                RowState.Failed -> Unit
            }
        }
        return null
    }
}
