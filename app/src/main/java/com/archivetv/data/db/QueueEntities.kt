package com.archivetv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One file in the audio queue, in play order ([position] 0 first). Mirrors the player's playlist. */
@Entity(tableName = "queue_entry")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    val identifier: String,
    val fileName: String,
    val title: String,
    val itemTitle: String,
    val durationSec: Double?,
    /** "spoken" or "music". */
    val kind: String,
    val isLibriVox: Boolean,
    val fileIndex: Int,
    val fileCount: Int,
)

/** Where the queue was when last saved; a single row with [id] 0. [currentIndex] -1 means empty. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
)
