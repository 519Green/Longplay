package com.archivetv.ui.live

import com.archivetv.domain.Artist
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
class ArtistsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val artists = listOf(
        Artist("y", "'68 Youth Riot"),
        Artist("s", "...And Stars Collide"),
        Artist("m", "moe."),
        Artist("m2", "Mountain Band"),
        Artist("z", "Zydeco Jed"),
    )

    @Test fun filtersIgnoringCaseAndPunctuation() = runTest {
        val vm = ArtistsViewModel(load = { artists })
        vm.setQuery("AND stars")
        assertThat(vm.state.value.shown.map { it.name }).containsExactly("...And Stars Collide")
        assertThat(vm.state.value.query).isEqualTo("AND stars")
        vm.setQuery("")
        assertThat(vm.state.value.shown).hasSize(5)
    }

    @Test fun lettersListOnlyThosePresent() = runTest {
        val vm = ArtistsViewModel(load = { listOf(Artist("z", "Zydeco Jed"), Artist("y", "'68 Youth Riot"), Artist("m", "moe.")) })
        assertThat(vm.state.value.letters).containsExactly('#', 'M', 'Z').inOrder()
        assertThat(vm.state.value.loading).isFalse()
    }

    @Test fun indexOfLetterFindsFirst() = runTest {
        val vm = ArtistsViewModel(load = { artists })
        assertThat(vm.indexOf('M')).isEqualTo(2)
        assertThat(vm.indexOf('#')).isEqualTo(0)
        assertThat(vm.indexOf('Q')).isEqualTo(-1)
    }

    @Test fun failedLoadCanRetry() = runTest {
        var calls = 0
        val vm = ArtistsViewModel(load = { if (calls++ == 0) error("down") else artists })
        assertThat(vm.state.value.failed).isTrue()
        assertThat(vm.state.value.loading).isFalse()
        vm.retry()
        assertThat(vm.state.value.failed).isFalse()
        assertThat(vm.state.value.shown).hasSize(5)
    }
}
