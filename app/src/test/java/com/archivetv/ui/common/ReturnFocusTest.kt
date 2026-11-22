package com.archivetv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReturnFocusTest {
    @Test fun nothingOpenedMeansNothingToRestore() {
        assertThat(ReturnFocus().restore()).isFalse()
    }

    @Test fun restoreTargetsTheOpenerOnce() {
        val memory = ReturnFocus()
        memory.opened = "Row/item-1"
        assertThat(memory.restore()).isTrue()
        assertThat(memory.pending).isEqualTo("Row/item-1")
        assertThat(memory.isRestoring).isTrue()
        // Used up: a later Back (say from the player, opened by a button) falls back.
        memory.cancel()
        assertThat(memory.isRestoring).isFalse()
        assertThat(memory.restore()).isFalse()
    }

    @Test fun aNewRestoreResetsTheResult() {
        val memory = ReturnFocus()
        memory.opened = "a"
        memory.restore()
        memory.restored = true
        memory.opened = "b"
        memory.restore()
        assertThat(memory.restored).isFalse()
        assertThat(memory.pending).isEqualTo("b")
    }

    @Test fun anEarlierSuccessDoesNotLeakIntoABackWithNothingToRestore() {
        val memory = ReturnFocus()
        memory.opened = "a"
        memory.restore()
        memory.restored = true
        // Next Back comes from a screen a button opened: nothing to restore, and no stale success.
        assertThat(memory.restore()).isFalse()
        assertThat(memory.restored).isFalse()
    }
}
