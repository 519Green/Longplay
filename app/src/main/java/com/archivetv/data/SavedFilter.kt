package com.archivetv.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Which saved identifiers (Continue Watching, Next Up, Continue Listening, My List) the parental filter hides.
 * Each identifier is looked up once per session; a failed lookup hides nothing and is retried next time, so an
 * offline TV keeps its rows instead of emptying them.
 */
class SavedFilter(
    private val flagged: suspend (List<String>) -> Set<String>,
    private val enabled: () -> Boolean,
) {
    private val lock = Mutex()
    private val known = mutableMapOf<String, Boolean>()

    suspend fun hidden(ids: List<String>): Set<String> {
        if (!enabled() || ids.isEmpty()) return emptySet()
        return lock.withLock {
            val missing = ids.filter { it !in known }.distinct()
            if (missing.isNotEmpty()) {
                try {
                    val flaggedNow = flagged(missing)
                    missing.forEach { known[it] = it in flaggedNow }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Offline or a server error: show the rows; ask again next time.
                }
            }
            ids.filterTo(HashSet()) { known[it] == true }
        }
    }

    /** Forgets every answer (the setting changed, or a new session). */
    fun clear() {
        known.clear()
    }
}
