package com.archivetv.data.db

import androidx.room.*

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_entry ORDER BY position")
    suspend fun entries(): List<QueueEntryEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: QueueStateEntity)

    @Query("DELETE FROM queue_entry")
    suspend fun clearEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<QueueEntryEntity>)

    /** Replaces the whole queue and its state in one transaction. */
    @Transaction
    suspend fun replaceAll(entries: List<QueueEntryEntity>, state: QueueStateEntity) {
        clearEntries()
        insertEntries(entries)
        saveState(state)
    }
}
