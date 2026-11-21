package com.archivetv.data.config

import com.archivetv.domain.Catalog
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.archivetv.domain.Facet
import com.archivetv.domain.FacetGroup
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class CatalogDto(
    val version: Int,
    val home: List<String> = emptyList(),
    val sections: List<NodeDto> = emptyList(),
    val facetGroups: Map<String, FacetGroupDto> = emptyMap(),
)

data class NodeDto(
    val id: String,
    val title: String,
    val children: List<NodeDto>? = null,
    val query: QueryDto? = null,
    val facets: String? = null,
    val playAll: Boolean = false,
)

data class QueryDto(
    val collections: List<String> = emptyList(),
    val subjectAny: List<String> = emptyList(),
    val titleAny: List<String> = emptyList(),
    val creatorAny: List<String> = emptyList(),
    val sponsorAny: List<String> = emptyList(),
    val subjectNone: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val match: String? = null,
    val mediatype: String? = null,
)

data class FacetGroupDto(val facets: List<FacetDto>, val minCount: Int = 1)
data class FacetDto(val label: String, val query: QueryDto)

/**
 * Parses and validates browse_config.json (version 2). Problems in the
 * bundled file are programming errors, so they throw IllegalArgumentException.
 */
class CatalogLoader {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(CatalogDto::class.java)

    fun parse(json: String): Catalog {
        val dto = requireNotNull(adapter.fromJson(json)) { "empty catalog" }
        require(dto.version == 2) { "unsupported catalog version ${dto.version}" }

        val seen = mutableSetOf<String>()
        fun node(n: NodeDto): CategoryNode {
            require(seen.add(n.id)) { "duplicate id ${n.id}" }
            require((n.children == null) != (n.query == null)) {
                "${n.id}: a node needs exactly one of children or query"
            }
            n.facets?.let { require(it in dto.facetGroups) { "${n.id}: unknown facet group $it" } }
            return if (n.children != null) {
                CategoryNode.Group(n.id, n.title, n.children.map(::node))
            } else {
                val mediatype = n.query!!.mediatype
                require(mediatype == null || mediatype in mediatypes) { "${n.id}: unknown mediatype $mediatype" }
                CategoryNode.Leaf(n.id, n.title, n.query.toDomain(), n.facets, n.playAll)
            }
        }

        val sections = dto.sections.map { s ->
            val built = node(s)
            require(built is CategoryNode.Group) { "${s.id}: top-level sections must be groups" }
            built
        }
        val facetGroups = dto.facetGroups.mapValues { (name, g) ->
            FacetGroup(name, g.facets.map { Facet(it.label, it.query.toDomain()) }, g.minCount)
        }
        val catalog = Catalog(sections, home = emptyList(), facetGroups = facetGroups)
        val home = dto.home.map { id ->
            requireNotNull(catalog.leaf(id)) { "home id $id is not a leaf" }
        }
        return catalog.copy(home = home)
    }

    private val mediatypes = setOf("movies", "audio")

    private fun QueryDto.toDomain() = CategoryQuery(
        collections = collections,
        subjectAny = subjectAny,
        titleAny = titleAny,
        creatorAny = creatorAny,
        sponsorAny = sponsorAny,
        subjectNone = subjectNone,
        excludeCollections = exclude,
        yearFrom = yearFrom,
        yearTo = yearTo,
        matchTitleOrSubject = match == "any",
        mediatype = mediatype ?: "movies",
    )
}
