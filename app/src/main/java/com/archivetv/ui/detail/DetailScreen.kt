package com.archivetv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.TopCenter),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to Color.Transparent,
                            1f to Color.Black,
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
                Text(item.title, style = MaterialTheme.typography.headlineLarge)
                item.year?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                item.description?.let {
                    androidx.compose.material3.Text(
                        it,
                        maxLines = 4,
                        color = Color.White,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPlay(identifier) }, enabled = state.playable) {
                        Text(if (state.playable) "Play" else "Unavailable")
                    }
                    Button(onClick = viewModel::toggleFavorite) {
                        Text(if (state.isFavorite) "Remove from My List" else "Add to My List")
                    }
                }
            }
        }
        state.error?.let {
            Text(it, modifier = Modifier.align(Alignment.Center))
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
