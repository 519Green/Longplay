package com.archivetv.ui.common

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import kotlinx.coroutines.CoroutineScope
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

data class ContentRow(val config: BrowseRow, val state: RowState)

/**
 * Loads every row concurrently and publishes each as it finishes, so one slow
 * or failing request does not hold up the screen. Used by Home and Section.
 */
class ParallelRows(
    private val scope: CoroutineScope,
    private val rows: List<BrowseRow>,
    private val loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
) {
    private val _state = MutableStateFlow(rows.map { ContentRow(it, RowState.Loading) })
    val state: StateFlow<List<ContentRow>> = _state.asStateFlow()

    fun start() = rows.indices.forEach(::load)

    fun retry(index: Int) = load(index)

    private fun load(index: Int) {
        set(index, RowState.Loading)
        scope.launch {
            val result = runCatching { loadRow(rows[index]) }
            set(index, result.fold({ RowState.Loaded(it) }, { RowState.Failed }))
        }
    }

    private fun set(index: Int, rowState: RowState) {
        _state.update { list ->
            list.toMutableList().also { it[index] = it[index].copy(state = rowState) }
        }
    }
}
