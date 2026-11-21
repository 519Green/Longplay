package com.archivetv.playback

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioConnectionTest {
    private val audio = AudioConnection(
        context = ApplicationProvider.getApplicationContext(),
        resolve = { error("not used") },
        progress = { _, _ -> emptyMap() },
    )

    @Test fun controlsBeforeConnectAreNoOps() {
        // A video starting before the service is connected calls pause(); it must not crash.
        audio.pause()
        audio.togglePlayPause()
        audio.seekBy(30_000)
        audio.next()
        audio.previous()
        audio.skipTo(3)
        assertThat(audio.state.value).isNull()
    }
}
