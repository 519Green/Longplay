package com.archivetv.ui.player

import android.view.KeyEvent
import androidx.media3.common.Player

/** What a remote key does in the player. */
enum class KeyAction { SEEK_BACK, SEEK_FORWARD, SHOW_BAR, PLAY_PAUSE, PLAY, PAUSE, NEXT, PREVIOUS, CLOSE_CAPTIONS, HIDE_BAR, EXIT, PASS }

/**
 * Key rules for the control bar:
 * - media keys always act, whether or not the bar shows;
 * - Left/Right seek while the bar is hidden or the seek bar has focus, otherwise they move focus;
 * - any other key on a hidden bar only shows it;
 * - Back closes the caption list, then the bar, then the player.
 */
fun keyAction(keyCode: Int, barVisible: Boolean, seekBarFocused: Boolean, captionsOpen: Boolean): KeyAction {
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> return KeyAction.PLAY_PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY -> return KeyAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> return KeyAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> return KeyAction.SEEK_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> return KeyAction.SEEK_BACK
        KeyEvent.KEYCODE_MEDIA_NEXT -> return KeyAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return KeyAction.PREVIOUS
        KeyEvent.KEYCODE_BACK -> return when {
            captionsOpen -> KeyAction.CLOSE_CAPTIONS
            barVisible -> KeyAction.HIDE_BAR
            else -> KeyAction.EXIT
        }
    }
    if (captionsOpen) return KeyAction.PASS
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
        if (barVisible && !seekBarFocused) return KeyAction.PASS
        return if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) KeyAction.SEEK_BACK else KeyAction.SEEK_FORWARD
    }
    return if (barVisible) KeyAction.PASS else KeyAction.SHOW_BAR
}

/** The bar hides on its own only while the picture is actually moving. */
fun autoHides(isPlaying: Boolean, playbackState: Int): Boolean = isPlaying && playbackState == Player.STATE_READY

data class BarButtons(val prevNext: Boolean, val captions: Boolean)

fun barButtons(fileCount: Int, textTrackCount: Int) = BarButtons(prevNext = fileCount > 1, captions = textTrackCount > 0)
