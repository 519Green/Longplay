package com.archivetv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val identifier: String,
    val title: String,
    val addedAt: Long,
)
