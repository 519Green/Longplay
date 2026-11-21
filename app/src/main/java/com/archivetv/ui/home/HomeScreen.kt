package com.archivetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.components.ArchiveCard
import com.archivetv.domain.BrowseRow

@Composable
fun HomeScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = homeViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        continueWatching = state.continueWatching,
        favorites = state.favorites,
        rows = state.rows,
        hero = state.hero,
        onItemClick = onItemClick,
        onSearch = onSearch,
    )
}

@Composable
fun HomeContent(
    continueWatching: List<ArchiveItem>,
    favorites: List<ArchiveItem>,
    rows: List<BrowseRow>,
    hero: ArchiveItem?,
    onItemClick: (ArchiveItem) -> Unit,
    onSearch: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(onSearch = onSearch)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            hero?.let { h ->
                item { Hero(h, onClick = { onItemClick(h) }) }
            }
            if (continueWatching.isNotEmpty()) {
                item { CardRow("Continue Watching", continueWatching, onItemClick) }
            }
            if (favorites.isNotEmpty()) {
                item { CardRow("My List", favorites, onItemClick) }
            }
            items(rows) { row ->
                if (row.items.isNotEmpty()) CardRow(row.title, row.items, onItemClick)
            }
        }
    }
}

@Composable
private fun Sidebar(onSearch: () -> Unit) {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { searchFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(140.dp)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = onSearch, modifier = Modifier.focusRequester(searchFocus)) { Text("Search") }
    }
}

@Composable
private fun Hero(item: ArchiveItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        )
                    )
            )
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }
    }
}

@Composable
private fun CardRow(title: String, items: List<ArchiveItem>, onClick: (ArchiveItem) -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items) { index, item ->
                ArchiveCard(
                    item = item,
                    onClick = { onClick(item) },
                    cardModifier = Modifier.focusProperties {
                        if (index == 0) left = FocusRequester.Cancel
                    },
                )
            }
        }
    }
}

@Composable
fun homeViewModel(): HomeViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(
                app.container.archiveRepository,
                app.container.libraryRepository,
                app.container.browseRows,
            ) as T
    })
}
