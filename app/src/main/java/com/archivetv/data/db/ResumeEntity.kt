package com.archivetv.data.db

import androidx.room.Entity

/**
 * Playback position for one file of an item. [fileName] is "" for rows
 * migrated from schema v1, which stored one position per item (its first file).
 * [fileIndex]/[fileCount] let Next Up be computed without a network call.
 * [kind] is "video", "spoken" or "music" (schema v3; older rows are video).
 */
@Entity(tableName = "resume", primaryKeys = ["identifier", "fileName"])
data class ResumeEntity(
    val identifier: String,
    val fileName: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val fileIndex: Int = 0,
    val fileCount: Int = 1,
    val kind: String = "video",
)
