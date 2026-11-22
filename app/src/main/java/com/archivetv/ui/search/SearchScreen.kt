package com.archivetv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.live.toCardItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.ui.focus.focusRestorer
import com.archivetv.ui.theme.Dimens

@Composable
fun SearchScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onOpenArtist: (String) -> Unit = {},
    voicePending: Boolean = false,
    onVoiceStarted: () -> Unit = {},
    viewModel: SearchViewModel = searchViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val voiceFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    // Bumped on each submit; when results for it arrive, focus jumps to them.
    var submits by remember { mutableIntStateOf(0) }
    var focusedSubmit by remember { mutableIntStateOf(0) }
    fun submit() {
        keyboard?.hide()
        submits++
        viewModel.submit()
    }
    LaunchedEffect(state.watch, state.listen, state.liveArtists, state.liveConcerts, submits) {
        val any = state.watch.isNotEmpty() || state.listen.isNotEmpty() ||
            state.liveArtists.isNotEmpty() || state.liveConcerts.isNotEmpty()
        if (submits > focusedSubmit && any) {
            focusedSubmit = submits
            runCatching { firstResultFocus.requestFocus() }
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
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { androidx.compose.material3.Text("Search films, TV, books, radio and music") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            // A focused text field keeps D-pad keys for its cursor, which traps
            // remote users in it. Up/Down always leave the field.
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { submit() }) { Text("Search") }
            Button(onClick = { startVoiceSearch() }, modifier = Modifier.focusRequester(voiceFocus)) {
                Text("Voice Search")
            }
        }
        state.error?.let { Text(it) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, bottom = Dimens.OverscanVertical),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            // First result overall gets focus after a search: Watch's first, else Listen's.
            if (state.watch.isNotEmpty()) {
                item(key = "watch-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Watch", style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(state.watch, key = { _, it -> "w-" + it.identifier }) { index, item ->
                    MediaCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        width = null,
                        cardModifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                    )
                }
            }
            if (state.listen.isNotEmpty()) {
                item(key = "listen-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Listen", style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(state.listen, key = { _, it -> "l-" + it.identifier }) { index, item ->
                    MediaCard(
                        item = item,
                        onClick = { onItemClick(item) },
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
                                    onClick = { onOpenArtist(artist.id) },
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
                        onClick = { onItemClick(card) },
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

@Composable
fun searchViewModel(): SearchViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(
                app.container.archiveRepository::search,
                app.container.archiveRepository::searchAudio,
                searchLiveArtists = { app.container.liveMusic.searchArtists(it) },
                searchLiveConcerts = { app.container.liveMusic.searchConcerts(it) },
            ) as T
    })
}
