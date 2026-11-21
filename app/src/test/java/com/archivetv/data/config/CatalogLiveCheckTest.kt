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
}
