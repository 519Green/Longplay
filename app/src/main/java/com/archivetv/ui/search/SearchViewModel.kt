package com.archivetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.BrowsePage
import com.archivetv.data.RecentSearches
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import com.archivetv.domain.SearchScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val scopes: List<SearchScope> = listOf(SearchScope.All, SearchScope.Everything),
    val scope: SearchScope = SearchScope.All,
    /** Video results and how many matched in all. */
    val watch: List<ArchiveItem> = emptyList(),
    val watchTotal: Int = 0,
    /** Audio results and how many matched in all. */
    val listen: List<ArchiveItem> = emptyList(),
    val listenTotal: Int = 0,
    /** Live Music Archive artists and concerts (one card per concert). */
    val liveArtists: List<Artist> = emptyList(),
    val liveConcerts: List<Concert> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** The query the shown results are for; null before the first search. */
    val searched: String? = null,
    val recent: List<String> = emptyList(),
) {
    val hasResults: Boolean
        get() = watch.isNotEmpty() || listen.isNotEmpty() || liveArtists.isNotEmpty() || liveConcerts.isNotEmpty()
    val noResults: Boolean get() = !loading && error == null && searched != null && !hasResults
}

/** Search delay after the last keystroke, so results fill in behind the keyboard. */
const val SEARCH_DEBOUNCE_MS = 500L

/**
 * Runs the searches the chosen chip asks for in parallel; one failing still
 * shows the others. Typing searches after [SEARCH_DEBOUNCE_MS]; a newer
 * search cancels an older one so results never arrive out of order. Only
 * explicit searches (Enter, the Search button, voice) and opened results are
 * added to the recent list, not every pause in typing.
 */
class SearchViewModel(
    private val searchVideo: suspend (SearchScope, String) -> BrowsePage,
    private val searchAudio: suspend (SearchScope, String) -> BrowsePage,
    private val searchLiveArtists: suspend (text: String) -> List<Artist> = { emptyList() },
    private val searchLiveConcerts: suspend (text: String) -> List<Concert> = { emptyList() },
    scopes: List<SearchScope> = listOf(SearchScope.All, SearchScope.Everything),
    private val recentStore: RecentSearches = RecentSearches.InMemory(),
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState(scopes = scopes, recent = recentStore.load()))
    val state: StateFlow<SearchUiState> = _state

    private var debounceJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        debounceJob?.cancel()
        if (q.trim().length >= 2) {
            debounceJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                run(remember = false)
            }
        }
    }

    /** Set the query (e.g. from voice input or a recent search) and run it in one step. */
    fun search(text: String) {
        _state.update { it.copy(query = text) }
        submit()
    }

    fun submit() {
        debounceJob?.cancel()
        run(remember = true)
    }

    fun setScope(scope: SearchScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        debounceJob?.cancel()
        run(remember = false)
    }

    /** A result was opened: the query was worth keeping even if it came from typing. */
    fun rememberQuery() {
        val q = _state.value.searched ?: return
        _state.update { it.copy(recent = recentStore.add(q)) }
    }

    private fun run(remember: Boolean) {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        if (remember) _state.update { it.copy(recent = recentStore.add(q)) }
        val scope = _state.value.scope
        searchJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val video = if (scope.searchesVideo) async { runCatching { searchVideo(scope, q) } } else null
            val audio = if (scope.searchesAudio) async { runCatching { searchAudio(scope, q) } } else null
            val artists = if (scope.searchesLive) async { runCatching { searchLiveArtists(q) } } else null
            val concerts = if (scope.searchesLive) async { runCatching { searchLiveConcerts(q) } } else null
            val w = video?.await()
            val l = audio?.await()
            val a = artists?.await()
            val c = concerts?.await()
            val attempted = listOfNotNull(w, l, a, c)
            _state.update {
                it.copy(
                    loading = false,
                    searched = q,
                    watch = w?.getOrNull()?.items.orEmpty(),
                    watchTotal = w?.getOrNull()?.total ?: 0,
                    listen = l?.getOrNull()?.items.orEmpty(),
                    listenTotal = l?.getOrNull()?.total ?: 0,
                    liveArtists = a?.getOrNull().orEmpty(),
                    liveConcerts = c?.getOrNull().orEmpty(),
                    error = if (attempted.all { r -> r.isFailure }) "Search failed. Try again." else null,
                )
            }
        }
    }
}
