package com.archivetv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ui.theme.Dimens
import com.archivetv.ui.theme.PlayerIcons

/** What the control bar shows. */
data class BarState(val positionMs: Long, val durationMs: Long, val isPlaying: Boolean, val buttons: BarButtons)

/**
 * The player's control bar, built for the D-pad: a seek bar (one focusable element; the screen seeks
 * with Left/Right while it has focus), elapsed and remaining time, and a row of buttons.
 */
@Composable
fun PlayerControls(
    state: BarState,
    playPauseFocus: FocusRequester,
    onSeekBarFocus: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCaptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .padding(
                start = Dimens.OverscanHorizontal,
                end = Dimens.OverscanHorizontal,
                top = 48.dp,
                bottom = Dimens.OverscanVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeekBar(state.positionMs, state.durationMs, onSeekBarFocus)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val remaining = (state.durationMs - state.positionMs).coerceAtLeast(0L)
            Text(clockText(state.positionMs), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text("-" + clockText(remaining), style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onPlayPause, modifier = Modifier.focusRequester(playPauseFocus)) {
                Icon(
                    if (state.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
            if (state.buttons.prevNext) {
                IconButton(onClick = onPrevious) { Icon(PlayerIcons.SkipPrevious, contentDescription = "Previous") }
                IconButton(onClick = onNext) { Icon(PlayerIcons.SkipNext, contentDescription = "Next") }
            }
            if (state.buttons.captions) {
                IconButton(onClick = onCaptions) { Icon(PlayerIcons.ClosedCaption, contentDescription = "Captions") }
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onFocus: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val accent = MaterialTheme.colorScheme.primary
    val thumb = 16.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width }
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = if (focused) 8.dp else 4.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(track)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (focused) accent else Color.White, RoundedCornerShape(4.dp)),
            )
        }
        if (focused) {
            val x = with(LocalDensity.current) { (fraction * widthPx - thumbPx / 2).coerceAtLeast(0f).toDp() }
            Box(Modifier.offset(x = x).size(thumb).background(accent, CircleShape))
        }
    }
}
