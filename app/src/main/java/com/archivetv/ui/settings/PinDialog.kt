package com.archivetv.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.data.AppSettings
import com.archivetv.data.PinGate
import kotlinx.coroutines.delay

/** SET: choose a PIN (enter, confirm). VERIFY: enter the PIN. CHANGE: current PIN, then a new one. */
enum class PinMode { SET, VERIFY, CHANGE }

private enum class Step { CURRENT, NEW, CONFIRM }

/**
 * Four-digit PIN entry for the remote: an on-screen keypad (D-pad and OK) or the remote's number keys.
 * A fourth digit submits. Back cancels. [onDone] gets true once the PIN was verified or set.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PinDialog(
    mode: PinMode,
    gate: PinGate,
    settings: AppSettings,
    /** Replaces the first step's title (the first-run prompt says "Set a parental PIN"). */
    firstTitle: String? = null,
    /** One line under the title explaining why the PIN is asked for. */
    intro: String? = null,
    /** Adds a button that closes the dialog without a PIN ("Not now"). */
    dismissLabel: String? = null,
    onDone: (Boolean) -> Unit,
) {
    var step by remember { mutableStateOf(if (mode == PinMode.SET) Step.NEW else Step.CURRENT) }
    var digits by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedMs by remember { mutableLongStateOf(gate.lockedForMs()) }
    var showForgot by remember { mutableStateOf(false) }
    val firstStep = if (mode == PinMode.SET) Step.NEW else Step.CURRENT
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { firstKey.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    // "Forgot PIN?" replaces itself with text; focus would otherwise fall out of the dialog.
    LaunchedEffect(showForgot) {
        if (!showForgot) return@LaunchedEffect
        repeat(10) {
            if (runCatching { firstKey.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    // Counts the lockout down while it lasts.
    LaunchedEffect(lockedMs > 0) {
        while (gate.lockedForMs() > 0) {
            lockedMs = gate.lockedForMs()
            delay(500)
        }
        lockedMs = 0
    }

    fun submit(pin: String) {
        when (step) {
            Step.CURRENT -> {
                if (gate.lockedForMs() > 0) return
                val ok = settings.checkPin(pin)
                gate.record(ok)
                lockedMs = gate.lockedForMs()
                when {
                    !ok -> message = "Wrong PIN"
                    mode == PinMode.CHANGE -> { step = Step.NEW; message = null }
                    else -> onDone(true)
                }
            }
            Step.NEW -> { firstPin = pin; step = Step.CONFIRM; message = null }
            Step.CONFIRM -> if (pin == firstPin) {
                settings.setPin(pin)
                onDone(true)
            } else {
                firstPin = ""
                step = Step.NEW
                message = "PINs didn't match"
            }
        }
    }

    fun type(d: Char) {
        if (lockedMs > 0 && step == Step.CURRENT) return
        val next = digits + d
        if (next.length < 4) {
            digits = next
        } else {
            digits = ""
            submit(next)
        }
    }

    val title = when {
        step == firstStep && firstTitle != null -> firstTitle
        step == Step.CURRENT -> if (mode == PinMode.CHANGE) "Enter current PIN" else "Enter PIN"
        step == Step.NEW -> if (mode == PinMode.CHANGE) "New PIN" else "Set a PIN"
        else -> "Enter the PIN again"
    }
    val note = if (lockedMs > 0 && step == Step.CURRENT) "Too many tries. Wait ${(lockedMs + 999) / 1000} s." else message

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                when {
                    // Taken here: Compose would spend an unhandled Back on clearing focus first.
                    code == KeyEvent.KEYCODE_BACK -> {
                        if (e.type == KeyEventType.KeyUp) onDone(false)
                        true
                    }
                    code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        if (e.type == KeyEventType.KeyDown) type('0' + (code - KeyEvent.KEYCODE_0))
                        true
                    }
                    code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                        if (e.type == KeyEventType.KeyDown) type('0' + (code - KeyEvent.KEYCODE_NUMPAD_0))
                        true
                    }
                    code == KeyEvent.KEYCODE_DEL -> {
                        if (e.type == KeyEventType.KeyDown) digits = digits.dropLast(1)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                // Focus stays on the keypad while the dialog is open.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            intro?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    val filled = i < digits.length
                    Box(
                        Modifier
                            .size(18.dp)
                            .border(2.dp, Color.White, CircleShape)
                            .background(if (filled) Color.White else Color.Transparent, CircleShape),
                    )
                }
            }
            Text(
                note ?: " ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            val rows = listOf("123", "456", "789", " 0<")
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        when (c) {
                            ' ' -> Spacer(Modifier.size(width = 72.dp, height = 48.dp))
                            '<' -> Button(
                                onClick = { digits = digits.dropLast(1) },
                                shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            ) { Text("Delete") }
                            else -> Button(
                                onClick = { type(c) },
                                modifier = if (c == '1') Modifier.focusRequester(firstKey) else Modifier,
                                shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            ) { Text(c.toString(), modifier = Modifier.padding(horizontal = 8.dp)) }
                        }
                    }
                }
            }
            // Recovery is offered only where the PIN is asked for, not on the Settings screen.
            if (step == Step.CURRENT) {
                if (showForgot) {
                    Text(
                        "Clear Longplay's storage in the TV's app settings, then set a new PIN.\n" +
                            "This also clears My List and your progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                } else {
                    Button(onClick = { showForgot = true }) { Text("Forgot PIN?") }
                }
            }
            dismissLabel?.let { label -> Button(onClick = { onDone(false) }) { Text(label) } }
        }
    }
}
