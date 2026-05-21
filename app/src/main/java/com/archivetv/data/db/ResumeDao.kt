package com.archivetv.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ResumeEntity>>

    @Query("SELECT * FROM resume WHERE identifier = :id")
    suspend fun get(id: String): ResumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResumeEntity)

    @Query("DELETE FROM resume WHERE identifier = :id")
    suspend fun clear(id: String)
}
