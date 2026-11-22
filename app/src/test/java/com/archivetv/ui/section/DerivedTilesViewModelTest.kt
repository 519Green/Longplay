package com.archivetv.ui.section

import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.archivetv.domain.DeriveRule
import com.archivetv.domain.DerivedEntry
import com.archivetv.ui.live.Load
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DerivedTilesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val entry = DerivedEntry("coronet", "Coronet Instructional Films", CategoryQuery(collections = listOf("prelinger"), creatorAny = listOf("Coronet")))

    @Test fun loadsEntries() = runTest {
        val vm = DerivedTilesViewModel(load = { listOf(entry) })
        assertThat(vm.state.value).isEqualTo(Load.Done(listOf(entry)))
    }

    @Test fun emptyGroupShowsNothingFound() = runTest {
        val vm = DerivedTilesViewModel(load = { emptyList() })
        assertThat(vm.state.value).isEqualTo(Load.Done(emptyList<DerivedEntry>()))
    }

    @Test fun failedGroupCanRetry() = runTest {
        var calls = 0
        val vm = DerivedTilesViewModel(load = { if (calls++ == 0) error("offline") else listOf(entry) })
        assertThat(vm.state.value).isEqualTo(Load.Failed)
        vm.retry()
        assertThat(vm.state.value).isEqualTo(Load.Done(listOf(entry)))
    }

    @Test fun derivedLeafCarriesEntryAndFacets() {
        val node = CategoryNode.Derived(
            "edu-studios", "By Studio",
            DeriveRule.FieldValues("creator", listOf("prelinger"), "movies", 5000, 10, 30), facetGroup = "decade",
        )
        val leaf = derivedLeaf(node, entry)
        assertThat(leaf.id).isEqualTo("derived-edu-studios-coronet")
        assertThat(leaf.title).isEqualTo("Coronet Instructional Films")
        assertThat(leaf.query).isEqualTo(entry.query)
        assertThat(leaf.facetGroup).isEqualTo("decade")
    }
}
