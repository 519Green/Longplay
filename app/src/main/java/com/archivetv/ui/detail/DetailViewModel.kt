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
import com.archivetv.domain.ItemKind
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
) {
    val isAudio: Boolean get() = kind != ItemKind.VIDEO
    val playable: Boolean get() = files.isNotEmpty()
    /** Episode display names with the shared show-name prefix removed. */
    val labels: List<String> by lazy { episodeLabels(files.map { it.title }) }
    val start: StartPoint get() = startPoint(files.map { it.fileName }, progress)
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

class DetailViewModel(
    private val archive: ArchiveRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    private var progressJob: Job? = null

    fun load(identifier: String) {
        viewModelScope.launch {
            runCatching { archive.detail(identifier) }
                .onSuccess { d ->
                    _state.value = DetailUiState(
                        item = d.item,
                        files = d.files,
                        isFavorite = library.isFavorite(identifier),
                        kind = d.kind,
                        isLibriVox = d.isLibriVox,
                    )
                    progressJob?.cancel()
                    progressJob = viewModelScope.launch {
                        library.observeProgress(identifier, d.files.map { it.fileName })
                            .collect { p -> _state.update { it.copy(progress = p) } }
                    }
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
            _state.update { it.copy(isFavorite = library.isFavorite(item.identifier)) }
        }
    }
}
