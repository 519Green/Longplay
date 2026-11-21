package com.archivetv.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
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

    @Test fun resumeIsKeyedByItemAndFile() = runTest {
        val dao = db.resumeDao()
        dao.upsert(ResumeEntity("show", "e1.mp4", 1000L, 5000L, 1L, 0, 3))
        dao.upsert(ResumeEntity("show", "e2.mp4", 2000L, 5000L, 2L, 1, 3))
        dao.upsert(ResumeEntity("show", "e1.mp4", 4000L, 5000L, 3L, 0, 3))
        assertThat(dao.get("show", "e1.mp4")!!.positionMs).isEqualTo(4000L)
        assertThat(dao.forItem("show").map { it.fileName }).containsExactly("e1.mp4", "e2.mp4").inOrder()
        dao.delete("show", "e2.mp4")
        assertThat(dao.forItem("show")).hasSize(1)
    }

    @Test fun migrationFromV1KeepsResumeRowsAsFirstFile() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // Build a v1 database by hand with the v1 schema.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`identifier` TEXT NOT NULL, `title` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`identifier`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `resume` (`identifier` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`identifier`))")
                        db.execSQL("INSERT INTO resume VALUES ('film', 60000, 600000, 5)")
                        db.execSQL("INSERT INTO favorites VALUES ('film', 'Film', 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val rows = migrated.resumeDao().forItem("film")
            assertThat(rows).hasSize(1)
            assertThat(rows[0].fileName).isEqualTo("")
            assertThat(rows[0].positionMs).isEqualTo(60000L)
            assertThat(rows[0].fileCount).isEqualTo(1)
            assertThat(migrated.favoriteDao().isFavorite("film")).isTrue()
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
