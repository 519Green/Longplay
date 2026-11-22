package com.archivetv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.YearCount
import com.archivetv.ui.theme.Dimens

fun recordingsLabel(n: Int) = if (n == 1) "1 recording" else "$n recordings"

/** One artist: year chips with concert counts, then that year's concerts. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistScreen(artistId: String, onOpenRecording: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val viewModel: ArtistViewModel = viewModel(key = "artist-$artistId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val live = app.container.liveMusic
            return ArtistViewModel(
                artistId = artistId,
                startYear = { live.startYear(artistId) },
                years = { live.years(artistId) },
                concerts = { year -> live.concerts(artistId, year) },
            ) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chips: List<YearCount?> = state.years
        ?: listOfNotNull(state.selectedYear?.let { YearCount(it, -1) })
    val chipState = rememberLazyListState()
    val selectedIndex = chips.indexOfFirst { it?.year == state.selectedYear }
    LaunchedEffect(state.years != null) {
        if (selectedIndex > 0) chipState.scrollToItem(selectedIndex)
    }
    val firstConcert = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    val concerts = (state.concerts as? Load.Done)?.value
    LaunchedEffect(concerts) {
        if (!focused && !concerts.isNullOrEmpty()) {
            focused = runCatching { firstConcert.requestFocus() }.isSuccess
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.name ?: "", style = MaterialTheme.typography.headlineSmall)
        LazyRow(
            state = chipState,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { it?.year ?: 0 }) { yc ->
                if (yc != null) {
                    FilterChip(selected = yc.year == state.selectedYear, onClick = { viewModel.selectYear(yc.year) }) {
                        Text(if (yc.concerts >= 0) "${yc.year} · ${yc.concerts}" else "${yc.year}")
                    }
                }
            }
            if (state.yearsFailed) {
                item(key = "years-retry") {
                    FilterChip(selected = false, onClick = viewModel::retryYears) { Text("Years unavailable · Retry") }
                }
            }
        }
        when (val st = state.concerts) {
            Load.Loading -> Text("Loading concerts…")
            Load.Failed -> Button(onClick = viewModel::retry) { Text("Couldn't load concerts. Retry") }
            is Load.Done -> if (st.value.isEmpty()) {
                Text("No concerts found")
            } else {
                LazyColumn(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(end = Dimens.OverscanHorizontal, bottom = Dimens.OverscanVertical),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(st.value, key = { _, c -> c.best.identifier }) { index, c ->
                        ListItem(
                            selected = false,
                            onClick = { onOpenRecording(c.best.identifier) },
                            headlineContent = { Text(concertSubtitle(c, withCity = true)) },
                            trailingContent = { Text(recordingsLabel(c.recordingCount)) },
                            modifier = if (index == 0) Modifier.focusRequester(firstConcert) else Modifier,
                        )
                    }
                }
            }
        }
    }
}
