package com.archivetv.data

import android.content.Context

/** The last few searches, newest first. */
interface RecentSearches {
    fun load(): List<String>
    /** Records [query] and returns the updated list. */
    fun add(query: String): List<String>

    class InMemory(initial: List<String> = emptyList()) : RecentSearches {
        private var list = initial
        override fun load() = list
        override fun add(query: String) = withRecent(list, query).also { list = it }
    }
}

const val RECENT_SEARCH_LIMIT = 8

/** [query] moves to the front; a case-insensitive duplicate is dropped; at most [limit] kept. */
fun withRecent(list: List<String>, query: String, limit: Int = RECENT_SEARCH_LIMIT): List<String> {
    val q = query.trim().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return list
    return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(limit)
}

/** Stored in SharedPreferences: a per-device convenience, not worth a Room table. */
class PrefsRecentSearches(context: Context) : RecentSearches {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    override fun load(): List<String> =
        prefs.getString(KEY, null).orEmpty().split('\n').filter { it.isNotBlank() }

    override fun add(query: String): List<String> {
        val updated = withRecent(load(), query)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
        return updated
    }

    private companion object { const val KEY = "recent" }
}
