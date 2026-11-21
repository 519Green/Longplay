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

/** Watched: at least 95% through a file of known length. */
fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0L && positionMs >= durationMs * 0.95

/** The next unwatched file of a multi-file item. */
data class NextUp(val identifier: String, val nextIndex: Int, val fileCount: Int)

/** Entries must be newest first (as the DAO returns them). */
private fun latestPerItem(entries: List<ResumeEntity>): List<ResumeEntity> =
    entries.distinctBy { it.identifier }

/** Video items whose most recent file is part-way through, newest first. */
fun continueWatching(entries: List<ResumeEntity>): List<ArchiveItem> =
    latestPerItem(entries.filter { it.kind == "video" })
        .filter { isResumable(it.positionMs, it.durationMs) }
        .map {
            ArchiveItem(
                identifier = it.identifier,
                title = it.identifier,
                progress = resumeFraction(it.positionMs, it.durationMs),
            )
        }

/** Video items whose most recent file was finished and that have more files after it. */
fun nextUp(entries: List<ResumeEntity>): List<NextUp> =
    latestPerItem(entries.filter { it.kind == "video" })
        .filter { isFinished(it.positionMs, it.durationMs) && it.fileIndex < it.fileCount - 1 }
        .map { NextUp(it.identifier, it.fileIndex + 1, it.fileCount) }

/** A Continue Listening card: resume [fileIndex] at [progress], or start it fresh when [isNext]. */
data class ListenCard(
    val identifier: String,
    val fileIndex: Int,
    val fileCount: Int,
    val progress: Float?,
    val isNext: Boolean,
)

/**
 * Spoken-word items (books, radio) to come back to, newest first: part-way
 * through a file, or finished one with more after it. Music is left out.
 */
fun continueListening(entries: List<ResumeEntity>): List<ListenCard> =
    latestPerItem(entries.filter { it.kind == "spoken" }).mapNotNull {
        when {
            isResumable(it.positionMs, it.durationMs) ->
                ListenCard(it.identifier, it.fileIndex, it.fileCount, resumeFraction(it.positionMs, it.durationMs), false)
            isFinished(it.positionMs, it.durationMs) && it.fileIndex < it.fileCount - 1 ->
                ListenCard(it.identifier, it.fileIndex + 1, it.fileCount, null, true)
            else -> null
        }
    }

/**
 * Progress per file name for one item. A legacy v1 row (fileName "") counts
 * as the first file unless that file has a row of its own.
 */
fun progressByFile(entries: List<ResumeEntity>, fileNames: List<String>): Map<String, ResumeEntity> {
    val byName = entries.filter { it.fileName.isNotEmpty() }.associateBy { it.fileName }
    val legacy = entries.firstOrNull { it.fileName.isEmpty() }
    val first = fileNames.firstOrNull()
    return if (legacy != null && first != null && first !in byName) byName + (first to legacy) else byName
}

enum class StartKind { BEGIN, RESUME, NEXT }

/** Where Play should start: file index, position, and why (for the button label). */
data class StartPoint(val index: Int, val positionMs: Long, val kind: StartKind)

/**
 * Play starts from the most recently watched file: resume it if part-way,
 * else the file after it if that exists, else the first file.
 */
fun startPoint(fileNames: List<String>, progress: Map<String, ResumeEntity>): StartPoint {
    val latest = progress.entries.maxByOrNull { it.value.updatedAt }
        ?: return StartPoint(0, 0L, StartKind.BEGIN)
    val index = fileNames.indexOf(latest.key)
    if (index < 0) return StartPoint(0, 0L, StartKind.BEGIN)
    val e = latest.value
    return when {
        isResumable(e.positionMs, e.durationMs) -> StartPoint(index, e.positionMs, StartKind.RESUME)
        isFinished(e.positionMs, e.durationMs) && index < fileNames.lastIndex ->
            StartPoint(index + 1, 0L, StartKind.NEXT)
        else -> StartPoint(0, 0L, StartKind.BEGIN)
    }
}

class LibraryRepository(
    private val favoriteDao: FavoriteDao,
    private val resumeDao: ResumeDao,
) {
    fun observeFavorites(): Flow<List<ArchiveItem>> =
        favoriteDao.observeAll().map { list ->
            list.map { ArchiveItem(it.identifier, it.title) }
        }

    fun observeContinueWatching(): Flow<List<ArchiveItem>> =
        resumeDao.observeAll().map(::continueWatching)

    fun observeNextUp(): Flow<List<NextUp>> =
        resumeDao.observeAll().map(::nextUp)

    fun observeContinueListening(): Flow<List<ListenCard>> =
        resumeDao.observeAll().map(::continueListening)

    /** Per-file progress for one item, keyed by file name. */
    fun observeProgress(id: String, fileNames: List<String>): Flow<Map<String, ResumeEntity>> =
        resumeDao.observeItem(id).map { progressByFile(it, fileNames) }

    suspend fun progress(id: String, fileNames: List<String>): Map<String, ResumeEntity> =
        progressByFile(resumeDao.forItem(id), fileNames)

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

    suspend fun saveResume(
        id: String,
        fileName: String,
        fileIndex: Int,
        fileCount: Int,
        positionMs: Long,
        durationMs: Long,
        kind: String = "video",
    ) {
        resumeDao.upsert(
            ResumeEntity(id, fileName, positionMs, durationMs, System.currentTimeMillis(), fileIndex, fileCount, kind)
        )
        // A real row now exists for this item; the migrated v1 row is superseded.
        resumeDao.delete(id, "")
    }
}
