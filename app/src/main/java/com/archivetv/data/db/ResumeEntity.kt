package com.archivetv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resume")
data class ResumeEntity(
    @PrimaryKey val identifier: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
