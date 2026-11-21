package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.domain.ItemKind
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

    @Test fun browseReturnsPageAndTotalAndSendsSortAndPage() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":57,"docs":[{"identifier":"id1","title":"Film One","year":"1950"}]}}"""
        ))
        val page = repo.browse("collection:(x)", BrowseSort.TRENDING, page = 3, rows = 10)
        assertThat(page.total).isEqualTo(57)
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("q")).isEqualTo("collection:(x)")
        assertThat(url.queryParameter("sort[]")).isEqualTo("week desc")
        assertThat(url.queryParameter("page")).isEqualTo("3")
        assertThat(url.queryParameter("rows")).isEqualTo("10")
        val items = page.items
        assertThat(items).hasSize(1)
        assertThat(items[0].identifier).isEqualTo("id1")
        assertThat(items[0].title).isEqualTo("Film One")
        assertThat(items[0].year).isEqualTo("1950")
    }

    @Test fun countAsksForZeroRows() = runTest {
        server.enqueue(MockResponse().setBody("""{"response":{"numFound":485,"docs":[]}}"""))
        assertThat(repo.count("q")).isEqualTo(485)
        assertThat(server.takeRequest().requestUrl!!.queryParameter("rows")).isEqualTo("0")
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
        assertThat(detail.files.single().fileName).isEqualTo("film.mp4")
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

    @Test fun itemsByIdsQueriesQuotedIdentifiersAndMapsById() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":2,"docs":[
                {"identifier":"b-2","title":"Second"},{"identifier":"a.1","title":"First","year":"1950"}]}}"""
        ))
        val map = repo.itemsByIds(listOf("a.1", "b-2"))
        assertThat(map["a.1"]?.title).isEqualTo("First")
        assertThat(map["a.1"]?.year).isEqualTo("1950")
        assertThat(map["b-2"]?.title).isEqualTo("Second")
        val q = server.takeRequest().requestUrl!!.queryParameter("q")
        assertThat(q).isEqualTo("identifier:(\"a.1\" OR \"b-2\")")
    }

    @Test fun itemsByIdsWithNoIdsMakesNoRequest() = runTest {
        assertThat(repo.itemsByIds(emptyList())).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
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

    @Test fun detailOfAudiobookIsSpokenAndLibriVox() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Holmes","mediatype":"audio","collection":["librivoxaudio","audio_bookspoetry"]},
                "files":[{"name":"ch01.mp3","format":"VBR MP3","source":"original"}]}"""
        ))
        val detail = repo.detail("adventures_holmes")
        assertThat(detail.kind).isEqualTo(ItemKind.SPOKEN)
        assertThat(detail.isLibriVox).isTrue()
        assertThat(detail.files.single().fileName).isEqualTo("ch01.mp3")
    }

    @Test fun detailOfSingleCollectionStringIsMusic() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Side","collection":"78rpm"},
                "files":[{"name":"side.mp3","format":"VBR MP3","source":"original"}]}"""
        ))
        val detail = repo.detail("side")
        assertThat(detail.kind).isEqualTo(ItemKind.MUSIC)
        assertThat(detail.isLibriVox).isFalse()
    }

    @Test fun detailOfFilmIsVideo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Film","collection":["feature_films"]},"files":[{"name":"f.mp4","format":"h.264"}]}"""
        ))
        assertThat(repo.detail("f").kind).isEqualTo(ItemKind.VIDEO)
    }
}
