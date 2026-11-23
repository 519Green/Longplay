package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SavedFilterTest {
    private val asked = mutableListOf<List<String>>()
    private var fail = false
    private var on = true
    private val filter = SavedFilter(
        flagged = { ids ->
            asked += ids
            if (fail) error("offline")
            ids.filter { it.startsWith("x") }.toSet()
        },
        enabled = { on },
    )

    @Test fun offLooksNothingUp() = runTest {
        on = false
        assertThat(filter.hidden(listOf("x1", "a"))).isEmpty()
        assertThat(asked).isEmpty()
    }

    @Test fun onCachesPerId() = runTest {
        assertThat(filter.hidden(listOf("x1", "a"))).containsExactly("x1")
        assertThat(filter.hidden(listOf("a", "x1"))).containsExactly("x1")
        assertThat(asked).containsExactly(listOf("x1", "a"))
        filter.hidden(listOf("a", "x2"))
        assertThat(asked.last()).containsExactly("x2")
    }

    @Test fun failureHidesNothingAndRetries() = runTest {
        fail = true
        assertThat(filter.hidden(listOf("x1", "a"))).isEmpty()
        fail = false
        assertThat(filter.hidden(listOf("x1", "a"))).containsExactly("x1")
        assertThat(asked).hasSize(2)
    }

    @Test fun clearForgets() = runTest {
        filter.hidden(listOf("x1"))
        filter.clear()
        filter.hidden(listOf("x1"))
        assertThat(asked).hasSize(2)
    }
}
