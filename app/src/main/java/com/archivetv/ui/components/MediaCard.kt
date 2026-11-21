package com.archivetv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.archivetv.domain.ArchiveItem
import com.archivetv.ui.theme.Dimens
import com.archivetv.ui.theme.SectionIcons
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * The one card used everywhere: thumbnail on a focus-scaled card, title and
 * year underneath. Pass [width] = null to fill the parent (grids).
 */
@Composable
fun MediaCard(
    item: ArchiveItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    width: Dp? = Dimens.CardWidth,
    subtitle: String? = listOfNotNull(item.year, "Community upload".takeIf { item.isCommunity })
        .joinToString(" · ").ifEmpty { null },
) {
    StandardCardContainer(
        modifier = if (width != null) modifier.width(width) else modifier.fillMaxWidth(),
        imageCard = { interaction ->
            Card(
                onClick = onClick,
                interactionSource = interaction,
                modifier = cardModifier
                    .fillMaxWidth()
                    .aspectRatio(Dimens.CardImageAspect),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                    ),
                ),
                colors = CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                CardImage(item)
            }
        },
        title = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        },
        subtitle = {
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun CardImage(item: ArchiveItem) {
    var failed by remember(item.identifier) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (failed) {
            // No artwork: show the title on the tinted surface instead of a blank tile.
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        } else {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.isAudio) {
            Icon(
                SectionIcons.Headphones,
                contentDescription = "Audio",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(3.dp)
                    .size(18.dp),
            )
        }
        item.progress?.let { fraction ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Non-focusable stand-in shown while a row loads, same size as a card. */
@Composable
fun PlaceholderCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(Dimens.CardWidth)
            .aspectRatio(Dimens.CardImageAspect)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.medium,
            ),
    )
}
