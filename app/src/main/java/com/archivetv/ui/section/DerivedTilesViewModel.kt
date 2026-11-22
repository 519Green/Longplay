package com.archivetv.ui.section

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.DerivedEntry
import com.archivetv.ui.live.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Entries of one derived group (TV Shows, By Studio, ...), loaded live with retry. */
class DerivedTilesViewModel(private val load: suspend () -> List<DerivedEntry>) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<DerivedEntry>>>(Load.Loading)
    val state: StateFlow<Load<List<DerivedEntry>>> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching { load() }.fold({ Load.Done(it) }, { Load.Failed })
        }
    }
}

/** A derived entry as a Browse leaf; facet chips follow the group's facet group. */
fun derivedLeaf(node: CategoryNode.Derived, entry: DerivedEntry): CategoryNode.Leaf =
    CategoryNode.Leaf(
        id = "derived-${node.id}-${entry.key}",
        title = entry.label,
        query = entry.query,
        facetGroup = node.facetGroup,
    )
