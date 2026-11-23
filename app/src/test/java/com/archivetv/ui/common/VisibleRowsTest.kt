package com.archivetv.ui.common

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisibleRowsTest {
    private fun items(n: Int) = (1..n).map { ArchiveItem("i$it", "T$it") }
    private fun row(id: String, minItems: Int, state: RowState) = ContentRow(BrowseRow(id, id, "q", minItems = minItems), state)

    @Test fun rowsBelowMinItemsAreHidden() {
        val rows = listOf(
            row("seven", 8, RowState.Loaded(items(7))),
            row("eight", 8, RowState.Loaded(items(8))),
            row("plain", 1, RowState.Loaded(items(2))),
        )
        assertThat(visibleRows(rows).map { it.config.id }).containsExactly("eight", "plain").inOrder()
    }

    @Test fun aRowWithAMinimumShowsNothingUntilLoaded() {
        val rows = listOf(
            row("loading", 8, RowState.Loading),
            row("failed", 8, RowState.Failed),
            row("plainLoading", 1, RowState.Loading),
        )
        assertThat(visibleRows(rows).map { it.config.id }).containsExactly("plainLoading")
    }
}
