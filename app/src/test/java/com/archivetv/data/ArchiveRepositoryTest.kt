package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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
}
