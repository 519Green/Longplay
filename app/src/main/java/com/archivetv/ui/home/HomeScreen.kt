package com.archivetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.components.PlaceholderCard
import com.archivetv.ui.theme.Dimens

@Composable
fun HomeScreen(
    onItemClick: (ArchiveItem) -> Unit,
    viewModel: HomeViewModel = homeViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        continueWatching = state.continueWatching,
        favorites = state.favorites,
        rows = state.rows,
        hero = state.hero,
        onItemClick = onItemClick,
        onRetry = viewModel::retry,
    )
}

@Composable
fun HomeContent(
    continueWatching: List<ArchiveItem>,
    favorites: List<ArchiveItem>,
    rows: List<HomeRow>,
    hero: ArchiveItem?,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: (Int) -> Unit,
) {
    val heroFocus = remember { FocusRequester() }
    // Focus content (not the drawer) once on first arrival; after that, focus
    // restoration on return from Detail must win, so the flag is saved.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hero != null) {
        if (hero != null && !initialFocusDone) {
            runCatching { heroFocus.requestFocus() }
            initialFocusDone = true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Dimens.OverscanVertical, bottom = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hero?.let { h ->
            item(key = "hero") {
                Hero(
                    item = h,
                    onClick = { onItemClick(h) },
                    modifier = Modifier
                        .padding(start = Dimens.ContentStart, end = Dimens.OverscanHorizontal)
                        .focusRequester(heroFocus),
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") { CardRow("Continue Watching", continueWatching, onItemClick) }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favorites") { CardRow("My List", favorites, onItemClick) }
        }
        itemsIndexed(rows, key = { _, row -> row.config.collectionId }) { index, row ->
            when (val st = row.state) {
                RowState.Loading -> PlaceholderRow(row.config.title)
                RowState.Failed -> FailedRow(row.config.title, onRetry = { onRetry(index) })
                is RowState.Loaded ->
                    if (st.items.isNotEmpty()) CardRow(row.config.title, st.items, onItemClick)
            }
        }
    }
}

@Composable
private fun Hero(item: ArchiveItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(220.dp),
        scale = CardDefaults.scale(focusedScale = 1.02f),
    ) {
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
private fun RowHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = Dimens.ContentStart, top = 12.dp),
    )
}

private val rowPadding = PaddingValues(
    start = Dimens.ContentStart,
    end = Dimens.OverscanHorizontal,
    top = Dimens.RowFocusRoom,
    bottom = Dimens.RowFocusRoom,
)

@Composable
private fun CardRow(title: String, items: List<ArchiveItem>, onClick: (ArchiveItem) -> Unit) {
    Column {
        RowHeader(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding,
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(items, key = { it.identifier }) { item ->
                MediaCard(item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun PlaceholderRow(title: String) {
    Column {
        RowHeader(title)
        LazyRow(
            contentPadding = rowPadding,
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            userScrollEnabled = false,
        ) {
            items(5) { PlaceholderCard() }
        }
    }
}

@Composable
private fun FailedRow(title: String, onRetry: () -> Unit) {
    Column {
        RowHeader(title)
        LazyRow(contentPadding = rowPadding) {
            item {
                Card(
                    onClick = onRetry,
                    modifier = Modifier.width(Dimens.CardWidth).aspectRatio(Dimens.CardImageAspect),
                    colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Couldn't load this row", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Select to retry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun homeViewModel(): HomeViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val archive = app.container.archiveRepository
            val library = app.container.libraryRepository
            return HomeViewModel(
                configuredRows = app.container.browseRows,
                loadRow = { archive.browseCollection(it.collectionId, limit = 20) },
                lookupItems = archive::itemsByIds,
                continueWatching = library.observeContinueWatching(),
                favorites = library.observeFavorites(),
            ) as T
        }
    })
}
