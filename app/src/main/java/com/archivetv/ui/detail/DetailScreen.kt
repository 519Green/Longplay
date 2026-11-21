package com.archivetv.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.archivetv.ArchiveTvApp
import com.archivetv.data.StartKind
import com.archivetv.data.isFinished
import com.archivetv.data.resumeFraction
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.PlayableFile

/**
 * @param onPlay video: identifier plus a file index; null index means "where I left off".
 * @param onPlayAudio the same for audio items, which play through the background player.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    identifier: String,
    onPlay: (String, Int?) -> Unit,
    onPlayAudio: (String, Int?) -> Unit = onPlay,
    viewModel: DetailViewModel = detailViewModel(),
) {
    LaunchedEffect(identifier) { viewModel.load(identifier) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audio = (LocalContext.current.applicationContext as ArchiveTvApp).container.audio
    val nowPlaying by audio.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmation) {
        if (confirmation != null) { delay(2_000); confirmation = null }
    }
    fun queue(next: Boolean, fileIndex: Int?) {
        scope.launch {
            val ok = if (next) audio.playNext(identifier, fileIndex) else audio.addToQueue(identifier, fileIndex)
            confirmation = if (ok) "Added to queue" else "Could not add this to the queue"
        }
    }
    val play: (String, Int?) -> Unit = if (state.isAudio) onPlayAudio else onPlay
    var menuFor by remember { mutableStateOf<Int?>(null) }
    // The open menu's row button; focus goes back to it when the menu closes.
    val menuButtonFocus = remember { FocusRequester() }
    fun closeMenu() {
        runCatching { menuButtonFocus.requestFocus() }
        menuFor = null
    }
    val item = state.item
    Box(Modifier.fillMaxSize()) {
        if (item != null) {
            val multi = state.files.size > 1
            // TV's default scrolls the focused item to ~30% from the top, which
            // pushes the title off screen when Play takes focus. Scroll only as
            // far as needed to show the focused element.
            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    Header(
                        item = item,
                        state = state,
                        onPlay = { play(identifier, null) },
                        onPlayNext = { queue(next = true, fileIndex = null) },
                        onAddToQueue = { queue(next = false, fileIndex = null) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        heightFraction = if (multi) 0.82f else 1f,
                    )
                }
                if (multi) {
                    item(key = "episodes-title") {
                        Text(
                            listHeader(state.kind, state.isLibriVox, state.files.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(state.files, key = { _, f -> f.fileName }) { index, file ->
                        val label = state.labels.getOrElse(index) { file.title }
                        val saved = state.progress[file.fileName]
                        EpisodeRow(
                            index = index,
                            file = file,
                            label = label,
                            fraction = saved?.let { resumeFraction(it.positionMs, it.durationMs) },
                            watched = saved?.let { isFinished(it.positionMs, it.durationMs) } == true,
                            playing = nowPlaying?.current?.let { it.identifier == identifier && it.fileName == file.fileName } == true,
                            onClick = { play(identifier, index) },
                            onQueue = if (state.isAudio) ({ menuFor = if (menuFor == index) null else index }) else null,
                            queueFocus = if (menuFor == index) menuButtonFocus else null,
                        )
                        if (menuFor == index) {
                            QueueMenu(
                                onPlayNext = { closeMenu(); queue(next = true, fileIndex = index) },
                                onAddToQueue = { closeMenu(); queue(next = false, fileIndex = index) },
                            )
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(27.dp)) }
                }
            }
            }
        }
        confirmation?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
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
private fun LazyItemScope.Header(
    item: ArchiveItem,
    state: DetailUiState,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    heightFraction: Float,
) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(heightFraction)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f).align(Alignment.TopCenter)) {
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
                .background(Brush.horizontalGradient(0f to background.copy(alpha = 0.85f), 0.6f to Color.Transparent))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.65f)
                .padding(horizontal = 48.dp, vertical = 27.dp),
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
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = onPlay,
                    enabled = state.playable,
                    modifier = Modifier.focusRequester(playFocus).widthIn(max = 420.dp),
                ) {
                    Text(playLabel(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.isAudio && state.playable) {
                    Button(onClick = onPlayNext) { Text("Play next", maxLines = 1, softWrap = false) }
                    Button(onClick = onAddToQueue) { Text("Add to queue", maxLines = 1, softWrap = false) }
                }
                Button(onClick = onToggleFavorite, modifier = Modifier.focusRequester(listFocus)) {
                    Text(if (state.isFavorite) "Remove from My List" else "Add to My List", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val MinimalScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f -> offset
            else -> trailing - containerSize
        }
    }
}

private fun playLabel(state: DetailUiState): String {
    if (!state.playable) return "Unavailable"
    val start = state.start
    val label = state.labels.getOrNull(start.index)
    val multi = state.files.size > 1
    return when (start.kind) {
        StartKind.BEGIN -> "Play"
        StartKind.RESUME ->
            if (multi) "Resume ${start.index + 1}. $label" else "Resume from ${clock(start.positionMs / 1000)}"
        StartKind.NEXT -> "Play next: ${start.index + 1}. $label"
    }
}

@Composable
private fun EpisodeRow(
    index: Int,
    file: PlayableFile,
    label: String,
    fraction: Float?,
    watched: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onQueue: (() -> Unit)?,
    queueFocus: FocusRequester? = null,
) {
    Row(
        modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth(0.7f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        headlineContent = {
            Text("${index + 1}. $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                file.durationSec?.let { Text(minutes(it), style = MaterialTheme.typography.bodySmall) }
                if (fraction != null && !watched && fraction > 0f) {
                    Box(Modifier.fillMaxWidth(0.5f).height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        },
        trailingContent = {
            when {
                playing -> Icon(Icons.Filled.PlayArrow, contentDescription = "Playing")
                watched -> Icon(Icons.Filled.Check, contentDescription = "Played")
            }
        },
    )
    if (onQueue != null) {
        // Reached with D-pad Right from the row.
        IconButton(
            onClick = onQueue,
            modifier = if (queueFocus != null) Modifier.focusRequester(queueFocus) else Modifier,
        ) { Icon(Icons.Filled.Add, contentDescription = "Queue options") }
    }
    }
}

/** Play next / Add to queue for one file, shown under its row. */
@Composable
private fun QueueMenu(onPlayNext: () -> Unit, onAddToQueue: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Row(
        modifier = Modifier.padding(start = 64.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onPlayNext, modifier = Modifier.focusRequester(first)) { Text("Play next") }
        Button(onClick = onAddToQueue) { Text("Add to queue") }
    }
}

/** 1530 s -> "26 min"; 5520 s -> "1 h 32 min". */
private fun minutes(seconds: Double): String {
    val total = (seconds / 60).toInt().coerceAtLeast(1)
    return if (total < 60) "$total min" else "${total / 60} h ${total % 60} min"
}

/** 2480 -> "41:20"; 3723 -> "1:02:03". */
private fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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
