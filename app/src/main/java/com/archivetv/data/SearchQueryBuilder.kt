package com.archivetv.data

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

    /** Returns an empty string when [text] has no searchable words. */
    fun userSearch(text: String): String {
        val terms = text.replace(luceneSyntax, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in operators }
            .joinToString(" ")
        if (terms.isEmpty()) return ""
        return "((${anyOf(curated)} AND (title:($terms) OR subject:($terms) OR creator:($terms))) " +
            "OR (${anyOf(community)} AND title:($terms))) AND $video"
    }

    private fun anyOf(collections: List<String>) =
        "collection:(${collections.joinToString(" OR ")})"
}
