package com.archivetv.data.config

import com.archivetv.data.SearchQueryBuilder
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

/**
 * Network check of the bundled catalog against the live Internet Archive.
 * Skipped unless ARCHIVETV_LIVE_CHECK is set, so normal test runs stay offline:
 *
 *   ARCHIVETV_LIVE_CHECK=1 ./gradlew :app:testDebugUnitTest --tests '*CatalogLiveCheckTest*'
 *
 * Prints each leaf's result count and top titles (stdout lands in the test
 * report) and fails if any leaf returns nothing; that is how two dead rows
 * once shipped unnoticed.
 */
class CatalogLiveCheckTest {
    @Test fun everyLeafReturnsResults() = runBlocking {
        assumeTrue(System.getenv("ARCHIVETV_LIVE_CHECK") != null)
        val catalog = CatalogLoader().parse(File("src/main/assets/browse_config.json").readText())
        val service = Retrofit.Builder()
            .baseUrl("https://archive.org/")
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        val render = SearchQueryBuilder()::render

        val empty = mutableListOf<String>()
        for (leaf in catalog.allLeaves.sortedBy { it.id }) {
            val body = service.search(render(leaf.query, null), rows = 5, sort = "downloads desc").response
            val titles = body.docs.joinToString(" | ") { it.title.firstOrNull().orEmpty().take(40) }
            println("LIVE %-28s %7d  %s".format(leaf.id, body.numFound, titles))
            if (body.numFound == 0) empty += leaf.id
        }
        assertWithMessage("leaves with no results").that(empty).isEmpty()
    }

    /** Episode lists for known multi-file items: counts and first few names in play order. */
    @Test fun multiFileItemsListEpisodesInOrder() = runBlocking {
        assumeTrue(System.getenv("ARCHIVETV_LIVE_CHECK") != null)
        val service = Retrofit.Builder()
            .baseUrl("https://archive.org/")
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        val selector = com.archivetv.data.PlayableFileSelector()
        val ids = listOf(
            "Thundarr_the_Barbarian", "TheStranger_0", "his_girl_friday",
            "GreenAcresCompleteSeries",
        )
        for (id in ids) {
            val files = selector.selectAll(id, service.metadata(id).files)
            println("EPISODES %-28s %4d  %s".format(id, files.size, listOf(0, 1, 2, files.size / 2, files.size - 1).filter { it in files.indices }.distinct().map { files[it] }.joinToString(" | ") { it.title.take(38) }))
        }
    }

    /** Live Music discovery rows and one known artist year return concerts. */
    @Test fun everyLiveQueryReturnsResults() = runBlocking {
        assumeTrue(System.getenv("ARCHIVETV_LIVE_CHECK") != null)
        val service = Retrofit.Builder()
            .baseUrl("https://archive.org/")
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        val live = com.archivetv.data.LiveMusicRepository(service)
        val today = java.util.Calendar.getInstance()
        val month = today.get(java.util.Calendar.MONTH) + 1
        val day = today.get(java.util.Calendar.DAY_OF_MONTH)
        val counts = linkedMapOf(
            "thisDay" to live.thisDay(month, day).also { println("LIVE thisDay first: ${it.firstOrNull()}") }.size,
            "topRated" to live.topRated().also { println("LIVE topRated first: ${it.firstOrNull()}") }.size,
            "mostPlayed" to live.mostPlayed().size,
            "popularArtists" to live.popularArtists().also { println("LIVE popular: ${it.take(5)}") }.size,
            "concerts GratefulDead 1977" to live.concerts("GratefulDead", 1977).size,
            "searchConcerts" to live.searchConcerts("grateful dead").size,
        )
        counts.forEach { (k, v) -> println("LIVE %-28s %5d".format(k, v)) }
        assertWithMessage("live queries with no results").that(counts.filterValues { it == 0 }.keys).isEmpty()
    }
}
