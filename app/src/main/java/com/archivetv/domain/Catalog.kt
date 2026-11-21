package com.archivetv.domain

/**
 * A saved Internet Archive search. Every list field is OR'ed internally;
 * the fields are AND'ed together (see SearchQueryBuilder.render).
 */
data class CategoryQuery(
    val collections: List<String> = emptyList(),
    val subjectAny: List<String> = emptyList(),
    val titleAny: List<String> = emptyList(),
    val creatorAny: List<String> = emptyList(),
    val sponsorAny: List<String> = emptyList(),
    val subjectNone: List<String> = emptyList(),
    val excludeCollections: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** When true, titleAny and subjectAny are OR'ed with each other (shows). */
    val matchTitleOrSubject: Boolean = false,
    /** IA mediatype: "movies" (all video, including TV) or "audio". */
    val mediatype: String = "movies",
)

/** One filter chip on a Browse screen, AND'ed onto the leaf's query. */
data class Facet(val label: String, val query: CategoryQuery)

/** A named set of facets; chips below [minCount] results are hidden. */
data class FacetGroup(val name: String, val facets: List<Facet>, val minCount: Int = 1)

sealed interface CategoryNode {
    val id: String
    val title: String

    data class Group(
        override val id: String,
        override val title: String,
        val children: List<CategoryNode>,
    ) : CategoryNode

    data class Leaf(
        override val id: String,
        override val title: String,
        val query: CategoryQuery,
        val facetGroup: String? = null,
        /** Browse offers Play all / Shuffle (music leaves). */
        val playAll: Boolean = false,
    ) : CategoryNode
}

data class Catalog(
    /** Top-level sections, in drawer order. */
    val sections: List<CategoryNode.Group>,
    /** Leaves shown as rows on Home, in order. */
    val home: List<CategoryNode.Leaf>,
    val facetGroups: Map<String, FacetGroup>,
) {
    private val index: Map<String, CategoryNode> = buildMap {
        fun walk(n: CategoryNode) {
            put(n.id, n)
            if (n is CategoryNode.Group) n.children.forEach(::walk)
        }
        sections.forEach(::walk)
    }

    fun node(id: String): CategoryNode? = index[id]
    fun leaf(id: String): CategoryNode.Leaf? = index[id] as? CategoryNode.Leaf
    fun group(id: String): CategoryNode.Group? = index[id] as? CategoryNode.Group
    val allLeaves: List<CategoryNode.Leaf> get() = index.values.filterIsInstance<CategoryNode.Leaf>()
}
