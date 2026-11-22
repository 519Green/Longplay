package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.DeriveRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DerivedGroupsTest {
    private lateinit var server: MockWebServer
    private lateinit var groups: DerivedGroups

    private val shows = CategoryNode.Derived(
        "tv-shows", "Shows", DeriveRule.TitleSeries(listOf("classic_tv"), "movies", 1500, 4, 40),
    )
    private val sponsors = CategoryNode.Derived(
        "edu-sponsors", "Sponsored Films",
        DeriveRule.FieldValues("sponsor", listOf("prelinger", "avgeeks"), "movies", 5000, 2, 30), "decade",
    )
    private val radio = CategoryNode.Derived(
        "listen-radio-shows", "Radio Shows", DeriveRule.Subcollections("oldtimeradio", "audio", 40),
    )

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        groups = DerivedGroups(service)
    }

    @After fun tearDown() = server.shutdown()

    private fun respond(docs: String) =
        server.enqueue(MockResponse().setBody("""{"response":{"numFound":99,"docs":[$docs]}}"""))

    private val dragnet = (1..4).joinToString(",") { """{"title":"Dragnet - Case $it","downloads":10}""" }

    @Test fun titleSeriesRequestAndResult() = runTest {
        respond(dragnet + """,{"title":"Lassie - x","downloads":5}""")
        val entries = groups.entries(shows)
        assertThat(entries.map { it.label }).containsExactly("Dragnet")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("q")).isEqualTo("collection:(classic_tv) AND mediatype:movies")
        assertThat(url.queryParameter("rows")).isEqualTo("1500")
        assertThat(url.queryParameterValues("fl[]")).containsExactly("title", "downloads")
        assertThat(url.queryParameter("sort[]")).isEqualTo("downloads desc")
    }

    @Test fun fieldValuesUseTheRuleField() = runTest {
        respond("""{"sponsor":["Ford Motor Company","Bell System"]},{"sponsor":"Ford Motor Company"},{"sponsor":"Bell System"},{}""")
        val entries = groups.entries(sponsors)
        assertThat(entries.map { it.label }).containsExactly("Bell System", "Ford Motor Company")
        assertThat(entries.first { it.label == "Ford Motor Company" }.query.sponsorAny).containsExactly("Ford Motor Company")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("q")).isEqualTo("collection:(prelinger OR avgeeks) AND mediatype:movies")
        assertThat(url.queryParameterValues("fl[]")).containsExactly("sponsor")
        assertThat(url.queryParameter("rows")).isEqualTo("5000")
    }

    @Test fun subcollectionQuery() = runTest {
        respond("""{"identifier":"suspenseradio","title":"Suspense","downloads":50},{"identifier":"fav-x","title":"Favs","downloads":90}""")
        val entries = groups.entries(radio)
        assertThat(entries.map { it.key }).containsExactly("suspenseradio")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("q")).isEqualTo("collection:(oldtimeradio) AND mediatype:collection")
        assertThat(url.queryParameterValues("fl[]")).containsExactly("identifier", "title", "downloads")
        assertThat(url.queryParameter("rows")).isEqualTo("80")
    }

    @Test fun entriesAreCachedPerGroup() = runTest {
        respond(dragnet)
        val first = groups.entries(shows)
        val second = groups.entries(shows)
        assertThat(second).isEqualTo(first)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun failedLoadIsNotCached() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        respond(dragnet)
        try {
            groups.entries(shows)
            fail("expected the first load to throw")
        } catch (e: Exception) {
            // expected
        }
        assertThat(groups.entries(shows).map { it.label }).containsExactly("Dragnet")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun entryFindsByKey() = runTest {
        respond(dragnet)
        assertThat(groups.entry(shows, "dragnet")?.label).isEqualTo("Dragnet")
        assertThat(groups.entry(shows, "nope")).isNull()
    }
}
