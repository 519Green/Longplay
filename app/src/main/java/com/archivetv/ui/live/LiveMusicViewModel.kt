package com.archivetv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data object Failed : Load<Nothing>
}

sealed interface LiveRow {
    val title: String
    data class Concerts(override val title: String, val state: Load<List<Concert>>) : LiveRow
    data class Artists(override val title: String, val state: Load<List<Artist>>) : LiveRow
}

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** The Live Music discovery page: four rows loaded in parallel, each retryable. */
class LiveMusicViewModel(
    today: Pair<Int, Int>,
    thisDay: suspend (Int, Int) -> List<Concert>,
    topRated: suspend () -> List<Concert>,
    mostPlayed: suspend () -> List<Concert>,
    popularArtists: suspend () -> List<Artist>,
) : ViewModel() {
    private val month = today.first
    private val day = today.second

    private val loaders: List<Pair<String, suspend () -> List<Any>>> = listOf(
        "This Day in History · ${months[month - 1]} $day" to { thisDay(month, day) },
        "Top Rated Shows" to { topRated() },
        "Most Played Shows" to { mostPlayed() },
        "Popular Artists" to { popularArtists() },
    )

    private val _rows = MutableStateFlow(loaders.mapIndexed { i, (title, _) -> row(i, title, Load.Loading) })
    val rows: StateFlow<List<LiveRow>> = _rows.asStateFlow()

    init {
        loaders.indices.forEach(::load)
    }

    fun retry(index: Int) = load(index)

    private fun load(index: Int) {
        val (title, loader) = loaders[index]
        set(index, row(index, title, Load.Loading))
        viewModelScope.launch {
            val state = runCatching { loader() }.fold({ Load.Done(it) }, { Load.Failed })
            set(index, row(index, title, state))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun row(index: Int, title: String, state: Load<List<Any>>): LiveRow =
        if (index == ARTISTS_ROW) LiveRow.Artists(title, state as Load<List<Artist>>)
        else LiveRow.Concerts(title, state as Load<List<Concert>>)

    private fun set(index: Int, row: LiveRow) =
        _rows.update { list -> list.toMutableList().also { it[index] = row } }

    private companion object {
        const val ARTISTS_ROW = 3
    }
}
