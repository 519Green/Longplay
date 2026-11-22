package com.archivetv.data

import com.archivetv.domain.CategoryQuery

class SearchQueryBuilder {
    // IA has no "tv" mediatype; television items are mediatype:movies.
    private val video = "mediatype:movies"

    /** Curated collections: matched on title, subject and creator (studio). */
    private val curated = listOf(
        "feature_films", "classic_tv", "animationandcartoons", "short_films",
        "cliffhangers", "prelinger", "educationalfilms",
    )

    /** Community Video uploads: matched on title only (uploader tags are noisy). */
    private val community = listOf("opensource_movies", "opensource")

    private val luceneSyntax = Regex("""[+\-&|!(){}\[\]^"~*?:\\/]""")
    private val operators = setOf("AND", "OR", "NOT")

    fun collection(collectionId: String): String =
        "collection:($collectionId) AND $video"

    /** Curated audio collections: matched on title, subject and creator (author, performer). */
    private val curatedAudio = listOf("librivoxaudio", "oldtimeradio", "78rpm", "album_recordings")

    /** High Fidelity Emporium (member-uploaded CD and record rips): title and creator only. */
    private val communityAudio = listOf("hifidelity")

    internal fun terms(text: String) = text.replace(luceneSyntax, " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in operators }
        .joinToString(" ")

    /** Audio search for the Listen results. Empty when [text] has no searchable words. */
    fun audioSearch(text: String): String {
        val terms = terms(text)
        if (terms.isEmpty()) return ""
        return "((${anyOf(curatedAudio)} AND (title:($terms) OR subject:($terms) OR creator:($terms))) " +
            "OR (${anyOf(communityAudio)} AND (title:($terms) OR creator:($terms)))) AND mediatype:audio"
    }

    /** Returns an empty string when [text] has no searchable words. */
    fun userSearch(text: String): String {
        val terms = terms(text)
        if (terms.isEmpty()) return ""
        return "((${anyOf(curated)} AND (title:($terms) OR subject:($terms) OR creator:($terms))) " +
            "OR (${anyOf(community)} AND title:($terms))) AND $video"
    }

    /**
     * Renders a catalog leaf (and an optional facet) to Lucene text. Clause
     * order is fixed so the output is stable and testable.
     */
    fun render(query: CategoryQuery, facet: CategoryQuery? = null): String =
        (clauses(query) + facet?.let(::clauses).orEmpty() + "mediatype:${query.mediatype}").joinToString(" AND ")

    private fun clauses(q: CategoryQuery): List<String> = buildList {
        if (q.collections.isNotEmpty()) add(anyOf(q.collections))
        if (q.matchTitleOrSubject && q.titleAny.isNotEmpty() && q.subjectAny.isNotEmpty()) {
            add(phrases("title", q.titleAny, close = false) + " OR " + phrases("subject", q.subjectAny, open = false))
        } else {
            if (q.subjectAny.isNotEmpty()) add(phrases("subject", q.subjectAny))
            if (q.titleAny.isNotEmpty()) add(phrases("title", q.titleAny))
        }
        if (q.creatorAny.isNotEmpty()) add(phrases("creator", q.creatorAny))
        if (q.sponsorAny.isNotEmpty()) add(phrases("sponsor", q.sponsorAny))
        if (q.yearFrom != null || q.yearTo != null) {
            add("year:[${q.yearFrom ?: "*"} TO ${q.yearTo ?: "*"}]")
        }
        q.subjectNone.forEach { add("NOT subject:${quote(it)}") }
        if (q.excludeCollections.isNotEmpty()) add("NOT " + anyOf(q.excludeCollections))
    }

    /** `(field:"a" OR field:"b")`; open/close let two groups share one pair of parentheses. */
    private fun phrases(field: String, values: List<String>, open: Boolean = true, close: Boolean = true) =
        (if (open) "(" else "") +
            values.joinToString(" OR ") { "$field:${quote(it)}" } +
            (if (close) ")" else "")

    private fun quote(phrase: String) =
        "\"" + phrase.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private val unsafeId = Regex("[^A-Za-z0-9_.-]")

    internal fun anyOf(collections: List<String>) =
        "collection:(${collections.joinToString(" OR ") { it.replace(unsafeId, "") }})"
}
