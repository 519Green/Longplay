package com.archivetv.ui.player

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerTextTest {
    private val labels = listOf("Part 1", "Part 2", "Part 3")

    @Test fun upNextShowsInTheLastTwentySecondsOnly() {
        assertThat(upNextText(0, 579_000, 600_000, labels)).isNull()
        assertThat(upNextText(0, 580_000, 600_000, labels)).isEqualTo("Up next: Part 2")
        assertThat(upNextText(1, 599_999, 600_000, labels)).isEqualTo("Up next: Part 3")
    }

    @Test fun noUpNextOnTheLastFileOrWithoutADuration() {
        assertThat(upNextText(2, 590_000, 600_000, labels)).isNull()
        assertThat(upNextText(0, 0, 0, labels)).isNull()
        assertThat(upNextText(0, 590_000, 600_000, listOf("Only"))).isNull()
    }

    @Test fun clockText() {
        assertThat(clockText(0)).isEqualTo("0:00")
        assertThat(clockText(754_000)).isEqualTo("12:34")
        assertThat(clockText(3_723_000)).isEqualTo("1:02:03")
    }

    @Test fun errorTextByCause() {
        assertThat(playbackErrorText(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
            .isEqualTo("The connection to Archive.org dropped.")
        assertThat(playbackErrorText(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
            .startsWith("Archive.org did not send")
        assertThat(playbackErrorText(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
            .isEqualTo("This device can't play this file's format.")
        assertThat(playbackErrorText(PlaybackException.ERROR_CODE_UNSPECIFIED))
            .isEqualTo("Playback stopped unexpectedly.")
    }
}
