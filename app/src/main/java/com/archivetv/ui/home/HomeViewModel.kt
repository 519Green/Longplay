package com.archivetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.ParallelRows
import com.archivetv.ui.common.RowState
import com.archivetv.data.NextUp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A Next Up card: the item, which file to play, and its card subtitle. */
data class NextUpCard(val item: ArchiveItem, val fileIndex: Int, val label: String)

data class HomeUiState(
    val continueWatching: List<ArchiveItem> = emptyList(),
    val nextUp: List<NextUpCard> = emptyList(),
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
    nextUp: Flow<List<NextUp>> = emptyFlow(),
) : ViewModel() {
    private val rows = ParallelRows(viewModelScope, configuredRows, loadRow)

    private val _state = MutableStateFlow(HomeUiState(rows = rows.state.value))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Titles for Continue Watching, which stores only identifiers locally. */
    private val knownItems = mutableMapOf<String, ArchiveItem>()

    init {
        viewModelScope.launch {
            continueWatching.collect { items ->
                learnTitles(items.map { it.identifier })
                val named = items.map { cw ->
                    knownItems[cw.identifier]
                        ?.let { cw.copy(title = it.title, year = it.year) } ?: cw
                }
                _state.update { it.copy(continueWatching = named) }
            }
        }
        viewModelScope.launch {
            nextUp.collect { list ->
                learnTitles(list.map { it.identifier })
                val cards = list.map { n ->
                    NextUpCard(
                        item = knownItems[n.identifier] ?: ArchiveItem(n.identifier, n.identifier),
                        fileIndex = n.nextIndex,
                        label = "Next: episode ${n.nextIndex + 1} of ${n.fileCount}",
                    )
                }
                _state.update { it.copy(nextUp = cards) }
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

    /** Fetches titles for identifiers not seen yet (one batched request). */
    private suspend fun learnTitles(ids: List<String>) {
        val missing = ids.filter { it !in knownItems }
        if (missing.isNotEmpty()) {
            runCatching { lookupItems(missing) }.onSuccess { knownItems.putAll(it) }
        }
    }

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
