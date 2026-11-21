package com.archivetv.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp
import com.archivetv.playback.QueueOps
import com.archivetv.ui.theme.Dimens

/** Full-screen view of the audio queue. Back leaves it; playback continues. */
@Composable
fun NowPlayingScreen() {
    val audio = (LocalContext.current.applicationContext as ArchiveTvApp).container.audio
    val state by audio.state.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val np = state
    if (np == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing is playing", style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    val current = np.current
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.OverscanHorizontal, vertical = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            AsyncImage(
                model = current?.let { "https://archive.org/services/img/${it.identifier}" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    current?.itemTitle.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    current?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val fraction = if (np.durationMs > 0) (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth(0.8f).height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
                Text(
                    "${clock(np.positionMs)} / ${clock(np.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = audio::previous) { Text("Previous") }
                    Button(onClick = { audio.seekBy(-15_000) }) { Text("-15 s") }
                    Button(onClick = audio::togglePlayPause, modifier = Modifier.focusRequester(playFocus)) {
                        Text(if (np.isPlaying) "Pause" else "Play")
                    }
                    Button(onClick = { audio.seekBy(30_000) }) { Text("+30 s") }
                    Button(onClick = audio::next) { Text("Next") }
                }
            }
        }
        val upNext = QueueOps.upNext(np.queue, np.currentIndex)
        Text(
            if (upNext.isEmpty()) "Nothing queued after this" else "Up next",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(upNext, key = { "${it.start}-${it.identifier}" }) { run ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    ListItem(
                        selected = false,
                        onClick = { audio.skipTo(run.start) },
                        headlineContent = {
                            Text(runLabel(run, np.queue, np.currentIndex), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { audio.removeRun(run) }) { Text("Remove") }
                }
            }
        }
    }
}
