package com.archivetv.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.ui.common.ContentRow
import com.archivetv.ui.common.RowState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersRowTitlesAndFailedRow() {
        compose.setContent {
            HomeContent(
                continueWatching = emptyList(),
                favorites = listOf(ArchiveItem("id1", "Saved Film")),
                rows = listOf(ContentRow(BrowseRow("film-noir", "Film Noir", "q"), RowState.Failed)),
                hero = null,
                onItemClick = {}, onRetry = {},
            )
        }
        compose.onNodeWithText("My List").assertIsDisplayed()
        compose.onNodeWithText("Film Noir").assertIsDisplayed()
        compose.onNodeWithText("Couldn't load this row").assertIsDisplayed()
    }
}
