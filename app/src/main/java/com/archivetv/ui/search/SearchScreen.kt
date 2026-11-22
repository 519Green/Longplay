package com.archivetv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.tv.material3.MaterialTheme
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.lazy.items as rowItems
import com.archivetv.data.PrefsRecentSearches
import com.archivetv.domain.searchScopes
import com.archivetv.ui.common.TileRow
import com.archivetv.ui.common.browseTiles
import java.text.NumberFormat
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.SearchScope
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.live.toCardItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.ui.focus.focusRestorer
import com.archivetv.ui.theme.Dimens

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    voicePending: Boolean = false,
    onVoiceStarted: () -> Unit = {},
    viewModel: SearchViewModel = searchViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val tiles = remember { browseTiles(app.container.catalog, onOpenGroup) }
    // Opening a result keeps its query in Recent searches, even one found by typing.
    val openItem: (ArchiveItem) -> Unit = { viewModel.rememberQuery(); onItemClick(it) }
    val openArtist: (String) -> Unit = { viewModel.rememberQuery(); onOpenArtist(it) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val voiceFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    // The selected chip: a stable place for focus when a tapped recent search
    // leaves the screen (the results, if any, then take it).
    val scopeFocus = remember { FocusRequester() }
    // Bumped on each submit; when results for it arrive, focus jumps to them.
    var submits by remember { mutableIntStateOf(0) }
    var focusedSubmit by remember { mutableIntStateOf(0) }
    fun submit() {
        keyboard?.hide()
        submits++
        viewModel.submit()
    }
    // Waits for the search to finish: while loading, the old results are
    // still on screen and focusing them would lose focus when they go.
    LaunchedEffect(state.watch, state.listen, state.liveArtists, state.liveConcerts, state.loading, submits) {
        val any = state.watch.isNotEmpty() || state.listen.isNotEmpty() ||
            state.liveArtists.isNotEmpty() || state.liveConcerts.isNotEmpty()
        if (submits > focusedSubmit && any && !state.loading) {
            focusedSubmit = submits
            // The first card may attach a frame or two after the results arrive
            // (and a tapped recent search has just left the screen with focus).
            repeat(10) {
                if (runCatching { firstResultFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                kotlinx.coroutines.delay(50)
            }
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { submits++; viewModel.search(it) }
        }
    }
    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a film or show title")
        }
        runCatching { voiceLauncher.launch(intent) }
    }
    // The remote's search/mic key sets voicePending; consume it exactly once.
    LaunchedEffect(voicePending) {
        if (voicePending) {
            onVoiceStarted()
            // Move focus off the drawer first so it collapses; otherwise the
            // system voice panel draws over an expanded drawer.
            runCatching { voiceFocus.requestFocus() }
            startVoiceSearch()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Focus alone must not open the on-screen keyboard: arriving on Search
        // would cover the screen with it. The box around the field takes focus;
        // OK on it (on key-up, so the IME never sees the release) starts editing.
        var editing by remember { mutableStateOf(false) }
        var boxFocused by remember { mutableStateOf(false) }
        val fieldFocus = remember { FocusRequester() }
        LaunchedEffect(editing) {
            if (editing) {
                repeat(10) {
                    if (runCatching { fieldFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                    kotlinx.coroutines.delay(30)
                }
                editing = false
            }
        }
        Box(
            Modifier
                .onFocusChanged { boxFocused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (editing || e.key !in okKeys) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyUp) editing = true
                    true
                }
                .border(
                    2.dp,
                    if (boxFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .focusable(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { androidx.compose.material3.Text("Search films, TV, books, radio and music") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                // A focused text field keeps D-pad keys for its cursor, which traps
                // remote users in it. Up/Down always leave the field.
                modifier = Modifier
                    // Wide enough for the label, which otherwise runs past the outline.
                    .width(400.dp)
                    .focusRequester(fieldFocus)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged {
                        if (!it.isFocused && editing) {
                            editing = false
                            keyboard?.hide()
                        }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { submit() }) { Text("Search") }
            Button(onClick = { startVoiceSearch() }, modifier = Modifier.focusRequester(voiceFocus)) {
                Text("Voice Search")
            }
        }
        // No focusRestorer here: every chip is on screen, and a restorer with
        // nothing saved made focus search skip the whole row.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = Dimens.OverscanHorizontal),
        ) {
            rowItems(state.scopes, key = { "scope-" + it.id }) { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { viewModel.setScope(scope) },
                    modifier = if (state.scope == scope) Modifier.focusRequester(scopeFocus) else Modifier,
                ) {
                    Text(scope.label)
                }
            }
        }
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            state.loading -> Text("Searching…", color = muted)
            state.error != null -> Text(state.error!!)
            state.noResults -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No results for \u201C${state.searched}\u201D.", style = MaterialTheme.typography.titleMedium)
                if (state.scope != SearchScope.Everything) {
                    Text("Try fewer words, or search everything on Archive.org.", color = muted)
                    Button(onClick = { viewModel.setScope(SearchScope.Everything) }) {
                        Text("Search everything on Archive.org")
                    }
                } else {
                    Text("Try fewer or different words.", color = muted)
                }
            }
        }
        if (state.query.isBlank() && !state.hasResults) {
            // Before anything is typed: recent searches and the Browse tiles, so the screen is not empty.
            if (state.recent.isNotEmpty()) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = Dimens.OverscanHorizontal),
                ) {
                    rowItems(state.recent, key = { "recent-$it" }) { q ->
                        Button(onClick = {
                            runCatching { scopeFocus.requestFocus() }
                            submits++
                            viewModel.search(q)
                        }) { Text(q, maxLines = 1) }
                    }
                }
            }
            if (tiles.isNotEmpty()) {
                // The screen column already applies ContentStart.
                Box(Modifier.padding(top = 8.dp)) { TileRow("Browse", tiles, start = 0.dp) }
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, bottom = Dimens.OverscanVertical),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            // First result overall gets focus after a search: Watch's first, else Listen's.
            if (state.watch.isNotEmpty()) {
                item(key = "watch-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(sectionHeader("Watch", state.watch.size, state.watchTotal), style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(state.watch, key = { _, it -> "w-" + it.identifier }) { index, item ->
                    MediaCard(
                        item = item,
                        onClick = { openItem(item) },
                        width = null,
                        cardModifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                    )
                }
            }
            if (state.listen.isNotEmpty()) {
                item(key = "listen-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(sectionHeader("Listen", state.listen.size, state.listenTotal), style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(state.listen, key = { _, it -> "l-" + it.identifier }) { index, item ->
                    MediaCard(
                        item = item,
                        onClick = { openItem(item) },
                        width = null,
                        cardModifier = if (index == 0 && state.watch.isEmpty()) {
                            Modifier.focusRequester(firstResultFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            if (state.liveArtists.isNotEmpty() || state.liveConcerts.isNotEmpty()) {
                item(key = "live-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Live Music", style = MaterialTheme.typography.titleMedium)
                }
                val liveFirst = state.watch.isEmpty() && state.listen.isEmpty()
                if (state.liveArtists.isNotEmpty()) {
                    item(key = "live-artists", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
                        ) {
                            rowItemsIndexed(state.liveArtists, key = { _, a -> a.id }) { index, artist ->
                                LabelCard(
                                    artist.name,
                                    onClick = { openArtist(artist.id) },
                                    modifier = if (index == 0 && liveFirst) Modifier.focusRequester(firstResultFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(state.liveConcerts, key = { _, c -> "c-" + c.best.identifier }) { index, c ->
                    val card = c.toCardItem()
                    MediaCard(
                        item = card,
                        subtitle = card.year,
                        onClick = { openItem(card) },
                        width = null,
                        cardModifier = if (index == 0 && liveFirst && state.liveArtists.isEmpty()) {
                            Modifier.focusRequester(firstResultFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

private val okKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** "Watch · 1,234" when every match is shown, else "Watch · top 40 of 1,234". */
internal fun sectionHeader(label: String, shown: Int, total: Int): String {
    val f = NumberFormat.getIntegerInstance()
    return if (total <= shown) "$label · ${f.format(shown)}" else "$label · top ${f.format(shown)} of ${f.format(total)}"
}

@Composable
fun searchViewModel(): SearchViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(
                searchVideo = { scope, q -> app.container.archiveRepository.searchScoped(scope, q, video = true) },
                searchAudio = { scope, q -> app.container.archiveRepository.searchScoped(scope, q, video = false) },
                searchLiveArtists = { app.container.liveMusic.searchArtists(it) },
                searchLiveConcerts = { app.container.liveMusic.searchConcerts(it) },
                scopes = searchScopes(app.container.catalog),
                recentStore = PrefsRecentSearches(app),
            ) as T
    })
}
