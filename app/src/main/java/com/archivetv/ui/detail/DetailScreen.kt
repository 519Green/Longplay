package com.archivetv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp

@Composable
fun DetailScreen(
    identifier: String,
    onPlay: (String) -> Unit,
    viewModel: DetailViewModel = detailViewModel(),
) {
    LaunchedEffect(identifier) { viewModel.load(identifier) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    Box(Modifier.fillMaxSize()) {
        if (item != null) {
            val background = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .align(Alignment.TopCenter),
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Fade the backdrop into the page so there is no hard bottom edge.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to background.copy(alpha = 0.35f),
                                1f to background,
                            )
                        )
                )
            }
            // Darken the left side behind the text block.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to background.copy(alpha = 0.85f),
                            0.6f to Color.Transparent,
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.65f)
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Land on the primary action once the item loads (Play, or My List
                // when nothing is playable); saved so returning from the player
                // does not steal focus back.
                val playFocus = remember { FocusRequester() }
                val listFocus = remember { FocusRequester() }
                var initialFocusDone by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!initialFocusDone) {
                        runCatching { (if (state.playable) playFocus else listFocus).requestFocus() }
                        initialFocusDone = true
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onPlay(identifier) },
                        enabled = state.playable,
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
                        Text(if (state.playable) "Play" else "Unavailable")
                    }
                    Button(onClick = viewModel::toggleFavorite, modifier = Modifier.focusRequester(listFocus)) {
                        Text(if (state.isFavorite) "Remove from My List" else "Add to My List")
                    }
                }
            }
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
fun detailViewModel(): DetailViewModel {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(
                app.container.archiveRepository,
                app.container.libraryRepository,
            ) as T
    })
}
