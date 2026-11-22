package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.archivetv.domain.YearCount
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class LiveMusicRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: LiveMusicRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        repo = LiveMusicRepository(service)
    }

    @After fun tearDown() = server.shutdown()

    private fun respond(docs: String) =
        server.enqueue(MockResponse().setBody("""{"response":{"numFound":99,"docs":[$docs]}}"""))

    private fun doc(id: String, date: String?, extra: String = "") =
        """{"identifier":"$id","title":"Grateful Dead Live at Barton Hall on x"""" +
            (date?.let { ""","date":"$it"""" } ?: "") +
            ""","collection":["GratefulDead","etree","stream_only"]$extra}"""

    @Test fun concertsGroupAndSortByDate() = runTest {
        respond(
            listOf(
                doc("a", "1977-05-08T00:00:00Z", ""","venue":"Barton Hall","coverage":"Ithaca, NY","avg_rating":4.9,"num_reviews":29,"downloads":10"""),
                doc("b", "1977-05-07T00:00:00Z", ""","venue":"Boston Garden","avg_rating":4.5,"num_reviews":3,"downloads":5"""),
                doc("c", "1977-05-08T00:00:00Z", ""","downloads":7"""),
            ).joinToString(","),
        )
        val concerts = repo.concerts("GratefulDead", 1977)
        assertThat(concerts.map { it.date }).containsExactly("1977-05-07", "1977-05-08").inOrder()
        val cornell = concerts[1]
        assertThat(cornell.recordingCount).isEqualTo(2)
        assertThat(cornell.best.identifier).isEqualTo("a")
        assertThat(cornell.venue).isEqualTo("Barton Hall")
        assertThat(cornell.city).isEqualTo("Ithaca, NY")
        assertThat(cornell.artistName).isEqualTo("Grateful Dead")
        val c = concerts[1].best
        assertThat(c.rating).isEqualTo(4.9)
        val path = server.takeRequest().path!!
        assertThat(path).contains("year%3A1977")
        assertThat(path).contains("rows=2000")
    }

    @Test fun missingFieldsParse() = runTest {
        respond(doc("c", "1977-05-08T00:00:00Z"))
        val r = repo.concerts("GratefulDead", 1977).single().best
        assertThat(r.venue).isNull()
        assertThat(r.city).isNull()
        assertThat(r.rating).isNull()
        assertThat(r.reviews).isEqualTo(0)
        assertThat(r.downloads).isEqualTo(0)
    }

    @Test fun yearsCountDistinctDates() = runTest {
        respond("""{"date":"1977-05-08T00:00:00Z"},{"date":"1977-05-08T00:00:00Z"},{"date":"1977-05-09T00:00:00Z"},{"date":"1978-01-01T00:00:00Z"},{}""")
        assertThat(repo.years("GratefulDead")).containsExactly(YearCount(1977, 2), YearCount(1978, 1)).inOrder()
        val path = server.takeRequest().path!!
        assertThat(path).contains("fl%5B%5D=date")
        assertThat(path).contains("rows=25000")
    }

    @Test fun undatedRecordingsAreDropped() = runTest {
        respond(doc("a", "1977-05-08T00:00:00Z") + "," + doc("nodate", null))
        val concerts = repo.concerts("GratefulDead", 1977)
        assertThat(concerts.map { it.best.identifier }).containsExactly("a")
        assertThat(concerts.single().recordingCount).isEqualTo(1)
    }

    @Test fun artistsAreFetchedOnce() = runTest {
        respond("""{"identifier":"moe","title":"moe."},{"identifier":"Trail","title":"...And You Will Know Us &amp; Co"},{"identifier":"Zz","title":" Zydeco "}""")
        val first = repo.artists()
        val second = repo.artists()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(second).isEqualTo(first)
        assertThat(first.map { it.name }).containsExactly("...And You Will Know Us & Co", "moe.", "Zydeco").inOrder()
    }

    @Test fun startYearReadsMostDownloaded() = runTest {
        respond("""{"date":"1977-05-08T00:00:00Z"}""")
        assertThat(repo.startYear("GratefulDead")).isEqualTo(1977)
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("sort[]")).isEqualTo("downloads desc")
        assertThat(url.queryParameter("rows")).isEqualTo("1")
    }

    @Test fun recordingsKeepOnlyTheShowPart() = runTest {
        respond(
            """{"identifier":"e1","title":"Grateful Dead Live at Fillmore East on 1970-01-03 (Early Show)","date":"1970-01-03T00:00:00Z","collection":["GratefulDead","etree"]},""" +
                """{"identifier":"l1","title":"Grateful Dead Live at Fillmore East on 1970-01-03 (Late Show)","date":"1970-01-03T00:00:00Z","collection":["GratefulDead","etree"]}""",
        )
        assertThat(repo.recordings("GratefulDead", "1970-01-03", "early").map { it.identifier }).containsExactly("e1")
    }

    @Test fun searchConcertsSkipsRequestForEmptyText() = runTest {
        assertThat(repo.searchConcerts("()")).isEmpty()
        assertThat(repo.searchArtists("  ")).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun recordingRequestsSkipTheHugeCollectionField() = runTest {
        respond("")
        repo.mostPlayed()
        val url = server.takeRequest().requestUrl!!
        val fields = url.queryParameterValues("fl[]")
        assertThat(fields).doesNotContain("collection")
        assertThat(fields).contains("creator")
    }

    @Test fun discoveryRowsGroupByCreatorWithoutCollections() = runTest {
        respond(
            """{"identifier":"a1","title":"Some Band Live at Hall on 1990-01-01","creator":"Some Band","date":"1990-01-01T00:00:00Z","downloads":9},""" +
                """{"identifier":"a2","title":"Some Band Live at Hall on 1990-01-01","creator":"Some Band","date":"1990-01-01T00:00:00Z","downloads":5},""" +
                """{"identifier":"b1","title":"Other Live at Hall on 1990-01-01","creator":"Other","date":"1990-01-01T00:00:00Z"},""" +
                """{"identifier":"c1","title":"Odd title format","date":"1990-01-01T00:00:00Z"}""",
        )
        val concerts = repo.mostPlayed()
        assertThat(concerts.map { it.artistName }).containsExactly("Some Band", "Other").inOrder()
        assertThat(concerts.first().recordingCount).isEqualTo(2)
    }

    @Test fun artistNameComesFromCreator() = runTest {
        respond("""{"identifier":"x","title":"Odd title","creator":"Tea Leaf Green","date":"2002-11-21T00:00:00Z"}""")
        val c = repo.concerts("TeaLeafGreen", 2002).single()
        assertThat(c.artistName).isEqualTo("Tea Leaf Green")
        assertThat(c.artistId).isEqualTo("TeaLeafGreen")
    }
}
