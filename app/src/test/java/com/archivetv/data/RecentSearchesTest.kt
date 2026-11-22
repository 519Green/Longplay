package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentSearchesTest {
    @Test fun newestFirstWithoutCaseDuplicates() {
        assertThat(withRecent(listOf("Night", "dog"), " night ")).containsExactly("night", "dog").inOrder()
    }

    @Test fun keepsAtMostTheLimitAndIgnoresBlank() {
        val full = (1..8).map { "q$it" }
        assertThat(withRecent(full, "new")).hasSize(RECENT_SEARCH_LIMIT)
        assertThat(withRecent(full, "new").first()).isEqualTo("new")
        assertThat(withRecent(full, "   ")).isEqualTo(full)
    }
}
