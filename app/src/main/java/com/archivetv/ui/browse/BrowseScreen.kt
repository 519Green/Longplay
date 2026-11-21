package com.archivetv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.data.BrowseSort
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.theme.Dimens
import java.text.NumberFormat

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    leafId: String,
    onItemClick: (ArchiveItem) -> Unit,
    viewModel: BrowseViewModel? = browseViewModel(leafId),
) {
    if (viewModel == null) {
        Text("Unknown category", modifier = Modifier.padding(Dimens.OverscanHorizontal))
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstItemFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty() && !initialFocusDone) {
            runCatching { firstItemFocus.requestFocus() }
            initialFocusDone = true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                start = Dimens.OverscanHorizontal,
                top = Dimens.OverscanVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            state.total?.let { "${NumberFormat.getIntegerInstance().format(it)} titles" } ?: "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = Dimens.OverscanHorizontal),
        ) {
            items(BrowseSort.entries, key = { "sort-${it.name}" }) { sort ->
                FilterChip(selected = state.sort == sort, onClick = { viewModel.setSort(sort) }) {
                    Text(sort.label)
                }
            }
            items(state.facets, key = { "facet-${it.label}" }) { chip ->
                FilterChip(selected = chip.selected, onClick = { viewModel.toggleFacet(chip.label) }) {
                    Text("${chip.label} · ${NumberFormat.getIntegerInstance().format(chip.count)}")
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(
                top = Dimens.RowFocusRoom,
                end = Dimens.OverscanHorizontal,
                bottom = Dimens.OverscanVertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            itemsIndexed(state.items, key = { _, it -> it.identifier }) { index, item ->
                // Ask for the next page a couple of rows before the end.
                if (index >= state.items.size - 8) {
                    LaunchedEffect(state.items.size) { viewModel.loadMore() }
                }
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    width = null,
                    cardModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                )
            }
            state.error?.let { message ->
                item(key = "error") { LabelCard(message, onClick = viewModel::retry, width = null) }
            }
        }
        if (state.total == 0) {
            Text(
                "Nothing here yet. Try another filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun browseViewModel(leafId: String): BrowseViewModel? {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val c = app.container
    val leaf = c.catalog.leaf(leafId) ?: return null
    return viewModel(key = "browse-$leafId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BrowseViewModel(
                leaf = leaf,
                facetGroup = leaf.facetGroup?.let { c.catalog.facetGroups[it] },
                render = c.queryBuilder::render,
                browse = c.archiveRepository::browse,
                count = c.archiveRepository::count,
            ) as T
    })
}
