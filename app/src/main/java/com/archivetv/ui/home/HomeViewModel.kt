package com.archivetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.ParallelRows
import com.archivetv.ui.common.RowState
import com.archivetv.data.ListenCard
import com.archivetv.data.NextUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One hero slide: an item already checked to have playable files. */
data class HeroSlide(
    val item: ArchiveItem,
    /** Display year (see displayYear); null when untrustworthy. */
    val year: String? = null,
    val description: String? = null,
    val isAudio: Boolean = false,
)

/** A Next Up card: the item, which file to play, and its card subtitle. */
data class NextUpCard(val item: ArchiveItem, val fileIndex: Int, val label: String)

data class HomeUiState(
    val continueWatching: List<ArchiveItem> = emptyList(),
    val nextUp: List<NextUpCard> = emptyList(),
    /** Spoken-word items to come back to; reuses NextUpCard (label empty when resuming). */
    val continueListening: List<NextUpCard> = emptyList(),
    val favorites: List<ArchiveItem> = emptyList(),
    val rows: List<ContentRow> = emptyList(),
    /** Up to [HERO_SLIDES] playable items from the top of the first loaded row, in row order. */
    val heroSlides: List<HeroSlide> = emptyList(),
)

const val HERO_SLIDES = 5
/** Candidates checked for playable files; some popular items have none left on IA. */
const val HERO_CANDIDATES = 8

/**
 * Hero slides come from the first row (in config order) that is not failed or
 * empty, so they do not jump when a later row finishes first. Each candidate
 * is checked with [heroCheck] (null = nothing playable) and slides are
 * published in row order as soon as every earlier candidate has been checked.
 */
class HomeViewModel(
    configuredRows: List<BrowseRow>,
    loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
    private val lookupItems: suspend (List<String>) -> Map<String, ArchiveItem>,
    continueWatching: Flow<List<ArchiveItem>>,
    favorites: Flow<List<ArchiveItem>>,
    nextUp: Flow<List<NextUp>> = emptyFlow(),
    continueListening: Flow<List<ListenCard>> = emptyFlow(),
    private val heroCheck: suspend (ArchiveItem) -> HeroSlide? = { HeroSlide(it) },
    /** Saved identifiers the parental filter hides; see [com.archivetv.data.SavedFilter]. */
    private val hiddenIds: suspend (List<String>) -> Set<String> = { emptySet() },
) : ViewModel() {
    private val rows = ParallelRows(viewModelScope, configuredRows, loadRow)

    private val _state = MutableStateFlow(HomeUiState(rows = rows.state.value))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Titles for Continue Watching, which stores only identifiers locally. */
    private val knownItems = mutableMapOf<String, ArchiveItem>()

    init {
        viewModelScope.launch {
            continueWatching.collect { all ->
                val items = visible(all) { it.identifier }
                learnTitles(items.map { it.identifier })
                val named = items.map { cw ->
                    knownItems[cw.identifier]
                        ?.let { cw.copy(title = it.title, year = it.year) } ?: cw
                }
                _state.update { it.copy(continueWatching = named) }
            }
        }
        viewModelScope.launch {
            nextUp.collect { all ->
                val list = visible(all) { it.identifier }
                learnTitles(list.map { it.identifier })
                val cards = list.map { n ->
                    NextUpCard(
                        item = knownItems[n.identifier] ?: ArchiveItem(n.identifier, n.identifier),
                        fileIndex = n.nextIndex,
                        label = "Next: episode ${n.nextIndex + 1} of ${n.fileCount}",
                    )
                }
                _state.update { it.copy(nextUp = cards) }
            }
        }
        viewModelScope.launch {
            continueListening.collect { all ->
                val list = visible(all) { it.identifier }
                learnTitles(list.map { it.identifier })
                val cards = list.map { c ->
                    val known = knownItems[c.identifier] ?: ArchiveItem(c.identifier, c.identifier)
                    NextUpCard(
                        item = known.copy(progress = c.progress),
                        fileIndex = c.fileIndex,
                        label = if (c.isNext) "Next: ${c.fileIndex + 1} of ${c.fileCount}" else "",
                    )
                }
                _state.update { it.copy(continueListening = cards) }
            }
        }
        viewModelScope.launch {
            favorites.collect { all ->
                val fav = visible(all) { it.identifier }
                _state.update { it.copy(favorites = fav) }
            }
        }
        viewModelScope.launch {
            rows.state.collect { list ->
                _state.update { it.copy(rows = list) }
                updateHero(list)
            }
        }
        rows.start()
    }

    fun retry(index: Int) = rows.retry(index)

    private suspend fun <T> visible(items: List<T>, id: (T) -> String): List<T> {
        val hidden = hiddenIds(items.map(id))
        return if (hidden.isEmpty()) items else items.filterNot { id(it) in hidden }
    }

    /** Fetches titles for identifiers not seen yet (one batched request). */
    private suspend fun learnTitles(ids: List<String>) {
        val missing = ids.filter { it !in knownItems }
        if (missing.isNotEmpty()) {
            runCatching { lookupItems(missing) }.onSuccess { knownItems.putAll(it) }
        }
    }

    private var heroKey: List<String>? = null
    private var heroJob: Job? = null

    private fun updateHero(rows: List<ContentRow>) {
        val candidates = heroSource(rows)?.take(HERO_CANDIDATES) ?: return
        val key = candidates.map { it.identifier }
        if (key == heroKey) return
        heroKey = key
        heroJob?.cancel()
        heroJob = viewModelScope.launch {
            val checked = arrayOfNulls<HeroSlide>(candidates.size)
            val done = BooleanArray(candidates.size)
            fun publish() {
                val slides = mutableListOf<HeroSlide>()
                for (i in candidates.indices) {
                    if (!done[i] || slides.size == HERO_SLIDES) break
                    checked[i]?.let(slides::add)
                }
                // Keep the current slides until the new set has one, so the
                // focused hero does not vanish while a retried row is checked.
                if (slides.isNotEmpty() || done.all { it }) _state.update { it.copy(heroSlides = slides) }
            }
            candidates.forEachIndexed { i, item ->
                launch {
                    checked[i] = try {
                        heroCheck(item)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    done[i] = true
                    publish()
                }
            }
        }
    }

    /** Items of the first non-failed, non-empty row; null while an earlier row is still loading. */
    private fun heroSource(rows: List<ContentRow>): List<ArchiveItem>? {
        for (row in rows) {
            when (val st = row.state) {
                RowState.Loading -> return null
                is RowState.Loaded -> if (st.items.isNotEmpty()) return st.items
                RowState.Failed -> Unit
            }
        }
        return null
    }
}
