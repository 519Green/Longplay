package com.archivetv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.components.MediaCard
import com.archivetv.ui.components.PlaceholderCard
import com.archivetv.ui.theme.Dimens

@Composable
fun RowHeader(title: String, start: Dp = Dimens.ContentStart) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = start, top = 12.dp),
    )
}

fun rowPadding(start: Dp = Dimens.ContentStart) = PaddingValues(
    start = start,
    end = Dimens.OverscanHorizontal,
    top = Dimens.RowFocusRoom,
    bottom = Dimens.RowFocusRoom,
)

/**
 * Rows worth showing. A row with a minimum ([BrowseRow.minItems] above 1, the seasonal rows) is a bonus: it shows
 * only once loaded with at least that many items, never as a placeholder or a retry card.
 */
fun visibleRows(rows: List<ContentRow>): List<ContentRow> = rows.filter(::isVisible)

private fun isVisible(row: ContentRow): Boolean {
    if (row.config.minItems <= 1) return true
    val st = row.state
    return st is RowState.Loaded && st.items.size >= row.config.minItems
}

/** One row per [ContentRow]; Loading shows placeholders, Failed a retry card. */
fun LazyListScope.contentRows(
    rows: List<ContentRow>,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: (Int) -> Unit,
    onSeeAll: ((ContentRow) -> Unit)? = null,
    keyPrefix: String = "row-",
) {
    rows.forEachIndexed { index, row ->
        // Filtered here rather than up front so onRetry keeps the row's index in [rows].
        if (!isVisible(row)) return@forEachIndexed
        item(key = "$keyPrefix${row.config.id}") {
            when (val st = row.state) {
                RowState.Loading -> PlaceholderRow(row.config.title)
                RowState.Failed -> FailedRow(row.config.title, onRetry = { onRetry(index) })
                is RowState.Loaded -> if (st.items.isNotEmpty()) {
                    CardRow(
                        title = row.config.title,
                        items = st.items,
                        onClick = onItemClick,
                        onSeeAll = onSeeAll?.let { { it(row) } },
                    )
                }
            }
        }
    }
}

@Composable
fun CardRow(
    title: String,
    items: List<ArchiveItem>,
    onClick: (ArchiveItem) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    start: Dp = Dimens.ContentStart,
) {
    Column {
        RowHeader(title, start)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding(start),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(items, key = { it.identifier }) { item ->
                // The same item can sit in several rows; the title tells them apart.
                MediaCard(item = item, onClick = { onClick(item) }, focusKey = "$title/${item.identifier}")
            }
            if (onSeeAll != null) {
                item(key = "see-all") { LabelCard("See all", onClick = onSeeAll, focusKey = "$title/see-all") }
            }
        }
    }
}

@Composable
fun PlaceholderRow(title: String) {
    Column {
        RowHeader(title)
        LazyRow(
            contentPadding = rowPadding(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            userScrollEnabled = false,
        ) {
            items(5) { PlaceholderCard() }
        }
    }
}

@Composable
fun FailedRow(title: String, onRetry: () -> Unit) {
    Column {
        RowHeader(title)
        LazyRow(contentPadding = rowPadding()) {
            item { LabelCard("Couldn't load this row", subtitle = "Select to retry", onClick = onRetry, focusKey = null) }
        }
    }
}

/** A row of category tiles (e.g. By Genre, By Decade, Shows). */
@Composable
fun TileRow(title: String?, tiles: List<Pair<String, () -> Unit>>, start: Dp = Dimens.ContentStart) {
    Column {
        title?.let { RowHeader(it, start) }
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = rowPadding(start),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            items(tiles, key = { it.first }) { (label, onClick) ->
                LabelCard(label, onClick = onClick, focusKey = "$title/$label")
            }
        }
    }
}

/** Card-sized tile with centered text: category tiles, "See all", retry. */
@Composable
fun LabelCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp? = Dimens.CardWidth,
    /** Unique on the screen; Back from what this tile opens returns focus here. Null: not tracked. */
    focusKey: String? = label,
) {
    val target = returnFocusTarget(focusKey)
    Card(
        onClick = { target.opened(); onClick() },
        modifier = (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .aspectRatio(Dimens.CardImageAspect)
            .then(target.modifier),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
