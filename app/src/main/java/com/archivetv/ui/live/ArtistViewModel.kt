package com.archivetv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.Concert
import com.archivetv.domain.YearCount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistUiState(
    /** From the first loaded concert; null until then. */
    val name: String? = null,
    /** Null until the (possibly slow) year list arrives. */
    val years: List<YearCount>? = null,
    /** The year list failed (it can take seconds for big artists); the page offers a retry. */
    val yearsFailed: Boolean = false,
    val selectedYear: Int? = null,
    val concerts: Load<List<Concert>> = Load.Loading,
)

/**
 * Artist page. The year of the most-downloaded recording is fetched first so
 * its concerts show at once; the full year list (seconds for big artists)
 * arrives in parallel.
 */
class ArtistViewModel(
    @Suppress("unused") val artistId: String,
    private val startYear: suspend () -> Int?,
    private val years: suspend () -> List<YearCount>,
    private val concerts: suspend (Int) -> List<Concert>,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    private var concertsJob: Job? = null

    init {
        start()
    }

    fun selectYear(year: Int) {
        concertsJob?.cancel()
        _state.update { it.copy(selectedYear = year, concerts = Load.Loading) }
        concertsJob = viewModelScope.launch {
            val result = runCatching { concerts(year) }
            ensureActive() // a superseded year's load must not overwrite the new one
            _state.update { s ->
                s.copy(
                    concerts = result.fold({ Load.Done(it) }, { Load.Failed }),
                    name = s.name ?: result.getOrNull()?.firstOrNull()?.artistName,
                )
            }
        }
    }

    fun retry() {
        val year = _state.value.selectedYear
        if (year != null) selectYear(year) else start()
    }

    fun retryYears() {
        loadYears()
    }

    private fun loadYears() = viewModelScope.async {
        _state.update { it.copy(yearsFailed = false) }
        runCatching { years() }.also { r ->
            _state.update { s -> r.fold({ s.copy(years = it) }, { s.copy(yearsFailed = true) }) }
        }
    }

    private fun start() {
        _state.update { it.copy(concerts = Load.Loading) }
        val known = _state.value.years
        val yearsLoad = if (known != null) CompletableDeferred(Result.success(known)) else loadYears()
        viewModelScope.launch {
            val first = runCatching { startYear() }.getOrNull()
            if (first != null) {
                selectYear(first)
                return@launch
            }
            val list = yearsLoad.await()
            val newest = list.getOrNull()?.lastOrNull()?.year
            when {
                newest != null -> selectYear(newest)
                list.isFailure -> _state.update { it.copy(concerts = Load.Failed) }
                else -> _state.update { it.copy(concerts = Load.Done(emptyList())) }
            }
        }
    }
}
