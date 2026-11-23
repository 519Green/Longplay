package com.archivetv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.archivetv.data.AppSettings
import com.archivetv.domain.RightsRule
import com.archivetv.ui.theme.Dimens
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(settings: AppSettings) {
    val openOnly by settings.openOnly.collectAsStateWithLifecycle()
    val cutoff = remember { RightsRule.cutoffYear(LocalDate.now()) }
    val rowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The row may attach a frame or two after the screen does.
        repeat(10) {
            if (runCatching { rowFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical, end = Dimens.OverscanHorizontal),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        ListItem(
            selected = false,
            onClick = { settings.setOpenOnly(!openOnly) },
            headlineContent = { Text("Public domain and openly licensed only") },
            supportingContent = {
                Text(
                    "Shows only titles the Internet Archive marks as public domain or openly licensed, " +
                        "made in $cutoff or earlier, or made by the US government.",
                )
            },
            // Not focusable on its own: the row is the one control, and OK on it toggles.
            trailingContent = { Switch(checked = openOnly, onCheckedChange = null) },
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(max = 720.dp)
                .focusRequester(rowFocus),
        )
    }
}
