package com.archivetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RowState {
    data object Loading : RowState
    data class Loaded(val items: List<ArchiveItem>) : RowState
    data object Failed : RowState
}

data class HomeRow(val config: BrowseRow, val state: RowState)

data class HomeUiState(
    val continueWatching: List<ArchiveItem> = emptyList(),
    val favorites: List<ArchiveItem> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val hero: ArchiveItem? = null,
)

/**
 * Rows load concurrently and publish as each one finishes. The hero is the
 * first item of the first row (in config order) that is not failed or empty,
 * so it does not jump when a later row happens to finish first.
 */
class HomeViewModel(
    private val configuredRows: List<BrowseRow>,
    private val loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
    private val lookupItems: suspend (List<String>) -> Map<String, ArchiveItem>,
    continueWatching: Flow<List<ArchiveItem>>,
    favorites: Flow<List<ArchiveItem>>,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeUiState(rows = configuredRows.map { HomeRow(it, RowState.Loading) })
    )
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
        configuredRows.indices.forEach(::load)
    }

    fun retry(index: Int) = load(index)

    private fun load(index: Int) {
        setRow(index, RowState.Loading)
        viewModelScope.launch {
            val result = runCatching { loadRow(configuredRows[index]) }
            setRow(index, result.fold({ RowState.Loaded(it) }, { RowState.Failed }))
        }
    }

    private fun setRow(index: Int, rowState: RowState) {
        _state.update { s ->
            val rows = s.rows.toMutableList().also { it[index] = it[index].copy(state = rowState) }
            s.copy(rows = rows, hero = heroFrom(rows))
        }
    }

    private fun heroFrom(rows: List<HomeRow>): ArchiveItem? {
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
