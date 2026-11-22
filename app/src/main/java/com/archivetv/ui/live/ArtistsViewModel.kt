package com.archivetv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.Artist
import com.archivetv.domain.jumpLetter
import com.archivetv.domain.searchKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val query: String = "",
    val shown: List<Artist> = emptyList(),
    /** Jump letters present in [shown], '#' first, then A-Z. */
    val letters: List<Char> = emptyList(),
)

/** All Artists: the full LMA artist list, filtered as you type, with an A-Z jump. */
class ArtistsViewModel(private val load: suspend () -> List<Artist>) : ViewModel() {
    private var all: List<Artist> = emptyList()
    private var keys: List<String> = emptyList()

    private val _state = MutableStateFlow(ArtistsUiState())
    val state: StateFlow<ArtistsUiState> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatching { load() }
                .onSuccess { list ->
                    all = list
                    keys = list.map { searchKey(it.name) }
                    _state.update { it.copy(loading = false) }
                    refilter(_state.value.query)
                }
                .onFailure { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun setQuery(q: String) = refilter(q)

    /** Index in `shown` of the first artist filed under [letter], or -1. */
    fun indexOf(letter: Char): Int = _state.value.shown.indexOfFirst { jumpLetter(it.name) == letter }

    private fun refilter(q: String) {
        val needle = searchKey(q)
        val shown = if (needle.isEmpty()) all else all.filterIndexed { i, _ -> needle in keys[i] }
        val present = shown.map { jumpLetter(it.name) }.toSet()
        val letters = (listOf('#') + ('A'..'Z')).filter { it in present }
        _state.update { it.copy(query = q, shown = shown, letters = letters) }
    }
}
