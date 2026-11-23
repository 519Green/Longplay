package com.archivetv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.LibraryRepository
import com.archivetv.data.StartPoint
import com.archivetv.data.db.ResumeEntity
import com.archivetv.data.episodeLabels
import com.archivetv.data.startPoint
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.ItemFacts
import com.archivetv.domain.moreLikeThisCollection
import com.archivetv.domain.ItemKind
import com.archivetv.domain.LiveInfo
import com.archivetv.domain.Recording
import com.archivetv.domain.PlayableFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: ArchiveItem? = null,
    val files: List<PlayableFile> = emptyList(),
    /** Saved position per file name; updates live when returning from the player. */
    val progress: Map<String, ResumeEntity> = emptyMap(),
    val isFavorite: Boolean = false,
    val error: String? = null,
    val kind: ItemKind = ItemKind.VIDEO,
    val isLibriVox: Boolean = false,
    /** Set for Live Music Archive recordings. */
    val live: LiveInfo? = null,
    /** The concert's other recordings, best first (LMA only). */
    val otherRecordings: List<Recording> = emptyList(),
    val facts: ItemFacts = ItemFacts(),
    /** Popular items from the same collection (not for LMA recordings). */
    val moreLikeThis: List<ArchiveItem> = emptyList(),
) {
    val isAudio: Boolean get() = kind != ItemKind.VIDEO
    val playable: Boolean get() = files.isNotEmpty()
    /** Episode display names with the shared show-name prefix removed. */
    val labels: List<String> by lazy { episodeLabels(files.map { it.title }) }
    val start: StartPoint get() = startPoint(files.map { it.fileName }, progress)
    /** Why Play is disabled, shown under the buttons. */
    val unavailableReason: String?
        get() = if (item == null || playable) null
        else if (isAudio) "No playable audio file in this item" else "No playable video file in this item"
}

/** Heading above an item's file list: "Chapters · 12", "Episodes · 973", "Tracks · 12". */
fun listHeader(kind: ItemKind, isLibriVox: Boolean, count: Int): String {
    val noun = when {
        kind == ItemKind.MUSIC -> "Tracks"
        kind == ItemKind.SPOKEN && isLibriVox -> "Chapters"
        else -> "Episodes"
    }
    return "$noun · $count"
}

/** "1977-05-08 · Barton Hall · Ithaca, NY" under an LMA recording's title; blank parts skipped. */
fun concertLine(live: LiveInfo): String {
    val part = when (live.showPart) {
        "early" -> " (Early Show)"
        "late" -> " (Late Show)"
        else -> ""
    }
    return (listOf(live.date + part) + listOfNotNull(live.venue, live.city).filter { it.isNotBlank() })
        .joinToString(" · ")
}

/** An Other recordings row: the taper's source notes, first 60 characters. */
fun recordingHeadline(r: Recording): String =
    r.source?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: "Recording"

/** "★ 4.9 · 29 reviews"; null when unrated. */
fun recordingRating(r: Recording): String? {
    val rating = r.rating?.takeIf { it > 0.0 } ?: return null
    val reviews = if (r.reviews == 1) "1 review" else "${r.reviews} reviews"
    return "★ " + String.format(java.util.Locale.US, "%.1f", rating) + " · " + reviews
}

class DetailViewModel(
    private val archive: ArchiveRepository,
    private val library: LibraryRepository,
    private val recordings: suspend (artistId: String, date: String, showPart: String) -> List<Recording> = { _, _, _ -> emptyList() },
    private val moreLike: suspend (collection: String, mediatype: String, exclude: String) -> List<ArchiveItem> =
        { c, m, e -> archive.moreLikeThis(c, m, e) },
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    private var progressJob: Job? = null

    fun load(identifier: String) {
        // Coming back to this screen re-runs the caller's effect; keep what is loaded
        // (More like this, Other recordings) so focus can return to it.
        if (_state.value.item?.identifier == identifier) return
        viewModelScope.launch {
            runCatching { archive.detail(identifier) }
                .onSuccess { d ->
                    _state.value = DetailUiState(
                        item = d.item,
                        files = d.files,
                        isFavorite = library.isFavorite(identifier),
                        kind = d.kind,
                        isLibriVox = d.isLibriVox,
                        live = d.live,
                        facts = d.facts,
                    )
                    // Live Music Archive items have Other recordings instead (and are mediatype etree).
                    val related = if (d.live == null && "etree" !in d.collections) moreLikeThisCollection(d.collections) else null
                    related?.let { collection ->
                        viewModelScope.launch {
                            val mediatype = if (d.kind == ItemKind.VIDEO) "movies" else "audio"
                            runCatching { moreLike(collection, mediatype, identifier) }.onSuccess { list ->
                                _state.update { s -> s.copy(moreLikeThis = list) }
                            }
                        }
                    }
                    d.live?.let { live ->
                        viewModelScope.launch {
                            runCatching { recordings(live.artistId, live.date, live.showPart) }.onSuccess { list ->
                                _state.update { s -> s.copy(otherRecordings = list.filter { it.identifier != identifier }) }
                            }
                        }
                    }
                    progressJob?.cancel()
                    progressJob = viewModelScope.launch {
                        library.observeProgress(identifier, d.files.map { it.fileName })
                            .collect { p -> _state.update { it.copy(progress = p) } }
                    }
                }
                .onFailure {
                    _state.value = DetailUiState(
                        error = if (it is com.archivetv.data.HiddenByParentalFilter) com.archivetv.data.PARENTAL_BLOCK_TEXT
                        else "Could not load this title.",
                    )
                }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            library.toggleFavorite(item)
            _state.update { it.copy(isFavorite = library.isFavorite(item.identifier)) }
        }
    }
}
