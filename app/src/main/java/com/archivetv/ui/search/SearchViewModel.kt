package com.archivetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    /** Video results. */
    val watch: List<ArchiveItem> = emptyList(),
    /** Audio results. */
    val listen: List<ArchiveItem> = emptyList(),
    /** Live Music Archive artists and concerts (one card per concert). */
    val liveArtists: List<Artist> = emptyList(),
    val liveConcerts: List<Concert> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Runs the video, audio and Live Music searches in parallel; one failing still shows the others. */
class SearchViewModel(
    private val searchVideo: suspend (text: String, limit: Int) -> List<ArchiveItem>,
    private val searchAudio: suspend (text: String, limit: Int) -> List<ArchiveItem>,
    private val searchLiveArtists: suspend (text: String) -> List<Artist> = { emptyList() },
    private val searchLiveConcerts: suspend (text: String) -> List<Concert> = { emptyList() },
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
            val artists = async { runCatching { searchLiveArtists(q) } }
            val concerts = async { runCatching { searchLiveConcerts(q) } }
            val w = video.await()
            val l = audio.await()
            val a = artists.await()
            val c = concerts.await()
            _state.value = _state.value.copy(
                loading = false,
                watch = w.getOrDefault(emptyList()),
                listen = l.getOrDefault(emptyList()),
                liveArtists = a.getOrDefault(emptyList()),
                liveConcerts = c.getOrDefault(emptyList()),
                error = if (listOf(w, l, a, c).all { it.isFailure }) "Search failed. Try again." else null,
            )
        }
    }
}
