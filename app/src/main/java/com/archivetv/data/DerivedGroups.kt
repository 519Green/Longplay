package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CollectionDoc
import com.archivetv.domain.DeriveRule
import com.archivetv.domain.DerivedEntry
import com.archivetv.domain.TitleDoc
import com.archivetv.domain.seriesEntries
import com.archivetv.domain.subcollectionEntries
import com.archivetv.domain.valueEntries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds derived catalog groups live from IA metadata. Each group is fetched
 * once per app session; a failed load is not kept, so a retry fetches again.
 * [service] is the rights-filtered service. Sub-collection records carry no license, so they are listed from
 * [raw]; when [openOnly] is on, only sub-collections holding at least one passing item are kept.
 */
class DerivedGroups(
    private val service: InternetArchiveService,
    private val raw: InternetArchiveService = service,
    private val openOnly: () -> Boolean = { false },
) {
    private val builder = SearchQueryBuilder()
    private val lock = Mutex()
    private val cache = mutableMapOf<String, List<DerivedEntry>>()

    suspend fun entries(node: CategoryNode.Derived): List<DerivedEntry> = lock.withLock {
        cache[node.id] ?: load(node.rule).also { cache[node.id] = it }
    }

    /** Drops every cached group, so the next calls fetch again (after the rights setting changes). */
    suspend fun clearCache() = lock.withLock { cache.clear() }

    suspend fun entry(node: CategoryNode.Derived, key: String): DerivedEntry? =
        entries(node).firstOrNull { it.key == key }

    private suspend fun load(rule: DeriveRule): List<DerivedEntry> = when (rule) {
        is DeriveRule.TitleSeries -> {
            val docs = fetch(sampleQuery(rule.collections, rule.mediatype), listOf("title", "downloads"), rule.sample)
            seriesEntries(
                docs.mapNotNull { d -> d.title.firstOrNull { it.isNotBlank() }?.let { TitleDoc(HtmlText.toPlain(it), d.downloads ?: 0) } },
                rule,
            )
        }
        is DeriveRule.FieldValues -> {
            val docs = fetch(sampleQuery(rule.collections, rule.mediatype), listOf(rule.field), rule.sample)
            val values = docs.flatMap { d ->
                (if (rule.field == "sponsor") d.sponsor else d.creator).map { HtmlText.toPlain(it).trim() }.distinct()
            }
            valueEntries(values, rule)
        }
        is DeriveRule.Subcollections -> {
            val parent = builder.anyOf(listOf(rule.parent))
            val open = openOnly()
            val listed = raw.searchFields(
                "$parent AND mediatype:collection", listOf("identifier", "title", "downloads"),
                rows = rule.limit * if (open) 10 else 2, sort = "downloads desc",
            ).response.docs
            val docs = if (!open) listed else {
                val passing = fetch("$parent AND NOT mediatype:collection", listOf("collection"), 5000)
                    .flatMapTo(HashSet()) { it.collection }
                listed.filter { it.identifier in passing }
            }
            subcollectionEntries(
                docs.mapNotNull { d ->
                    d.identifier?.let { id ->
                        CollectionDoc(id, d.title.firstOrNull()?.let(HtmlText::toPlain).orEmpty(), d.downloads ?: 0)
                    }
                },
                rule,
            )
        }
    }

    private fun sampleQuery(collections: List<String>, mediatype: String) =
        "${builder.anyOf(collections)} AND mediatype:$mediatype"

    private suspend fun fetch(query: String, fields: List<String>, rows: Int) =
        service.searchFields(query, fields, rows = rows, sort = "downloads desc").response.docs
}
