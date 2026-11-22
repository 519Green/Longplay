package com.archivetv.ui.live

import com.archivetv.domain.Concert
import com.archivetv.domain.YearCount
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun concertsLoadWithoutWaitingForYears() = runTest {
        val years = CompletableDeferred<List<YearCount>>()
        val vm = ArtistViewModel(
            artistId = "SomeBand",
            startYear = { 1977 },
            years = { years.await() },
            concerts = { y -> listOf(concert("c$y", date = "$y-05-08")) },
        )
        val s = vm.state.value
        assertThat(s.years).isNull()
        assertThat(s.selectedYear).isEqualTo(1977)
        assertThat(s.concerts).isEqualTo(Load.Done(listOf(concert("c1977", date = "1977-05-08"))))
        assertThat(s.name).isEqualTo("Some Band")
        years.cancel()
    }

    @Test fun yearsArriveLater() = runTest {
        val years = CompletableDeferred<List<YearCount>>()
        val vm = ArtistViewModel("SomeBand", { 1977 }, { years.await() }, { listOf(concert("c")) })
        years.complete(listOf(YearCount(1976, 3), YearCount(1977, 64), YearCount(1978, 70)))
        val s = vm.state.value
        assertThat(s.years).containsExactly(YearCount(1976, 3), YearCount(1977, 64), YearCount(1978, 70)).inOrder()
        assertThat(s.selectedYear).isEqualTo(1977)
    }

    @Test fun noStartYearShowsNoConcerts() = runTest {
        val vm = ArtistViewModel("SomeBand", { null }, { emptyList() }, { error("must not be called") })
        val s = vm.state.value
        assertThat(s.concerts).isEqualTo(Load.Done(emptyList<Any>()))
        assertThat(s.selectedYear).isNull()
    }

    @Test fun noStartYearFallsBackToNewestYear() = runTest {
        val asked = mutableListOf<Int>()
        val vm = ArtistViewModel("SomeBand", { null }, { listOf(YearCount(1990, 1), YearCount(1995, 2)) }, { asked += it; listOf(concert("c")) })
        assertThat(vm.state.value.selectedYear).isEqualTo(1995)
        assertThat(asked).containsExactly(1995)
    }

    @Test fun selectYearReloads() = runTest {
        val asked = mutableListOf<Int>()
        val vm = ArtistViewModel("SomeBand", { 1977 }, { listOf(YearCount(1977, 1), YearCount(1978, 1)) }, { asked += it; listOf(concert("c$it")) })
        vm.selectYear(1978)
        assertThat(asked).containsExactly(1977, 1978).inOrder()
        assertThat(vm.state.value.selectedYear).isEqualTo(1978)
        assertThat(vm.state.value.concerts).isEqualTo(Load.Done(listOf(concert("c1978"))))
    }

    @Test fun failedConcertsCanRetry() = runTest {
        var calls = 0
        val vm = ArtistViewModel("SomeBand", { 1977 }, { emptyList() }, { if (calls++ == 0) error("down") else listOf(concert("c")) })
        assertThat(vm.state.value.concerts).isEqualTo(Load.Failed)
        vm.retry()
        assertThat(vm.state.value.concerts).isEqualTo(Load.Done(listOf(concert("c"))))
    }

    @Test fun switchingYearDoesNotShowFailedForTheCancelledLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = mapOf(1977 to CompletableDeferred<List<Concert>>(), 1978 to CompletableDeferred())
        val vm = ArtistViewModel("SomeBand", { 1977 }, { emptyList() }, { pending.getValue(it).await() })
        advanceUntilIdle()
        vm.selectYear(1978)
        advanceUntilIdle()
        assertThat(vm.state.value.concerts).isEqualTo(Load.Loading)
        pending.getValue(1978).complete(listOf(concert("c1978")))
        advanceUntilIdle()
        assertThat(vm.state.value.concerts).isEqualTo(Load.Done(listOf(concert("c1978"))))
    }
}
