package com.archivetv

import android.app.Application
import androidx.room.Room
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.DerivedGroups
import com.archivetv.data.LibraryRepository
import com.archivetv.data.LiveMusicRepository
import com.archivetv.data.PlayableFileSelector
import com.archivetv.data.SearchQueryBuilder
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.archivetv.data.config.CatalogLoader
import com.archivetv.domain.Catalog
import com.archivetv.domain.CategoryNode
import com.archivetv.data.db.AppDatabase
import com.archivetv.data.db.MIGRATION_1_2
import com.archivetv.data.db.MIGRATION_2_3
import com.archivetv.domain.BrowseRow
import com.archivetv.playback.AudioConnection
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ArchiveTvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val moshi = archiveMoshi()

    private val service: InternetArchiveService = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(InternetArchiveService::class.java)

    private val db = Room.databaseBuilder(
        app, AppDatabase::class.java, "archivetv.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    val queryBuilder = SearchQueryBuilder()

    val archiveRepository = ArchiveRepository(service, PlayableFileSelector(), queryBuilder)
    val liveMusic = LiveMusicRepository(service)
    val derivedGroups = DerivedGroups(service)
    val libraryRepository = LibraryRepository(db.favoriteDao(), db.resumeDao())
    val queueDao = db.queueDao()

    /** The audio player, shared by every screen; connected while MainActivity is started. */
    val audio = AudioConnection(app, archiveRepository::detail, libraryRepository::progress)

    val catalog: Catalog =
        app.assets.open("browse_config.json").bufferedReader().use {
            CatalogLoader().parse(it.readText())
        }

    fun rowFor(leaf: CategoryNode.Leaf) = BrowseRow(leaf.id, leaf.title, queryBuilder.render(leaf.query))

    val homeRows: List<BrowseRow> = catalog.home.map(::rowFor)
}
