package com.archivetv.ui.player

import android.view.KeyEvent
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerKeysTest {
    private fun action(key: Int, bar: Boolean = false, seekBar: Boolean = false, captions: Boolean = false) =
        keyAction(key, barVisible = bar, seekBarFocused = seekBar, captionsOpen = captions)

    @Test fun hiddenBarLeftRightSeek() {
        assertThat(action(KeyEvent.KEYCODE_DPAD_RIGHT)).isEqualTo(KeyAction.SEEK_FORWARD)
        assertThat(action(KeyEvent.KEYCODE_DPAD_LEFT)).isEqualTo(KeyAction.SEEK_BACK)
    }

    @Test fun visibleBarLeftRightMoveFocusUnlessOnSeekBar() {
        assertThat(action(KeyEvent.KEYCODE_DPAD_LEFT, bar = true)).isEqualTo(KeyAction.PASS)
        assertThat(action(KeyEvent.KEYCODE_DPAD_RIGHT, bar = true)).isEqualTo(KeyAction.PASS)
        assertThat(action(KeyEvent.KEYCODE_DPAD_LEFT, bar = true, seekBar = true)).isEqualTo(KeyAction.SEEK_BACK)
        assertThat(action(KeyEvent.KEYCODE_DPAD_RIGHT, bar = true, seekBar = true)).isEqualTo(KeyAction.SEEK_FORWARD)
    }

    @Test fun otherKeysShowAHiddenBar() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_ENTER)
            .forEach {
                assertThat(action(it)).isEqualTo(KeyAction.SHOW_BAR)
                assertThat(action(it, bar = true)).isEqualTo(KeyAction.PASS)
            }
    }

    @Test fun mediaKeysActWhetherOrNotTheBarShows() {
        listOf(false, true).forEach { bar ->
            assertThat(action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, bar = bar)).isEqualTo(KeyAction.PLAY_PAUSE)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_PLAY, bar = bar)).isEqualTo(KeyAction.PLAY)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_PAUSE, bar = bar)).isEqualTo(KeyAction.PAUSE)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, bar = bar)).isEqualTo(KeyAction.SEEK_FORWARD)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_REWIND, bar = bar)).isEqualTo(KeyAction.SEEK_BACK)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_NEXT, bar = bar)).isEqualTo(KeyAction.NEXT)
            assertThat(action(KeyEvent.KEYCODE_MEDIA_PREVIOUS, bar = bar)).isEqualTo(KeyAction.PREVIOUS)
        }
    }

    @Test fun backOrder() {
        assertThat(action(KeyEvent.KEYCODE_BACK, bar = true, captions = true)).isEqualTo(KeyAction.CLOSE_CAPTIONS)
        assertThat(action(KeyEvent.KEYCODE_BACK, bar = true)).isEqualTo(KeyAction.HIDE_BAR)
        assertThat(action(KeyEvent.KEYCODE_BACK)).isEqualTo(KeyAction.EXIT)
    }

    @Test fun captionListGetsItsOwnKeys() {
        // Up/Down/OK move and pick inside the list; Left/Right do not seek behind it.
        assertThat(action(KeyEvent.KEYCODE_DPAD_DOWN, bar = true, captions = true)).isEqualTo(KeyAction.PASS)
        assertThat(action(KeyEvent.KEYCODE_DPAD_RIGHT, bar = true, captions = true)).isEqualTo(KeyAction.PASS)
    }

    @Test fun autoHideOnlyWhilePlaying() {
        assertThat(autoHides(true, Player.STATE_READY)).isTrue()
        assertThat(autoHides(false, Player.STATE_READY)).isFalse()
        assertThat(autoHides(true, Player.STATE_BUFFERING)).isFalse()
        assertThat(autoHides(false, Player.STATE_ENDED)).isFalse()
    }

    @Test fun onlyNavigationKeysShowAHiddenBar() {
        // Search, volume, mute, stop and captions keep their own jobs.
        listOf(
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_CAPTIONS,
        ).forEach { assertThat(action(it)).isEqualTo(KeyAction.PASS) }
    }

    @Test fun seekShowsTheBarOnlyForMediaKeys() {
        assertThat(seekKeyShowsBar(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)).isTrue()
        assertThat(seekKeyShowsBar(KeyEvent.KEYCODE_MEDIA_REWIND)).isTrue()
        assertThat(seekKeyShowsBar(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(seekKeyShowsBar(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
    }

    @Test fun pauseIconFollowsIntentNotBuffering() {
        // While a stream buffers on open, the button must offer Pause, which is what OK will do.
        assertThat(showsPause(playWhenReady = true, playbackState = Player.STATE_BUFFERING)).isTrue()
        assertThat(showsPause(playWhenReady = true, playbackState = Player.STATE_READY)).isTrue()
        assertThat(showsPause(playWhenReady = false, playbackState = Player.STATE_READY)).isFalse()
        assertThat(showsPause(playWhenReady = true, playbackState = Player.STATE_ENDED)).isFalse()
    }

    @Test fun videoSessionIdsAreUnique() {
        val a = nextVideoSessionId()
        val b = nextVideoSessionId()
        assertThat(a).startsWith("video")
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun buttonsFollowTheItem() {
        assertThat(barButtons(1, 0)).isEqualTo(BarButtons(prevNext = false, captions = false))
        assertThat(barButtons(3, 2)).isEqualTo(BarButtons(prevNext = true, captions = true))
    }
}
