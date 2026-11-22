package com.archivetv.ui.detail

import com.archivetv.data.ArchiveRepository
import com.archivetv.data.LibraryRepository
import com.archivetv.data.PlayableFileSelector
import com.archivetv.data.SearchQueryBuilder
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.archivetv.data.db.FavoriteDao
import com.archivetv.data.db.FavoriteEntity
import com.archivetv.data.db.ResumeDao
import com.archivetv.data.db.ResumeEntity
import com.archivetv.domain.Recording
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private class NoFavorites : FavoriteDao {
    override fun observeAll(): Flow<List<FavoriteEntity>> = flowOf(emptyList())
    override suspend fun isFavorite(id: String) = false
    override suspend fun add(entity: FavoriteEntity) = Unit
    override suspend fun remove(id: String) = Unit
}

private class NoResume : ResumeDao {
    override fun observeAll(): Flow<List<ResumeEntity>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<List<ResumeEntity>> = flowOf(emptyList())
    override suspend fun forItem(id: String) = emptyList<ResumeEntity>()
    override suspend fun get(id: String, fileName: String): ResumeEntity? = null
    override suspend fun upsert(entity: ResumeEntity) = Unit
    override suspend fun delete(id: String, fileName: String) = Unit
    override suspend fun clear(id: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailOtherRecordingsTest {
    private lateinit var server: MockWebServer
    private lateinit var archive: ArchiveRepository
    private val library = LibraryRepository(NoFavorites(), NoResume())

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(archiveMoshi()))
            .build()
            .create(InternetArchiveService::class.java)
        archive = ArchiveRepository(service, PlayableFileSelector(), SearchQueryBuilder())
    }

    @After fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun rec(id: String) = Recording(
        identifier = id, title = "t", date = "1977-05-08", showPart = "", venue = null, city = null,
        source = "Soundboard", rating = 4.5, reviews = 3, downloads = 1, artistId = "GratefulDead", artistName = "Grateful Dead",
    )

    @Test fun otherRecordingsExcludeCurrent() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Grateful Dead Live at Barton Hall on 1977-05-08","mediatype":"etree",""" +
                """"collection":["GratefulDead","etree"],"date":"1977-05-08"},"files":[]}"""
        ))
        val asked = mutableListOf<Triple<String, String, String>>()
        val vm = DetailViewModel(archive, library, recordings = { a, d, p ->
            asked += Triple(a, d, p)
            listOf(rec("other1"), rec("current"), rec("other2"))
        })
        vm.load("current")
        // The metadata request runs on OkHttp's thread; wait for the state it produces.
        vm.state.first { it.otherRecordings.isNotEmpty() }
        assertThat(asked).containsExactly(Triple("GratefulDead", "1977-05-08", ""))
        assertThat(vm.state.value.live?.artistId).isEqualTo("GratefulDead")
        assertThat(vm.state.value.otherRecordings.map { it.identifier }).containsExactly("other1", "other2").inOrder()
    }

    @Test fun noLiveInfoMeansNoRecordingsRequest() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Holmes","mediatype":"audio","collection":["librivoxaudio"]},"files":[]}"""
        ))
        var calls = 0
        val vm = DetailViewModel(archive, library, recordings = { _, _, _ -> calls++; emptyList() })
        vm.load("holmes")
        vm.state.first { it.item != null }
        assertThat(vm.state.value.item?.title).isEqualTo("Holmes")
        assertThat(calls).isEqualTo(0)
        assertThat(vm.state.value.otherRecordings).isEmpty()
    }

    @Test fun failedRecordingsLookupLeavesPageUsable() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"GD","mediatype":"etree","collection":["GratefulDead","etree"],"date":"1977-05-08"},"files":[]}"""
        ))
        var called = false
        val vm = DetailViewModel(archive, library, recordings = { _, _, _ -> called = true; error("down") })
        vm.load("current")
        vm.state.first { it.item != null }
        while (!called) Thread.sleep(5)
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.item).isNotNull()
        assertThat(vm.state.value.otherRecordings).isEmpty()
    }

    @Test fun moreLikeThisUsesFirstSpecificCollectionAndMediatype() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"A Film","collection":["moviesandfilms","Comedy_Films"]},""" +
                """"files":[{"name":"a.mp4","format":"h.264"}]}"""
        ))
        var asked: Triple<String, String, String>? = null
        val vm = DetailViewModel(archive, library, moreLike = { c, m, e ->
            asked = Triple(c, m, e)
            listOf(com.archivetv.domain.ArchiveItem("other", "Other"))
        })
        vm.load("film1")
        vm.state.first { it.moreLikeThis.isNotEmpty() }
        assertThat(asked).isEqualTo(Triple("Comedy_Films", "movies", "film1"))
        assertThat(vm.state.value.unavailableReason).isNull()
    }

    @Test fun noMoreLikeThisForLiveRecordingsAndReasonWhenUnplayable() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"metadata":{"title":"Show","mediatype":"etree","collection":["SomeBand","etree"],"date":"1977-05-08"},"files":[]}"""
        ))
        var calls = 0
        val vm = DetailViewModel(archive, library, recordings = { _, _, _ -> emptyList() }, moreLike = { _, _, _ -> calls++; emptyList() })
        vm.load("rec")
        vm.state.first { it.item != null }
        assertThat(calls).isEqualTo(0)
        assertThat(vm.state.value.unavailableReason).isEqualTo("No playable audio file in this item")
    }
}
