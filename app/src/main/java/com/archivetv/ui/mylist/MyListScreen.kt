package com.archivetv.ui.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.theme.Dimens

@Composable
fun MyListScreen(onItemClick: (ArchiveItem) -> Unit) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val flow = remember { app.container.libraryRepository.observeFavorites() }
    val saved by flow.collectAsStateWithLifecycle(initialValue = null)
    // The parental filter hides flagged titles here too.
    val favorites by produceState<List<ArchiveItem>?>(null, saved) {
        val list = saved
        value = list?.let { items ->
            val hidden = app.container.savedFilter.hidden(items.map { it.identifier })
            items.filterNot { it.identifier in hidden }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical),
    ) {
        Text("My List", style = MaterialTheme.typography.headlineSmall)
        val items = favorites
        if (items != null && items.isEmpty()) {
            Text(
                "Nothing saved yet. Use \"Add to My List\" on any film or show.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(
                top = 16.dp,
                end = Dimens.OverscanHorizontal,
                bottom = Dimens.OverscanVertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(items.orEmpty(), key = { it.identifier }) { item ->
                MediaCard(item = item, onClick = { onItemClick(item) }, width = null)
            }
        }
    }
}
