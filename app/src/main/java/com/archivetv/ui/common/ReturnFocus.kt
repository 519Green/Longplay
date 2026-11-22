package com.archivetv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * Where Back puts focus on a screen: the card or tile that opened the screen
 * above it. A screen is recomposed from scratch when Back returns to it, so
 * focus restorers have nothing left to restore; this keeps the opener's key
 * per back stack entry instead. The nav host calls [restore] after Back and
 * falls back to the screen's own restorer when nothing took focus.
 */
class ReturnFocus {
    /** Key of the element clicked last; cleared once used, so it never goes stale. */
    internal var opened: String? = null

    /** The element with this key takes focus while it is set. */
    internal var pending by mutableStateOf<String?>(null)

    var restored: Boolean = false
        internal set

    val isRestoring: Boolean get() = pending != null

    /** Asks the element that opened the screen above to take focus; false when there is none. */
    fun restore(): Boolean {
        val key = opened ?: return false
        opened = null
        restored = false
        pending = key
        return true
    }

    /** Gives up a restore whose element never appeared (it scrolled away or its data changed). */
    fun cancel() {
        pending = null
    }
}

/** The current screen's [ReturnFocus]; null outside the nav host (previews, tests). */
val LocalReturnFocus = staticCompositionLocalOf<ReturnFocus?> { null }

/** Focus hooks for one clickable element: apply [modifier], call [opened] in its onClick. */
class ReturnTarget(val modifier: Modifier, val opened: () -> Unit)

/**
 * Lets the element with [key] get focus back after Back. [key] must be unique
 * on the screen: rows that can share an item prefix it with the row title.
 */
@Composable
fun returnFocusTarget(key: String): ReturnTarget {
    val memory = LocalReturnFocus.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (memory != null && memory.pending == key) {
        LaunchedEffect(Unit) {
            // The element may attach a frame or two after its screen resumes.
            repeat(20) {
                runCatching { requester.requestFocus() }
                delay(30)
                if (focused) {
                    memory.restored = true
                    memory.pending = null
                    return@LaunchedEffect
                }
            }
            memory.pending = null
        }
    }
    return remember(memory, key) {
        ReturnTarget(
            modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.hasFocus },
            opened = { memory?.opened = key },
        )
    }
}
