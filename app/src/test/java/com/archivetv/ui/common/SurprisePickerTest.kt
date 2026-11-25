package com.archivetv.ui.common

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SurprisePickerTest {
    private val leaf = CategoryNode.Leaf("door", "Door", CategoryQuery(collections = listOf("colA")))
    private fun item(id: String) = ArchiveItem(id, "T$id")

    @Test fun picksOneOfTheFetchedItems() = runTest {
        val picker = SurprisePicker(fetch = { listOf(item("a"), item("b"), item("c")) }, random = Random(1))
        assertThat(picker.pick(leaf)!!.identifier).isIn(listOf("a", "b", "c"))
    }

    @Test fun avoidsRecentPicksUntilEveryItemWasShown() = runTest {
        val picker = SurprisePicker(fetch = { listOf(item("a"), item("b"), item("c")) }, random = Random(7))
        val firstThree = (1..3).map { picker.pick(leaf)!!.identifier }
        assertThat(firstThree).containsExactly("a", "b", "c")
        assertThat(picker.pick(leaf)!!.identifier).isIn(listOf("a", "b", "c"))
    }

    @Test fun nothingFetchedPicksNothing() = runTest {
        val picker = SurprisePicker(fetch = { emptyList() })
        assertThat(picker.pick(leaf)).isNull()
    }

    @Test fun doorsKeepCatalogOrderAndSkipMissingLeaves() {
        val catalog = com.archivetv.domain.Catalog(
            sections = listOf(
                CategoryNode.Group(
                    "s", "S",
                    listOf(
                        CategoryNode.Leaf("silent", "Silent", CategoryQuery()),
                        CategoryNode.Leaf("movies-popular", "Movies", CategoryQuery()),
                    ),
                ),
            ),
            home = emptyList(),
            facetGroups = emptyMap(),
        )
        assertThat(surpriseDoors(catalog).map { it.first })
            .containsExactly("Feature film", "Silent film").inOrder()
    }
}
