package com.archivetv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.tv.material3.Button
import com.archivetv.domain.ItemKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.archivetv.ui.common.RowHeader
import com.archivetv.ui.common.rowPadding
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.TileRow
import com.archivetv.ui.common.browseTiles
import com.archivetv.ui.common.contentRows
import com.archivetv.ui.theme.Dimens

@Composable
fun HomeScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onPlayFile: (String, Int) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenLeaf: (String) -> Unit,
    onListen: (String, Int) -> Unit = onPlayFile,
    /** Plays a hero item from where the viewer left off (audio goes to the background player). */
    onPlay: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = homeViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The date may have moved on while the app sat in the background (TVs keep apps alive for days).
    androidx.lifecycle.compose.LifecycleStartEffect(viewModel) {
        viewModel.refreshSeasons()
        onStopOrDispose { }
    }
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val tiles = remember { browseTiles(app.container.catalog, onOpenGroup) }
    val surpriseScope = androidx.compose.runtime.rememberCoroutineScope()
    val surpriseTiles = remember {
        val doors = com.archivetv.ui.common.surpriseDoors(app.container.catalog)
        var busy = false
        fun open(leaf: () -> com.archivetv.domain.CategoryNode.Leaf) {
            if (busy) return
            busy = true
            surpriseScope.launch {
                // A failed or empty pick leaves the viewer on Home; pressing again tries again.
                runCatching { app.container.surprise.pick(leaf()) }.getOrNull()?.let(onItemClick)
                busy = false
            }
        }
        if (doors.isEmpty()) emptyList()
        else listOf("Anything" to { open { doors.random().second } }) +
            doors.map { (label, leaf) -> label to { open { leaf } } }
    }
    HomeContent(
        continueWatching = state.continueWatching,
        favorites = state.favorites,
        rows = state.rows,
        heroSlides = state.heroSlides,
        onPlayHero = { onPlay(it.item.identifier, it.isAudio) },
        nextUp = state.nextUp,
        onPlayFile = onPlayFile,
        continueListening = state.continueListening,
        seasonal = state.seasonal,
        onListen = onListen,
        browseTiles = tiles,
        surpriseTiles = surpriseTiles,
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
    heroSlides: List<HeroSlide>,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: (Int) -> Unit,
    browseTiles: List<Pair<String, () -> Unit>> = emptyList(),
    surpriseTiles: List<Pair<String, () -> Unit>> = emptyList(),
    onSeeAll: ((ContentRow) -> Unit)? = null,
    nextUp: List<NextUpCard> = emptyList(),
    onPlayFile: (String, Int) -> Unit = { _, _ -> },
    continueListening: List<NextUpCard> = emptyList(),
    onListen: (String, Int) -> Unit = { _, _ -> },
    onPlayHero: (HeroSlide) -> Unit = {},
    seasonal: List<ContentRow> = emptyList(),
) {
    val heroFocus = remember { FocusRequester() }
    // Focus content (not the drawer) once on first arrival; after that, focus
    // restoration on return from Detail must win, so the flag is saved.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val hasHero = heroSlides.isNotEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(hasHero) {
        if (hasHero && !initialFocusDone) {
            // Slides arrive after their playability checks, by which time focus
            // may have scrolled the list past the top; bring the hero back into
            // composition, then focus it once it has attached.
            listState.scrollToItem(0)
            repeat(20) {
                if (runCatching { heroFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    return@LaunchedEffect
                }
                delay(50)
            }
            initialFocusDone = true
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Slides arrive a request after the first row. Once the viewer has
            // pressed a key here (moved, or opened something), they must not be
            // pulled back to the hero.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) initialFocusDone = true
                false
            },
        contentPadding = PaddingValues(top = Dimens.OverscanVertical, bottom = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasHero) {
            item(key = "hero") {
                HeroCarousel(
                    slides = heroSlides,
                    onPlay = onPlayHero,
                    onDetails = { onItemClick(it.item) },
                    focusRequester = heroFocus,
                    modifier = Modifier.padding(start = Dimens.ContentStart, end = Dimens.OverscanHorizontal),
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") { CardRow("Continue Watching", continueWatching, onItemClick) }
        }
        if (continueListening.isNotEmpty()) {
            item(key = "continue-listening") { NextUpRow("Continue Listening", continueListening, onListen) }
        }
        if (nextUp.isNotEmpty()) {
            item(key = "next-up") { NextUpRow("Next Up", nextUp, onPlayFile) }
        }
        // Seasonal rows never fail visibly or retry (contentRows hides them until they have enough items).
        contentRows(seasonal, onItemClick, onRetry = {}, keyPrefix = "season-")
        if (favorites.isNotEmpty()) {
            item(key = "favorites") { CardRow("My List", favorites, onItemClick) }
        }
        if (browseTiles.isNotEmpty()) {
            item(key = "browse") { TileRow("Browse", browseTiles) }
        }
        if (surpriseTiles.isNotEmpty()) {
            item(key = "surprise") { TileRow("Surprise me", surpriseTiles) }
        }
        contentRows(rows, onItemClick, onRetry, onSeeAll)
    }
}

@Composable
private fun NextUpRow(title: String, cards: List<NextUpCard>, onPlayFile: (String, Int) -> Unit) {
    Column {
        RowHeader(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(cards, key = { it.item.identifier }) { card ->
                MediaCard(
                    item = card.item,
                    subtitle = card.label.ifEmpty { null },
                    onClick = { onPlayFile(card.item.identifier, card.fileIndex) },
                    focusKey = "$title/${card.item.identifier}",
                )
            }
        }
    }
}

/**
 * Card-style hero (research spec 2.4): text and actions at left, the small IA
 * thumbnail at its own size at right. tv-material 1.0.0's Carousel drops focus
 * to the drawer when a slide changes under a focused button, so this keeps one
 * pair of buttons and swaps only the text and image. Left on Play goes back a
 * slide (or on to the drawer from the first), Right on Details goes forward,
 * and slides advance every 8 s while the hero is not focused.
 */
@Composable
private fun HeroCarousel(
    slides: List<HeroSlide>,
    onPlay: (HeroSlide) -> Unit,
    onDetails: (HeroSlide) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = slides[index.coerceIn(0, slides.lastIndex)]
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(slides.size) { if (index > slides.lastIndex) index = 0 }
    LaunchedEffect(focused, index, slides.size) {
        if (!focused && slides.size > 1) {
            delay(8_000)
            index = (index + 1) % slides.size
        }
    }
    fun Modifier.onKey(key: Key, action: () -> Boolean) = onPreviewKeyEvent { e ->
        e.type == KeyEventType.KeyDown && e.key == key && action()
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .onFocusChanged { focused = it.hasFocus }
            .wholeItemIntoView(),
    ) {
        Row(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Fixed height so the buttons do not move between slides.
                Crossfade(current, label = "hero-text", modifier = Modifier.height(150.dp)) { slide ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        Text(
                            slide.item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        slide.year?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        slide.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onPlay(current) },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onKey(Key.DirectionLeft) { (index > 0).also { if (it) index-- } },
                    ) { Text("Play") }
                    Button(
                        onClick = { onDetails(current) },
                        modifier = Modifier.onKey(Key.DirectionRight) {
                            (slides.size > 1).also { if (it) index = (index + 1) % slides.size }
                        },
                    ) { Text("Details") }
                }
            }
            Crossfade(current.item.thumbnailUrl, label = "hero-image") { url ->
                AsyncImage(
                    model = url,
                    contentDescription = current.item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(300.dp)
                        .aspectRatio(1.45f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        if (slides.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slides.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == index) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Makes bring-into-view requests from children (a focused button) ask for this
 * whole element. The TV scroll default moves a focused child towards 30 % of
 * the screen, which would push the top of the hero under the mini bar.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.wholeItemIntoView(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size.toSize())
            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    onSizeChanged { size = it }.bringIntoViewResponder(responder)
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
                hiddenIds = app.container.savedFilter::hidden,
                seasonalRows = { app.container.seasonalRows() },
                continueWatching = library.observeContinueWatching(),
                favorites = library.observeFavorites(),
                nextUp = library.observeNextUp(),
                continueListening = library.observeContinueListening(),
                heroCheck = { item ->
                    val d = archive.detail(item.identifier)
                    if (d.files.isEmpty()) null
                    else HeroSlide(
                        item = item.copy(title = d.item.title),
                        year = d.facts.year,
                        description = d.item.description,
                        isAudio = d.kind != ItemKind.VIDEO,
                    )
                },
            ) as T
        }
    })
}
