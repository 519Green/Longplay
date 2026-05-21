package com.archivetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.LibraryRepository
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val continueWatching: List<ArchiveItem> = emptyList(),
    val favorites: List<ArchiveItem> = emptyList(),
    val rows: List<BrowseRow> = emptyList(),
    val hero: ArchiveItem? = null,
)

class HomeViewModel(
    private val archive: ArchiveRepository,
    private val library: LibraryRepository,
    private val configuredRows: List<BrowseRow>,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            library.observeContinueWatching().collect { cw ->
                _state.value = _state.value.copy(continueWatching = cw)
            }
        }
        viewModelScope.launch {
            library.observeFavorites().collect { fav ->
                _state.value = _state.value.copy(favorites = fav)
            }
        }
        viewModelScope.launch { loadRows() }
    }

    private suspend fun loadRows() {
        val loaded = configuredRows.map { row ->
            runCatching { archive.browseCollection(row.collectionId, limit = 20) }
                .map { row.copy(items = it) }
                .getOrDefault(row)
        }
        _state.value = _state.value.copy(
            rows = loaded,
            hero = loaded.firstOrNull { it.items.isNotEmpty() }?.items?.first(),
        )
    }
}
