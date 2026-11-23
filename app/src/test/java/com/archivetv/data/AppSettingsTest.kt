package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsTest {
    @Test fun defaultsToOpenOnlyAndEmitsChanges() {
        val settings = AppSettings.InMemory()
        assertThat(settings.openOnly.value).isTrue()
        settings.setOpenOnly(false)
        assertThat(settings.openOnly.value).isFalse()
    }
}
