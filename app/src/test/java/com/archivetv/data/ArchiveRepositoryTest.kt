package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.domain.ItemKind
import com.archivetv.domain.LiveInfo
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

    @Test fun searchAudioMarksAudioAndCommunityUploads() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":2,"docs":[
                {"identifier":"songs-of-leonard-cohen","title":"Songs Of Leonard Cohen","mediatype":"audio",
                 "collection":["hifidelity_potpourri","hifidelity"]},
                {"identifier":"adventures_holmes","title":"Holmes","mediatype":"audio","collection":"librivoxaudio"}]}}"""
        ))
        val items = repo.searchAudio("leonard cohen", limit = 40)
        assertThat(items.map { it.isAudio }).containsExactly(true, true)
        assertThat(items.map { it.isCommunity }).containsExactly(true, false).inOrder()
        assertThat(server.takeRequest().path).contains("mediatype%3Aaudio")
    }

    @Test fun videoSearchMarksCommunityVideo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"response":{"numFound":2,"docs":[
                {"identifier":"Thundarr","title":"Thundarr","mediatype":"movies","collection":["opensource_movies"]},
                {"identifier":"film","title":"Film","mediatype":"movies","collection":["feature_films"]}]}}"""
        ))
        val items = repo.search("thundarr", limit = 40)
        assertThat(items.map { it.isCommunity }).containsExactly(true, false).inOrder()
        assertThat(items.none { it.isAudio }).isTrue()
    }

    @Test fun detailOfLmaRecordingHasLiveInfo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Grateful Dead Live at Barton Hall on 1977-05-08","mediatype":"etree",""" +
                """"collection":["GratefulDead","etree","stream_only"],"date":"1977-05-08","venue":"Barton Hall","coverage":"Ithaca, NY"},""" +
                """"files":[{"name":"t1.mp3","format":"VBR MP3","title":"Minglewood Blues","track":"1"}]}"""
        ))
        val d = repo.detail("gd77-05-08")
        assertThat(d.live).isEqualTo(LiveInfo("GratefulDead", "1977-05-08", "", "Barton Hall", "Ithaca, NY"))
    }

    @Test fun detailWithoutArtistCollectionHasNoLiveInfo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Orphan","mediatype":"etree","collection":["etree","stream_only"],"date":"1977-05-08"},"files":[]}"""
        ))
        assertThat(repo.detail("orphan").live).isNull()
    }

    @Test fun detailOfNonLmaAudioHasNoLiveInfo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Holmes","mediatype":"audio","collection":["librivoxaudio"],"date":"2008-01-01"},"files":[]}"""
        ))
        assertThat(repo.detail("holmes").live).isNull()
    }

    @Test fun detailParsesFilesWhoseOriginalIsAList() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Show","mediatype":"etree","collection":["SomeBand","etree"],"date":"1977-05-08"},""" +
                """"files":[{"name":"t1.flac","format":"Flac","source":"original"},""" +
                """{"name":"t1.mp3","format":"VBR MP3","source":"derivative","original":["t1.flac","t1.ffp"],"title":"Song","track":"1"}]}"""
        ))
        val d = repo.detail("show")
        assertThat(d.files.map { it.fileName }).containsExactly("t1.mp3")
    }

    @Test fun detailBuildsFactsFromReviewsLengthsAndTags() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Night Film (1949)","year":"2025","sound":"silent","color":"black and white",
                "collection":["Film_Noir","feature_films"]},
                "reviews":[{"stars":"5"},{"stars":4},{"stars":"x"}],
                "files":[{"name":"film.mp4","format":"h.264","length":"5520"}]}"""
        ))
        val d = repo.detail("id1")
        assertThat(d.facts.year).isEqualTo("1949")
        assertThat(d.facts.runtimeSec).isEqualTo(5520.0)
        assertThat(d.facts.totalRuntime).isFalse()
        assertThat(d.facts.rating).isEqualTo(4.5)
        assertThat(d.facts.reviews).isEqualTo(2)
        assertThat(d.facts.tags).containsExactly("Silent", "B&W").inOrder()
        assertThat(d.collections).containsExactly("Film_Noir", "feature_films").inOrder()
    }

    @Test fun detailToleratesReviewsThatAreNotAList() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Film"},"reviews":{"odd":true},"files":[]}"""
        ))
        val d = repo.detail("id1")
        assertThat(d.facts.rating).isNull()
        assertThat(d.facts.reviews).isEqualTo(0)
    }
}
