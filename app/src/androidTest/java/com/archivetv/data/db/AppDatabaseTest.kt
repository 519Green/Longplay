package com.archivetv.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun favoriteAddRemoveRoundTrips() = runTest {
        val dao = db.favoriteDao()
        dao.add(FavoriteEntity("id1", "Film One", 100L))
        assertThat(dao.isFavorite("id1")).isTrue()
        assertThat(dao.observeAll().first()).hasSize(1)
        dao.remove("id1")
        assertThat(dao.isFavorite("id1")).isFalse()
    }

    @Test fun resumeUpsertOverwrites() = runTest {
        val dao = db.resumeDao()
        dao.upsert(ResumeEntity("id1", 1000L, 5000L, 1L))
        dao.upsert(ResumeEntity("id1", 2000L, 5000L, 2L))
        assertThat(dao.get("id1")!!.positionMs).isEqualTo(2000L)
    }
}
