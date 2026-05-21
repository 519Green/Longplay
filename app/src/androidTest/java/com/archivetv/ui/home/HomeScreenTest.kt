package com.archivetv.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archivetv.domain.ArchiveItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersSearchAndRowTitles() {
        compose.setContent {
            HomeContent(
                continueWatching = emptyList(),
                favorites = listOf(ArchiveItem("id1", "Saved Film")),
                rows = emptyList(),
                hero = null,
                onItemClick = {}, onSearch = {},
            )
        }
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithText("My List").assertIsDisplayed()
    }
}
