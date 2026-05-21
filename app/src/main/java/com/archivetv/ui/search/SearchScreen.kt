package com.archivetv.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem

@Composable
fun SearchScreen(
    onItemClick: (ArchiveItem) -> Unit,
    viewModel: SearchViewModel = searchViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { androidx.compose.material3.Text("Search films & TV") },
        )
        Button(onClick = viewModel::submit) { Text("Search") }
        state.error?.let { Text(it) }
        LazyVerticalGrid(columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.results) { item ->
                Card(onClick = { onItemClick(item) },
                    modifier = Modifier.width(160.dp).height(90.dp)) {
                    AsyncImage(model = item.thumbnailUrl,
                        contentDescription = item.title, modifier = Modifier.fillMaxSize())
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
            SearchViewModel(app.container.archiveRepository) as T
    })
}
