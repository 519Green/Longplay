package com.archivetv

import android.app.Application
import androidx.room.Room
import com.archivetv.data.ArchiveRepository
import com.archivetv.data.LibraryRepository
import com.archivetv.data.PlayableFileSelector
import com.archivetv.data.SearchQueryBuilder
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.archiveMoshi
import com.archivetv.data.config.BrowseConfigLoader
import com.archivetv.data.db.AppDatabase
import com.archivetv.domain.BrowseRow
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
    ).build()

    val archiveRepository = ArchiveRepository(
        service, PlayableFileSelector(), SearchQueryBuilder()
    )
    val libraryRepository = LibraryRepository(db.favoriteDao(), db.resumeDao())

    val browseRows: List<BrowseRow> =
        app.assets.open("browse_config.json").bufferedReader().use {
            BrowseConfigLoader().parse(it.readText())
        }
}
