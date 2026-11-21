package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.google.common.truth.Truth.assertThat
import com.archivetv.data.api.archiveMoshi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ArchiveRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: ArchiveRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val moshi = archiveMoshi()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(InternetArchiveService::class.java)
        repo = ArchiveRepository(service, PlayableFileSelector(), SearchQueryBuilder())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun browseCollectionMapsDocsToItems() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":1,"docs":[{"identifier":"id1","title":"Film One","year":"1950"}]}}"""
        ))
        val items = repo.browseCollection("feature_films", limit = 10)
        assertThat(items).hasSize(1)
        assertThat(items[0].identifier).isEqualTo("id1")
        assertThat(items[0].title).isEqualTo("Film One")
        assertThat(items[0].year).isEqualTo("1950")
    }

    @Test fun searchMapsDocsToItems() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":1,"docs":[{"identifier":"id2","title":"Found"}]}}"""
        ))
        val items = repo.search("metropolis", limit = 10)
        assertThat(items.single().identifier).isEqualTo("id2")
    }

    @Test fun detailReturnsItemAndPlayableFile() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Film One","description":"desc","year":"1950"},
                "files":[{"name":"film.mp4","format":"h.264"}]}"""
        ))
        val detail = repo.detail("id1")
        assertThat(detail.item.title).isEqualTo("Film One")
        assertThat(detail.item.description).isEqualTo("desc")
        assertThat(detail.playable?.fileName).isEqualTo("film.mp4")
    }

    @Test fun detailHandlesArrayFieldsAndHtmlDescription() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":["Tom &amp; Jerry","Alt title"],
                "description":["<p>First&nbsp;part.</p>","Second<br/>line"],
                "year":["1940"]},
                "files":[]}"""
        ))
        val detail = repo.detail("id1")
        assertThat(detail.item.title).isEqualTo("Tom & Jerry")
        assertThat(detail.item.description).isEqualTo("First part.\n\nSecond\nline")
        assertThat(detail.item.year).isEqualTo("1940")
    }

    @Test fun searchHandlesArrayTitleAndNumericYear() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":1,"docs":[{"identifier":"id3","title":["Show","Other"],"year":1955}]}}"""
        ))
        val item = repo.search("show", limit = 10).single()
        assertThat(item.title).isEqualTo("Show")
        assertThat(item.year).isEqualTo("1955")
    }

    @Test fun searchWithOnlySyntaxMakesNoRequest() = runTest {
        assertThat(repo.search(" :() ", limit = 10)).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun blankTitleFallsBackToIdentifier() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":1,"docs":[{"identifier":"id4","title":"  "}]}}"""
        ))
        assertThat(repo.search("x", limit = 10).single().title).isEqualTo("id4")
    }
}
