package com.archivetv.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.data.BrowseSort
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.domain.CategoryNode
import com.archivetv.ui.common.ParallelRows
import com.archivetv.ui.common.TileRow
import com.archivetv.ui.common.contentRows
import com.archivetv.ui.theme.Dimens

class SectionViewModel(
    rows: List<BrowseRow>,
    loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
) : ViewModel() {
    private val parallel = ParallelRows(viewModelScope, rows, loadRow).also { it.start() }
    val rows = parallel.state
    fun retry(index: Int) = parallel.retry(index)
}

/**
 * A top-level section (Movies, TV, ...): a tile row for its sub-groups
 * (By Genre, By Decade, Shows), then a content row per direct leaf.
 */
@Composable
fun SectionScreen(
    sectionId: String,
    onItemClick: (ArchiveItem) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenLeaf: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val section = app.container.catalog.group(sectionId) ?: run {
        Text("Unknown section")
        return
    }
    val viewModel: SectionViewModel = viewModel(key = "section-$sectionId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SectionViewModel(
                rows = section.children.filterIsInstance<CategoryNode.Leaf>().map(app.container::rowFor),
                loadRow = {
                    app.container.archiveRepository.browse(it.query, BrowseSort.POPULAR, page = 1, rows = 20).items
                },
            ) as T
    })
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val tiles = remember(section) {
        section.children.filterIsInstance<CategoryNode.Group>().map { g -> g.title to { onOpenGroup(g.id) } }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Dimens.OverscanVertical, bottom = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text(
                section.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = Dimens.ContentStart),
            )
        }
        if (tiles.isNotEmpty()) {
            item(key = "tiles") { TileRow(title = null, tiles = tiles) }
        }
        contentRows(rows, onItemClick, viewModel::retry, onSeeAll = { onOpenLeaf(it.config.id) })
    }
}
