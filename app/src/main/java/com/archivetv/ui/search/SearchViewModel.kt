package com.archivetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.ArchiveRepository
import com.archivetv.domain.ArchiveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<ArchiveItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val archive: ArchiveRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { archive.search(q, limit = 40) }
                .onSuccess { _state.value = _state.value.copy(loading = false, results = it) }
                .onFailure {
                    _state.value = _state.value.copy(loading = false,
                        error = "Search failed. Try again.")
                }
        }
    }
}
