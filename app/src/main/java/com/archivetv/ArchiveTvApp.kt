package com.archivetv

import android.app.Application
import androidx.room.Room
import com.archivetv.data.AppSettings
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.DerivedGroups
import com.archivetv.data.LibraryRepository
import com.archivetv.data.LiveMusicRepository
import com.archivetv.data.PlayableFileSelector
import com.archivetv.data.PrefsAppSettings
import com.archivetv.data.PinGate
import com.archivetv.data.RightsFilteredService
import com.archivetv.data.SavedFilter
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

    val settings: AppSettings = PrefsAppSettings(app)

    /** Unfiltered; used only for identifier lookups and collection-record listings. */
    private val rawService: InternetArchiveService = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(InternetArchiveService::class.java)

    /** Every other search goes through the rights filter. */
    private val service: InternetArchiveService = RightsFilteredService(
        rawService,
        openOnly = { settings.openOnly.value },
        hideAdult = { settings.hideAdult.value },
    )

    private val db = Room.databaseBuilder(
        app, AppDatabase::class.java, "archivetv.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    val queryBuilder = SearchQueryBuilder()

    val archiveRepository = ArchiveRepository(service, PlayableFileSelector(), queryBuilder, lookupService = rawService,
        hideAdult = { settings.hideAdult.value },
    )
    val liveMusic = LiveMusicRepository(service, rawService) { settings.openOnly.value }
    val derivedGroups = DerivedGroups(service, rawService) { settings.openOnly.value }
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

    /** Drops every in-memory search result, so screens reload under a changed rights setting. */
    /** Which saved items the parental filter hides (Home's saved rows, My List). */
    val savedFilter = SavedFilter(archiveRepository::flaggedIds) { settings.hideAdult.value }

    /** Wrong-PIN lockout, shared by every PIN prompt for the life of the app. */
    val pinGate = PinGate()

    suspend fun clearSessionCaches() {
        derivedGroups.clearCache()
        liveMusic.clearCache()
        savedFilter.clear()
    }
}
