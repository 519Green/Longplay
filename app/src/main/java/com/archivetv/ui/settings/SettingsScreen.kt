package com.archivetv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.archivetv.data.PinGate
import com.archivetv.domain.RightsRule
import com.archivetv.ui.theme.Dimens
import java.time.LocalDate
import kotlinx.coroutines.delay

/** A PIN prompt in progress: which prompt, what to do once it succeeds, and where focus goes back. */
private class PinRequest(val mode: PinMode, val onSuccess: () -> Unit, val returnTo: FocusRequester)

/**
 * The two content filters and the parental PIN. Turning a filter off needs the PIN (set one first if there is
 * none); turning a filter on never does.
 */
@Composable
fun SettingsScreen(settings: AppSettings, pinGate: PinGate) {
    val openOnly by settings.openOnly.collectAsStateWithLifecycle()
    val hideAdult by settings.hideAdult.collectAsStateWithLifecycle()
    var hasPin by remember { mutableStateOf(settings.hasPin()) }
    var request by remember { mutableStateOf<PinRequest?>(null) }
    val cutoff = remember { RightsRule.cutoffYear(LocalDate.now()) }
    val openFocus = remember { FocusRequester() }
    val adultFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }
    // Where focus goes once a PIN prompt closes (bumped to retry the request).
    var focusTarget by remember { mutableStateOf(openFocus to 0) }

    LaunchedEffect(focusTarget) {
        // The row may attach a frame or two after the screen (or the closing dialog) does.
        repeat(10) {
            if (runCatching { focusTarget.first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    fun turnOff(returnTo: FocusRequester, apply: () -> Unit) {
        request = PinRequest(if (hasPin) PinMode.VERIFY else PinMode.SET, apply, returnTo)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = Dimens.ContentStart, top = Dimens.OverscanVertical, end = Dimens.OverscanHorizontal),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            SwitchRow(
                title = "Public domain and openly licensed only",
                explanation = "Shows only titles the Internet Archive marks as public domain or openly licensed, " +
                    "made in $cutoff or earlier, or made by the US government.",
                checked = openOnly,
                focus = openFocus,
                onToggle = {
                    if (openOnly) turnOff(openFocus) { settings.setOpenOnly(false) } else settings.setOpenOnly(true)
                },
            )
            SwitchRow(
                title = "Hide adult content",
                explanation = "Hides titles the Internet Archive or their uploaders mark as adult.",
                checked = hideAdult,
                focus = adultFocus,
                onToggle = {
                    if (hideAdult) turnOff(adultFocus) { settings.setHideAdult(false) } else settings.setHideAdult(true)
                },
            )
            ListItem(
                selected = false,
                onClick = { request = PinRequest(if (hasPin) PinMode.CHANGE else PinMode.SET, {}, pinFocus) },
                headlineContent = { Text(if (hasPin) "Change PIN" else "Set PIN") },
                supportingContent = { Text("The PIN is needed to turn either filter off.") },
                modifier = Modifier.widthIn(max = 720.dp).focusRequester(pinFocus),
            )
            Text(
                "Forgot the PIN? Clear Longplay's storage in the TV's app settings. " +
                    "This also clears My List and your progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 720.dp).padding(start = 16.dp),
            )
        }
        request?.let { r ->
            PinDialog(mode = r.mode, gate = pinGate, settings = settings) { ok ->
                if (ok) r.onSuccess()
                hasPin = settings.hasPin()
                request = null
                focusTarget = r.returnTo to focusTarget.second + 1
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, explanation: String, checked: Boolean, focus: FocusRequester, onToggle: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onToggle,
        headlineContent = { Text(title) },
        supportingContent = { Text(explanation) },
        // Not focusable on its own: the row is the control, and OK on it toggles.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.widthIn(max = 720.dp).focusRequester(focus),
    )
}
