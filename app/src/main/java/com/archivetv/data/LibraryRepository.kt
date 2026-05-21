package com.archivetv.data

import com.archivetv.data.db.FavoriteDao
import com.archivetv.data.db.FavoriteEntity
import com.archivetv.data.db.ResumeDao
import com.archivetv.data.db.ResumeEntity
import com.archivetv.domain.ArchiveItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun resumeFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/** Resumable when between 5s and 95% watched. */
fun isResumable(positionMs: Long, durationMs: Long): Boolean =
    positionMs >= 5_000L && (durationMs <= 0L || positionMs < durationMs * 0.95)

class LibraryRepository(
    private val favoriteDao: FavoriteDao,
    private val resumeDao: ResumeDao,
) {
    fun observeFavorites(): Flow<List<ArchiveItem>> =
        favoriteDao.observeAll().map { list ->
            list.map { ArchiveItem(it.identifier, it.title) }
        }

    fun observeContinueWatching(): Flow<List<ArchiveItem>> =
        resumeDao.observeAll().map { list ->
            list.filter { isResumable(it.positionMs, it.durationMs) }
                .map { ArchiveItem(it.identifier, it.identifier) }
        }

    suspend fun isFavorite(id: String): Boolean = favoriteDao.isFavorite(id)

    suspend fun toggleFavorite(item: ArchiveItem) {
        if (favoriteDao.isFavorite(item.identifier)) {
            favoriteDao.remove(item.identifier)
        } else {
            favoriteDao.add(
                FavoriteEntity(item.identifier, item.title, System.currentTimeMillis())
            )
        }
    }

    suspend fun resumePosition(id: String): Long = resumeDao.get(id)?.positionMs ?: 0L

    suspend fun saveResume(id: String, positionMs: Long, durationMs: Long) {
        resumeDao.upsert(
            ResumeEntity(id, positionMs, durationMs, System.currentTimeMillis())
        )
    }
}
