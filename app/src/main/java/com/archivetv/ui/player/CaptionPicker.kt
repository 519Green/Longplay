package com.archivetv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ui.theme.Dimens
import kotlinx.coroutines.delay

/** The CC list: "Off", then one row per caption track. OK picks a row and closes the list. */
@Composable
fun CaptionPicker(labels: List<String>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier
                .padding(end = Dimens.OverscanHorizontal, bottom = 160.dp)
                .width(360.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Captions",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            (listOf<String?>(null) + labels).forEachIndexed { i, label ->
                ListItem(
                    selected = label == selected,
                    onClick = {
                        onPick(label)
                        onDismiss()
                    },
                    headlineContent = { Text(label ?: "Off") },
                    trailingContent = if (label == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                    } else null,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
