package com.archivetv.ui.home

import com.archivetv.data.ListenCard
import com.archivetv.data.NextUp
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.BrowseRow
import com.archivetv.ui.common.RowState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val rowA = BrowseRow("a", "Row A", "q-a")
    private val rowB = BrowseRow("b", "Row B", "q-b")
    private fun item(id: String) = ArchiveItem(id, "Title $id")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        rows: List<BrowseRow> = listOf(rowA, rowB),
        loadRow: suspend (BrowseRow) -> List<ArchiveItem>,
        continueWatching: kotlinx.coroutines.flow.Flow<List<ArchiveItem>> = flowOf(emptyList()),
        lookup: suspend (List<String>) -> Map<String, ArchiveItem> = { emptyMap() },
        nextUp: kotlinx.coroutines.flow.Flow<List<NextUp>> = flowOf(emptyList()),
        continueListening: kotlinx.coroutines.flow.Flow<List<ListenCard>> = flowOf(emptyList()),
        heroCheck: suspend (ArchiveItem) -> HeroSlide? = { HeroSlide(it) },
        favorites: kotlinx.coroutines.flow.Flow<List<ArchiveItem>> = flowOf(emptyList()),
        hiddenIds: suspend (List<String>) -> Set<String> = { emptySet() },
        seasonalRows: List<BrowseRow> = emptyList(),
        seasonsNow: () -> List<BrowseRow> = { seasonalRows },
    ) = HomeViewModel(
        rows, loadRow, lookup, continueWatching, favorites, nextUp, continueListening, heroCheck, hiddenIds = hiddenIds,
        seasonalRows = seasonsNow,
    )

    @Test fun rowsStartLoadingAndPublishIndependently() = runTest {
        val gateA = CompletableDeferred<List<ArchiveItem>>()
        val gateB = CompletableDeferred<List<ArchiveItem>>()
        val model = vm(loadRow = { if (it.id == "a") gateA.await() else gateB.await() })

        assertThat(model.state.value.rows.map { it.state }).containsExactly(RowState.Loading, RowState.Loading)

        gateB.complete(listOf(item("b1")))
        assertThat(model.state.value.rows[0].state).isEqualTo(RowState.Loading)
        assertThat(model.state.value.rows[1].state).isEqualTo(RowState.Loaded(listOf(item("b1"))))
    }

    @Test fun failedRowIsMarkedAndCanBeRetried() = runTest {
        var calls = 0
        val model = vm(rows = listOf(rowA), loadRow = {
            calls++
            if (calls == 1) error("network") else listOf(item("a1"))
        })
        assertThat(model.state.value.rows[0].state).isEqualTo(RowState.Failed)

        model.retry(0)
        assertThat(model.state.value.rows[0].state).isEqualTo(RowState.Loaded(listOf(item("a1"))))
    }

    @Test fun heroComesFromFirstRowInConfigOrder() = runTest {
        val gateA = CompletableDeferred<List<ArchiveItem>>()
        val model = vm(loadRow = { if (it.id == "a") gateA.await() else listOf(item("b1")) })
        // Row B loaded first, but row A is still loading: hero waits for A.
        assertThat(model.state.value.heroSlides).isEmpty()
        gateA.complete(listOf(item("a1")))
        assertThat(model.state.value.heroSlides.map { it.item }).containsExactly(item("a1"))
    }

    @Test fun heroSkipsUnplayableAndKeepsRowOrderAndLimit() = runTest {
        val ids = (1..9).map { "a$it" }
        val gates = ids.associateWith { CompletableDeferred<Boolean>() }
        val model = vm(
            rows = listOf(rowA),
            loadRow = { ids.map(::item) },
            heroCheck = { if (gates.getValue(it.identifier).await()) HeroSlide(it) else null },
        )
        // a2 answers first, but a1 is still unchecked: nothing is published yet.
        gates.getValue("a2").complete(true)
        assertThat(model.state.value.heroSlides).isEmpty()
        gates.getValue("a1").complete(false)
        assertThat(model.state.value.heroSlides.map { it.item.identifier }).containsExactly("a2")
        ids.drop(2).forEach { gates.getValue(it).complete(true) }
        // At most 5 become slides.
        assertThat(model.state.value.heroSlides.map { it.item.identifier })
            .containsExactly("a2", "a3", "a4", "a5", "a6").inOrder()
    }

    @Test fun heroChecksOnlyTheFirstEightCandidates() = runTest {
        val checked = mutableListOf<String>()
        val model = vm(
            rows = listOf(rowA),
            loadRow = { (1..9).map { item("a$it") } },
            // a1..a5 have nothing playable, so a9 would be a slide if it were checked.
            heroCheck = { checked += it.identifier; if (it.identifier.drop(1).toInt() <= 5) null else HeroSlide(it) },
        )
        assertThat(checked).doesNotContain("a9")
        assertThat(model.state.value.heroSlides.map { it.item.identifier }).containsExactly("a6", "a7", "a8").inOrder()
    }

    @Test fun heroCheckFailureCountsAsUnplayable() = runTest {
        val model = vm(
            rows = listOf(rowA),
            loadRow = { listOf(item("a1"), item("a2")) },
            heroCheck = { if (it.identifier == "a1") error("down") else HeroSlide(it) },
        )
        assertThat(model.state.value.heroSlides.map { it.item.identifier }).containsExactly("a2")
    }

    @Test fun heroSkipsFailedAndEmptyRows() = runTest {
        val model = vm(
            rows = listOf(rowA, rowB, BrowseRow("c", "Row C", "q-c")),
            loadRow = {
                when (it.id) {
                    "a" -> error("boom")
                    "b" -> emptyList()
                    else -> listOf(item("c1"))
                }
            },
        )
        assertThat(model.state.value.heroSlides.map { it.item }).containsExactly(item("c1"))
    }

    @Test fun continueWatchingGetsTitlesFromLookupAndKeepsProgress() = runTest {
        val cw = MutableStateFlow(listOf(ArchiveItem("x", "x", progress = 0.4f)))
        val model = vm(
            loadRow = { emptyList() },
            continueWatching = cw,
            lookup = { ids -> ids.associateWith { ArchiveItem(it, "Real $it", year = "1950") } },
        )
        val first = model.state.value.continueWatching.single()
        assertThat(first.title).isEqualTo("Real x")
        assertThat(first.year).isEqualTo("1950")
        assertThat(first.progress).isEqualTo(0.4f)
    }

    @Test fun nextUpCardsCarryTitleAndEpisodeToPlay() = runTest {
        var lookups = 0
        val model = vm(
            loadRow = { emptyList() },
            continueWatching = flowOf(listOf(ArchiveItem("film", "film", progress = 0.3f))),
            nextUp = flowOf(listOf(NextUp("show", nextIndex = 3, fileCount = 21))),
            lookup = { ids -> lookups++; ids.associateWith { ArchiveItem(it, "Real $it") } },
        )
        val card = model.state.value.nextUp.single()
        assertThat(card.item.title).isEqualTo("Real show")
        assertThat(card.fileIndex).isEqualTo(3)
        assertThat(card.label).isEqualTo("Next: episode 4 of 21")
        assertThat(lookups).isEqualTo(2)
    }

    @Test fun continueWatchingFallsBackToIdWhenLookupFails() = runTest {
        val model = vm(
            loadRow = { emptyList() },
            continueWatching = flowOf(listOf(ArchiveItem("x", "x", progress = 0.5f))),
            lookup = { error("offline") },
        )
        assertThat(model.state.value.continueWatching.single().title).isEqualTo("x")
    }

    @Test fun continueListeningCardsAreTitledAndKeepProgress() = runTest {
        val model = vm(
            loadRow = { emptyList() },
            continueListening = flowOf(listOf(ListenCard("adventures_holmes", 2, 12, 0.4f, isNext = false))),
            lookup = { ids -> ids.associateWith { ArchiveItem(it, "Real $it") } },
        )
        val card = model.state.value.continueListening.single()
        assertThat(card.item.title).isEqualTo("Real adventures_holmes")
        assertThat(card.item.progress).isEqualTo(0.4f)
        assertThat(card.fileIndex).isEqualTo(2)
        assertThat(card.label).isEmpty()
    }

    @Test fun continueListeningNextCardSaysWhichFile() = runTest {
        val model = vm(
            loadRow = { emptyList() },
            continueListening = flowOf(listOf(ListenCard("otr", 5, 973, null, isNext = true))),
        )
        val card = model.state.value.continueListening.single()
        assertThat(card.fileIndex).isEqualTo(5)
        assertThat(card.label).isEqualTo("Next: 6 of 973")
    }

    @Test fun flaggedSavedItemsAreHidden() = runTest {
        val model = vm(
            rows = emptyList(),
            loadRow = { emptyList() },
            continueWatching = flowOf(listOf(item("a"), item("c"))),
            favorites = flowOf(listOf(item("a"), item("b"))),
            hiddenIds = { ids -> ids.filter { it == "a" }.toSet() },
        )
        assertThat(model.state.value.favorites.map { it.identifier }).containsExactly("b")
        assertThat(model.state.value.continueWatching.map { it.identifier }).containsExactly("c")
    }

    @Test fun seasonalRowsLoadSeparately() = runTest {
        val season = BrowseRow("season", "Season", "q-season", minItems = 8)
        val model = vm(
            loadRow = { row -> if (row.id == "season") (1..8).map { item("s$it") } else listOf(item(row.id)) },
            seasonalRows = listOf(season),
        )
        val loaded = model.state.value.seasonal.single().state
        assertThat(loaded).isEqualTo(RowState.Loaded((1..8).map { item("s$it") }))
        assertThat(model.state.value.rows.map { it.config.id }).doesNotContain("season")
    }

    @Test fun seasonsFollowTheDateWhenHomeComesBack() = runTest {
        val autumn = BrowseRow("autumn", "Autumn", "q-autumn", minItems = 8)
        val winter = BrowseRow("winter", "Winter", "q-winter", minItems = 8)
        var active = listOf(autumn)
        val model = vm(loadRow = { (1..8).map { n -> item("x$n") } }, seasonsNow = { active })
        assertThat(model.state.value.seasonal.map { it.config.id }).containsExactly("autumn")
        model.refreshSeasons()
        assertThat(model.state.value.seasonal.map { it.config.id }).containsExactly("autumn")
        active = listOf(winter)
        model.refreshSeasons()
        assertThat(model.state.value.seasonal.map { it.config.id }).containsExactly("winter")
        assertThat(model.state.value.heroSlides.map { it.item.identifier }).doesNotContain("winter")
    }
}

