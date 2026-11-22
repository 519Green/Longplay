package com.archivetv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.theme.Dimens

/** Every Live Music Archive artist: type to filter, or jump by letter. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistsScreen(onOpenArtist: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val viewModel: ArtistsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArtistsViewModel(load = { app.container.liveMusic.artists() }) as T
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val jumpFocus = remember { FocusRequester() }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(jumpTarget) {
        val target = jumpTarget ?: return@LaunchedEffect
        gridState.scrollToItem(target)
        withFrameNanos { }
        runCatching { jumpFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("All Artists", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { androidx.compose.material3.Text("Find an artist") },
            singleLine = true,
            // Up/Down always leave the field, as on Search, so remote users are not trapped.
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
        )
        when {
            state.loading -> Text("Loading artists…")
            state.failed -> Button(onClick = viewModel::retry) { Text("Couldn't load artists. Retry") }
            state.shown.isEmpty() -> Text("No artists found")
            else -> {
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.letters, key = { it }) { letter ->
                        FilterChip(selected = false, onClick = {
                            val i = viewModel.indexOf(letter)
                            if (i >= 0) jumpTarget = i
                        }) { Text(letter.toString()) }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, bottom = Dimens.OverscanVertical),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
                ) {
                    itemsIndexed(state.shown, key = { _, a -> a.id }) { index, artist ->
                        LabelCard(
                            artist.name,
                            onClick = { onOpenArtist(artist.id) },
                            width = null,
                            modifier = (if (index == jumpTarget) Modifier.focusRequester(jumpFocus) else Modifier)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
