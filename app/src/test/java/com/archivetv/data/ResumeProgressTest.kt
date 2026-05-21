package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumeProgressTest {
    @Test fun fractionIsPositionOverDuration() {
        assertThat(resumeFraction(2500L, 10000L)).isWithin(0.001f).of(0.25f)
    }

    @Test fun fractionZeroWhenDurationUnknown() {
        assertThat(resumeFraction(2500L, 0L)).isEqualTo(0f)
    }

    @Test fun nearEndIsNotResumable() {
        assertThat(isResumable(9600L, 10000L)).isFalse()
    }

    @Test fun midpointIsResumable() {
        assertThat(isResumable(5000L, 10000L)).isTrue()
    }
}
