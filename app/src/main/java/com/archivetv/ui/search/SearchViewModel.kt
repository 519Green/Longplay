package com.archivetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import com.archivetv.domain.ArchiveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    /** Video results. */
    val watch: List<ArchiveItem> = emptyList(),
    /** Audio results. */
    val listen: List<ArchiveItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Runs the video and audio searches in parallel; one failing still shows the other. */
class SearchViewModel(
    private val searchVideo: suspend (text: String, limit: Int) -> List<ArchiveItem>,
    private val searchAudio: suspend (text: String, limit: Int) -> List<ArchiveItem>,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    /** Set the query (e.g. from voice input) and run the search in one step. */
    fun search(text: String) {
        _state.value = _state.value.copy(query = text)
        submit()
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val video = async { runCatching { searchVideo(q, 40) } }
            val audio = async { runCatching { searchAudio(q, 40) } }
            val w = video.await()
            val l = audio.await()
            _state.value = _state.value.copy(
                loading = false,
                watch = w.getOrDefault(emptyList()),
                listen = l.getOrDefault(emptyList()),
                error = if (w.isFailure && l.isFailure) "Search failed. Try again." else null,
            )
        }
    }
}
