package com.archivetv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteEntity::class, ResumeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun resumeDao(): ResumeDao
}

/**
 * v1 stored one position per item; v2 stores one per file. Old rows keep
 * their position under fileName "" and are read as the item's first file.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `resume_new` (
                `identifier` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `fileIndex` INTEGER NOT NULL,
                `fileCount` INTEGER NOT NULL,
                PRIMARY KEY(`identifier`, `fileName`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO resume_new (identifier, fileName, positionMs, durationMs, updatedAt, fileIndex, fileCount) " +
                "SELECT identifier, '', positionMs, durationMs, updatedAt, 0, 1 FROM resume"
        )
        db.execSQL("DROP TABLE resume")
        db.execSQL("ALTER TABLE resume_new RENAME TO resume")
    }
}
