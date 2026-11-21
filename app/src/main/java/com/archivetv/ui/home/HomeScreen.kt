package com.archivetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.archivetv.data.BrowseSort
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.common.CardRow
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.TileRow
import com.archivetv.ui.common.contentRows
import com.archivetv.ui.theme.Dimens

/** Category groups surfaced as tiles on Home (Jellyfin-style "My Media" row). */
private val homeTiles = listOf(
    "movie-genres" to "Movie Genres",
    "movie-decades" to "Movies by Decade",
    "tv-decades" to "TV by Decade",
    "tv-genres" to "TV Genres",
    "tv-shows" to "TV Shows",
    "animation-studios" to "Animation Studios",
    "edu-studios" to "Educational Studios",
)

@Composable
fun HomeScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenLeaf: (String) -> Unit,
    viewModel: HomeViewModel = homeViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val tiles = remember {
        homeTiles.filter { app.container.catalog.group(it.first) != null }
            .map { (id, label) -> label to { onOpenGroup(id) } }
    }
    HomeContent(
        continueWatching = state.continueWatching,
        favorites = state.favorites,
        rows = state.rows,
        hero = state.hero,
        browseTiles = tiles,
        onItemClick = onItemClick,
        onRetry = viewModel::retry,
        onSeeAll = { onOpenLeaf(it.config.id) },
    )
}

@Composable
fun HomeContent(
    continueWatching: List<ArchiveItem>,
    favorites: List<ArchiveItem>,
    rows: List<ContentRow>,
    hero: ArchiveItem?,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: (Int) -> Unit,
    browseTiles: List<Pair<String, () -> Unit>> = emptyList(),
    onSeeAll: ((ContentRow) -> Unit)? = null,
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
        if (browseTiles.isNotEmpty()) {
            item(key = "browse") { TileRow("Browse", browseTiles) }
        }
        contentRows(rows, onItemClick, onRetry, onSeeAll)
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
fun homeViewModel(): HomeViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val archive = app.container.archiveRepository
            val library = app.container.libraryRepository
            return HomeViewModel(
                configuredRows = app.container.homeRows,
                loadRow = { archive.browse(it.query, BrowseSort.POPULAR, page = 1, rows = 20).items },
                lookupItems = archive::itemsByIds,
                continueWatching = library.observeContinueWatching(),
                favorites = library.observeFavorites(),
            ) as T
        }
    })
}
