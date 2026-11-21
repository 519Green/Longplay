package com.archivetv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ResumeEntity>>

    @Query("SELECT * FROM resume WHERE identifier = :id ORDER BY updatedAt DESC")
    fun observeItem(id: String): Flow<List<ResumeEntity>>

    @Query("SELECT * FROM resume WHERE identifier = :id ORDER BY updatedAt DESC")
    suspend fun forItem(id: String): List<ResumeEntity>

    @Query("SELECT * FROM resume WHERE identifier = :id AND fileName = :fileName")
    suspend fun get(id: String, fileName: String): ResumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResumeEntity)

    @Query("DELETE FROM resume WHERE identifier = :id AND fileName = :fileName")
    suspend fun delete(id: String, fileName: String)

    @Query("DELETE FROM resume WHERE identifier = :id")
    suspend fun clear(id: String)
}
