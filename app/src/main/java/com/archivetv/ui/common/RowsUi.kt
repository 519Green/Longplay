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

/** One row per [ContentRow]; Loading shows placeholders, Failed a retry card. */
fun LazyListScope.contentRows(
    rows: List<ContentRow>,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: (Int) -> Unit,
    onSeeAll: ((ContentRow) -> Unit)? = null,
) {
    rows.forEachIndexed { index, row ->
        item(key = "row-${row.config.id}") {
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
                MediaCard(item = item, onClick = { onClick(item) })
            }
            if (onSeeAll != null) {
                item(key = "see-all") { LabelCard("See all", onClick = onSeeAll) }
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
            item { LabelCard("Couldn't load this row", subtitle = "Select to retry", onClick = onRetry) }
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
            items(tiles, key = { it.first }) { (label, onClick) -> LabelCard(label, onClick = onClick) }
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
) {
    Card(
        onClick = onClick,
        modifier = (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .aspectRatio(Dimens.CardImageAspect),
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
