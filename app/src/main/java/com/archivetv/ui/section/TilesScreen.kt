package com.archivetv.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.CategoryNode
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.live.Load
import com.archivetv.ui.theme.Dimens

private data class Tile(val key: String, val label: String, val onClick: () -> Unit)

/**
 * "Browse by" grid: one tile per child of a group (genres, decades...), or per
 * entry of a derived group (shows, studios, sponsors, radio), built live.
 */
@Composable
fun TilesScreen(
    groupId: String,
    onOpenGroup: (String) -> Unit,
    onOpenLeaf: (String) -> Unit,
    onOpenEntry: (groupId: String, key: String) -> Unit = { _, _ -> },
) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val catalog = app.container.catalog
    val parent = remember(groupId) {
        catalog.sections.firstOrNull { s -> s.children.any { it.id == groupId } }
    }
    catalog.derived(groupId)?.let { node ->
        DerivedTiles(node, title = listOfNotNull(parent?.title, node.title).joinToString(" · "), onOpenEntry)
        return
    }
    val group = catalog.group(groupId) ?: run {
        Text("Unknown category")
        return
    }
    val tiles = remember(group) {
        group.children.map { node ->
            Tile(node.id, node.title) {
                when (node) {
                    is CategoryNode.Group, is CategoryNode.Derived -> onOpenGroup(node.id)
                    is CategoryNode.Leaf -> onOpenLeaf(node.id)
                }
            }
        }
    }
    TilesPage(listOfNotNull(parent?.title, group.title).joinToString(" · ")) { TileGrid(tiles) }
}

@Composable
private fun DerivedTiles(node: CategoryNode.Derived, title: String, onOpenEntry: (String, String) -> Unit) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val viewModel: DerivedTilesViewModel = viewModel(key = "derived-${node.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DerivedTilesViewModel(load = { app.container.derivedGroups.entries(node) }) as T
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    TilesPage(title) {
        when (val st = state) {
            Load.Loading -> Text("Loading…")
            Load.Failed -> {
                val retryFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
                Button(onClick = viewModel::retry, modifier = Modifier.focusRequester(retryFocus)) {
                    Text("Couldn't load. Retry")
                }
            }
            is Load.Done -> if (st.value.isEmpty()) {
                Text("Nothing found")
            } else {
                TileGrid(st.value.map { e -> Tile(e.key, e.label) { onOpenEntry(node.id, e.key) } })
            }
        }
    }
}

@Composable
private fun TilesPage(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.OverscanHorizontal, top = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>) {
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialFocusDone) {
            runCatching { firstFocus.requestFocus() }
            initialFocusDone = true
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
        itemsIndexed(tiles, key = { _, t -> t.key }) { index, tile ->
            LabelCard(
                label = tile.label,
                width = null,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = tile.onClick,
            )
        }
    }
}
