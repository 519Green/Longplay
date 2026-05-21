package com.archivetv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.LibraryRepository
import com.archivetv.domain.ArchiveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: ArchiveItem? = null,
    val playable: Boolean = false,
    val isFavorite: Boolean = false,
    val error: String? = null,
)

class DetailViewModel(
    private val archive: ArchiveRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    fun load(identifier: String) {
        viewModelScope.launch {
            runCatching { archive.detail(identifier) }
                .onSuccess { d ->
                    _state.value = DetailUiState(
                        item = d.item,
                        playable = d.playable != null,
                        isFavorite = library.isFavorite(identifier),
                    )
                }
                .onFailure {
                    _state.value = DetailUiState(error = "Could not load this title.")
                }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            library.toggleFavorite(item)
            _state.value = _state.value.copy(
                isFavorite = library.isFavorite(item.identifier))
        }
    }
}
