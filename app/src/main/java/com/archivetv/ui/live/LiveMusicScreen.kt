package com.archivetv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import com.archivetv.ui.common.FailedRow
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.common.PlaceholderRow
import com.archivetv.ui.common.RowHeader
import com.archivetv.ui.common.rowPadding
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.theme.Dimens
import java.util.Calendar

/** Live Music discovery: This Day in History, Top Rated, Most Played, Popular Artists. */
@Composable
fun LiveMusicScreen(
    onOpenRecording: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onAllArtists: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val viewModel: LiveMusicViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val live = app.container.liveMusic
            val now = Calendar.getInstance()
            return LiveMusicViewModel(
                today = (now.get(Calendar.MONTH) + 1) to now.get(Calendar.DAY_OF_MONTH),
                thisDay = { m, d -> live.thisDay(m, d) },
                topRated = { live.topRated() },
                mostPlayed = { live.mostPlayed() },
                popularArtists = { live.popularArtists() },
            ) as T
        }
    })
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val allArtistsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { allArtistsFocus.requestFocus() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Dimens.OverscanVertical, bottom = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Row(
                Modifier.padding(start = Dimens.ContentStart, end = Dimens.OverscanHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("Live Music", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onAllArtists, modifier = Modifier.focusRequester(allArtistsFocus)) {
                    Text("All Artists")
                }
            }
        }
        rows.forEachIndexed { index, row ->
            item(key = "row-$index") {
                when (row) {
                    is LiveRow.Concerts -> when (val st = row.state) {
                        Load.Loading -> PlaceholderRow(row.title)
                        Load.Failed -> FailedRow(row.title) { viewModel.retry(index) }
                        is Load.Done -> if (st.value.isNotEmpty()) ConcertRow(row.title, st.value, onOpenRecording)
                    }
                    is LiveRow.Artists -> when (val st = row.state) {
                        Load.Loading -> PlaceholderRow(row.title)
                        Load.Failed -> FailedRow(row.title) { viewModel.retry(index) }
                        is Load.Done -> if (st.value.isNotEmpty()) ArtistRow(row.title, st.value, onOpenArtist)
                    }
                }
            }
        }
    }
}

@Composable
fun ConcertRow(title: String?, concerts: List<Concert>, onOpenRecording: (String) -> Unit) {
    Column {
        title?.let { RowHeader(it) }
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(concerts, key = { it.best.identifier }) { c ->
                val item = c.toCardItem()
                MediaCard(item = item, subtitle = item.year, onClick = { onOpenRecording(c.best.identifier) })
            }
        }
    }
}

@Composable
fun ArtistRow(title: String?, artists: List<Artist>, onOpenArtist: (String) -> Unit) {
    Column {
        title?.let { RowHeader(it) }
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(artists, key = { it.id }) { a -> LabelCard(a.name, onClick = { onOpenArtist(a.id) }) }
        }
    }
}
