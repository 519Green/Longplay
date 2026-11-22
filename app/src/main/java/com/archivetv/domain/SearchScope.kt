package com.archivetv.domain

enum class ScopeKind { ALL, VIDEO, AUDIO, EVERYTHING }

/**
 * A Search filter chip. ALL is the default curated search (video, audio and
 * Live Music); VIDEO and AUDIO limit it to one catalog section's collections;
 * EVERYTHING matches titles anywhere on archive.org.
 */
data class SearchScope(
    val id: String,
    val label: String,
    val kind: ScopeKind,
    val collections: List<String> = emptyList(),
) {
    val searchesVideo: Boolean get() = kind != ScopeKind.AUDIO
    val searchesAudio: Boolean get() = kind != ScopeKind.VIDEO
    val searchesLive: Boolean get() = kind == ScopeKind.ALL || kind == ScopeKind.AUDIO

    companion object {
        val All = SearchScope("all", "All", ScopeKind.ALL)
        val Everything = SearchScope("everything", "Everything on Archive.org", ScopeKind.EVERYTHING)
    }
}

/** All, one chip per catalog section (from its leaves' collections), then Everything. */
fun searchScopes(catalog: Catalog): List<SearchScope> {
    val sections = catalog.sections.mapNotNull { section ->
        val collections = linkedSetOf<String>()
        val mediatypes = mutableSetOf<String>()
        fun walk(n: CategoryNode) {
            when (n) {
                is CategoryNode.Group -> n.children.forEach(::walk)
                is CategoryNode.Leaf -> {
                    collections += n.query.collections
                    mediatypes += n.query.mediatype
                }
                is CategoryNode.Derived -> when (val r = n.rule) {
                    is DeriveRule.TitleSeries -> { collections += r.collections; mediatypes += r.mediatype }
                    is DeriveRule.FieldValues -> { collections += r.collections; mediatypes += r.mediatype }
                    is DeriveRule.Subcollections -> { collections += r.parent; mediatypes += r.mediatype }
                }
            }
        }
        walk(section)
        if (collections.isEmpty()) return@mapNotNull null
        val kind = if (mediatypes == setOf("audio")) ScopeKind.AUDIO else ScopeKind.VIDEO
        SearchScope(section.id, section.title, kind, collections.toList())
    }
    return listOf(SearchScope.All) + sections + SearchScope.Everything
}
