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
 */
class DerivedGroups(private val service: InternetArchiveService) {
    private val builder = SearchQueryBuilder()
    private val lock = Mutex()
    private val cache = mutableMapOf<String, List<DerivedEntry>>()

    suspend fun entries(node: CategoryNode.Derived): List<DerivedEntry> = lock.withLock {
        cache[node.id] ?: load(node.rule).also { cache[node.id] = it }
    }

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
            val query = "${builder.anyOf(listOf(rule.parent))} AND mediatype:collection"
            val docs = fetch(query, listOf("identifier", "title", "downloads"), rule.limit * 2)
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
