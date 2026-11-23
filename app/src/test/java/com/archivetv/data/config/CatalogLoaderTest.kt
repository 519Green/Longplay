package com.archivetv.data.config

import com.archivetv.domain.CategoryNode
import com.archivetv.domain.CategoryQuery
import com.archivetv.domain.DeriveRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class CatalogLoaderTest {
    private val loader = CatalogLoader()

    private val valid = """
    {
      "version": 2,
      "home": ["film-noir"],
      "sections": [
        { "id": "movies", "title": "Movies", "children": [
          { "id": "film-noir", "title": "Film Noir",
            "query": { "collections": ["Film_Noir"] }, "facets": "decade" },
          { "id": "movie-genres", "title": "By Genre", "children": [
            { "id": "westerns", "title": "Westerns",
              "query": { "collections": ["feature_films"], "subjectAny": ["western", "westerns"],
                         "yearFrom": 1930, "yearTo": 1959, "exclude": ["x"],
                         "subjectNone": ["home movies"] } },
            { "id": "show-dragnet", "title": "Dragnet",
              "query": { "titleAny": ["Dragnet"], "subjectAny": ["dragnet"], "match": "any",
                         "creatorAny": ["c"], "sponsorAny": ["s"] } }
          ]}
        ]}
      ],
      "facetGroups": {
        "decade": { "minCount": 20, "facets": [
          { "label": "1940s", "query": { "yearFrom": 1940, "yearTo": 1949 } }
        ]}
      }
    }
    """

    @Test fun parsesTreeHomeAndFacets() {
        val c = loader.parse(valid)
        assertThat(c.sections.map { it.id }).containsExactly("movies")
        assertThat(c.home.map { it.id }).containsExactly("film-noir")
        assertThat(c.leaf("film-noir")!!.facetGroup).isEqualTo("decade")
        assertThat(c.group("movie-genres")!!.children.map { it.id })
            .containsExactly("westerns", "show-dragnet").inOrder()
        assertThat(c.leaf("westerns")!!.query).isEqualTo(
            CategoryQuery(
                collections = listOf("feature_films"),
                subjectAny = listOf("western", "westerns"),
                yearFrom = 1930, yearTo = 1959,
                excludeCollections = listOf("x"),
                subjectNone = listOf("home movies"),
            )
        )
        val dragnet = c.leaf("show-dragnet")!!.query
        assertThat(dragnet.matchTitleOrSubject).isTrue()
        assertThat(dragnet.creatorAny).containsExactly("c")
        assertThat(dragnet.sponsorAny).containsExactly("s")
        val decade = c.facetGroups.getValue("decade")
        assertThat(decade.minCount).isEqualTo(20)
        assertThat(decade.facets.single().query).isEqualTo(CategoryQuery(yearFrom = 1940, yearTo = 1949))
    }

    @Test fun rejectsDuplicateIds() {
        val json = valid.replace("\"id\": \"westerns\"", "\"id\": \"film-noir\"")
        assertThrows(IllegalArgumentException::class.java) { loader.parse(json) }
    }

    @Test fun rejectsNodeWithBothChildrenAndQuery() {
        val json = valid.replace(
            "{ \"id\": \"movies\", \"title\": \"Movies\", \"children\": [",
            "{ \"id\": \"movies\", \"title\": \"Movies\", \"query\": {}, \"children\": [",
        )
        assertThrows(IllegalArgumentException::class.java) { loader.parse(json) }
    }

    @Test fun rejectsNodeWithNeitherChildrenNorQuery() {
        val json = valid.replace(
            "\"query\": { \"collections\": [\"Film_Noir\"] }, \"facets\": \"decade\"",
            "\"facets\": \"decade\"",
        )
        assertThrows(IllegalArgumentException::class.java) { loader.parse(json) }
    }

    @Test fun rejectsHomeIdThatIsNotALeaf() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(valid.replace("\"home\": [\"film-noir\"]", "\"home\": [\"movie-genres\"]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(valid.replace("\"home\": [\"film-noir\"]", "\"home\": [\"nope\"]"))
        }
    }

    @Test fun rejectsUnknownFacetGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(valid.replace("\"facets\": \"decade\"", "\"facets\": \"genre\""))
        }
    }

    @Test fun rejectsTopLevelLeaf() {
        val json = valid.replace(
            "\"sections\": [",
            "\"sections\": [ { \"id\": \"loose\", \"title\": \"Loose\", \"query\": {} },",
        )
        assertThrows(IllegalArgumentException::class.java) { loader.parse(json) }
    }

    @Test fun bundledConfigIsValid() {
        // Unit tests run with the module directory as working directory.
        val catalog = loader.parse(File("src/main/assets/browse_config.json").readText())
        assertThat(catalog.sections.map { it.id })
            .containsExactly("movies", "tv", "animation", "educational", "listen").inOrder()
        assertThat(catalog.home).isNotEmpty()
        assertThat(catalog.allLeaves.size).isAtLeast(40)
        // Every leaf narrows by at least a collection, title, creator or sponsor.
        catalog.allLeaves.forEach { leaf ->
            val q = leaf.query
            assertWithMessage("scope of ${leaf.id}")
                .that(q.collections + q.titleAny + q.creatorAny + q.sponsorAny).isNotEmpty()
        }
        assertThat(catalog.node("tv-1990s")).isNotNull()
        assertThat(catalog.leaf("home-movies")).isNotNull()
    }

    @Test fun emptyQueryIsAllowedOnlyAsFacetGroupMember() {
        // A leaf with an empty query object still parses; scope is checked by the bundled test.
        val c = loader.parse(valid)
        assertThat(c.node("show-dragnet")).isInstanceOf(CategoryNode.Leaf::class.java)
    }

    private fun oneLeaf(extra: String) = """
    { "version": 2, "sections": [
      { "id": "listen", "title": "Listen", "children": [
        { "id": "a", "title": "A", "query": { "collections": ["librivoxaudio"] $extra } }
      ]}
    ]}
    """

    @Test fun parsesMediatypeAndPlayAll() {
        val json = """
        { "version": 2, "sections": [
          { "id": "listen", "title": "Listen", "children": [
            { "id": "a", "title": "A", "playAll": true,
              "query": { "collections": ["78rpm"], "mediatype": "audio" } }
          ]}
        ]}
        """
        val leaf = loader.parse(json).leaf("a")!!
        assertThat(leaf.query.mediatype).isEqualTo("audio")
        assertThat(leaf.playAll).isTrue()
    }

    @Test fun mediatypeDefaultsToMovies() {
        val leaf = loader.parse(oneLeaf("")).leaf("a")!!
        assertThat(leaf.query.mediatype).isEqualTo("movies")
        assertThat(leaf.playAll).isFalse()
    }

    @Test fun rejectsUnknownMediatype() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(oneLeaf(", \"mediatype\": \"texts\""))
        }
    }

    @Test fun bundledCatalogHasListenSection() {
        val catalog = loader.parse(File("src/main/assets/browse_config.json").readText())
        assertThat(catalog.sections.map { it.id }).contains("listen")
        val listen = catalog.group("listen")!!
        fun leaves(n: CategoryNode): List<CategoryNode.Leaf> = when (n) {
            is CategoryNode.Leaf -> listOf(n)
            is CategoryNode.Group -> n.children.flatMap(::leaves)
            is CategoryNode.Derived -> emptyList()
        }
        val all = leaves(listen)
        assertThat(all).isNotEmpty()
        all.forEach { assertWithMessage(it.id).that(it.query.mediatype).isEqualTo("audio") }
    }

    private fun derived(derive: String, extra: String = "") = """
    { "version": 2, "sections": [
      { "id": "edu", "title": "Educational", "children": [
        { "id": "d", "title": "Sponsored Films", "derive": $derive $extra }
      ]}
    ],
      "facetGroups": { "decade": { "facets": [ { "label": "1940s", "query": { "yearFrom": 1940, "yearTo": 1949 } } ] } }
    }
    """

    @Test fun parsesDerivedGroup() {
        val c = loader.parse(derived(
            """{ "kind": "fieldValues", "field": "sponsor", "collections": ["prelinger"], "mediatype": "movies",
                 "sample": 5000, "minItems": 5, "limit": 30 }""",
            """, "facets": "decade" """,
        ))
        val node = c.derived("d")!!
        assertThat(node.title).isEqualTo("Sponsored Films")
        assertThat(node.facetGroup).isEqualTo("decade")
        assertThat(node.rule).isEqualTo(DeriveRule.FieldValues("sponsor", listOf("prelinger"), "movies", 5000, 5, 30))
        assertThat(c.node("d")).isInstanceOf(CategoryNode.Derived::class.java)
    }

    @Test fun parsesTitleSeriesAndSubcollections() {
        val series = loader.parse(derived(
            """{ "kind": "titleSeries", "collections": ["classic_tv"], "sample": 1500, "minItems": 4, "limit": 40 }""",
        )).derived("d")!!.rule
        assertThat(series).isEqualTo(DeriveRule.TitleSeries(listOf("classic_tv"), "movies", 1500, 4, 40))
        val radio = loader.parse(derived(
            """{ "kind": "subcollections", "parent": "oldtimeradio", "mediatype": "audio", "limit": 40 }""",
        )).derived("d")!!.rule
        assertThat(radio).isEqualTo(DeriveRule.Subcollections("oldtimeradio", "audio", 40))
    }

    @Test fun rejectsDerivedWithChildren() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(derived(
                """{ "kind": "subcollections", "parent": "oldtimeradio" }""",
                """, "children": [] """,
            ))
        }
    }

    @Test fun rejectsUnknownDeriveKind() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(derived("""{ "kind": "magic", "collections": ["x"], "sample": 10 }"""))
        }
    }

    @Test fun rejectsFieldValuesWithoutField() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.parse(derived("""{ "kind": "fieldValues", "collections": ["x"], "sample": 10 }"""))
        }
    }

    @Test fun bundledCatalogHasNoHandPickedNames() {
        val catalog = loader.parse(File("src/main/assets/browse_config.json").readText())
        catalog.allLeaves.forEach { leaf ->
            val q = leaf.query
            assertWithMessage("hand-picked names in ${leaf.id}").that(q.titleAny + q.creatorAny + q.sponsorAny).isEmpty()
        }
        listOf("tv-shows", "edu-studios", "edu-sponsors", "listen-radio-shows").forEach {
            assertWithMessage(it).that(catalog.derived(it)).isNotNull()
        }
        assertThat(catalog.node("animation-studios")).isNull()
        assertThat(catalog.home.map { it.id }).contains("edu-popular")
        assertThat(catalog.home.map { it.id }).doesNotContain("studio-coronet")
    }

    private fun withSeasonal(seasonal: String) = """
    { "version": 2, "sections": [
      { "id": "movies", "title": "Movies", "children": [
        { "id": "a", "title": "A", "query": { "collections": ["colA"] } }
      ]}
    ], "seasonal": [ $seasonal ] }
    """

    @Test fun parsesSeasonalEntries() {
        val catalog = loader.parse(
            withSeasonal(
                """
                { "id": "winter", "title": "Winter", "from": "12-26", "to": "01-06",
                  "query": { "collections": ["colA"], "subjectAny": ["snow"] } },
                { "id": "spring", "title": "Spring", "easter": [-7, 1], "query": { "subjectAny": ["eggs"] } }
                """,
            ),
        )
        assertThat(catalog.seasonal).containsExactly(
            com.archivetv.domain.SeasonalEntry(
                "winter", "Winter",
                com.archivetv.domain.SeasonWindow.Dates(java.time.MonthDay.of(12, 26), java.time.MonthDay.of(1, 6)),
                com.archivetv.domain.CategoryQuery(collections = listOf("colA"), subjectAny = listOf("snow")),
            ),
            com.archivetv.domain.SeasonalEntry(
                "spring", "Spring", com.archivetv.domain.SeasonWindow.Easter(-7, 1),
                com.archivetv.domain.CategoryQuery(subjectAny = listOf("eggs")),
            ),
        ).inOrder()
    }

    @Test fun rejectsBadWindow() {
        listOf(
            """{ "id": "x", "title": "X", "from": "13-40", "to": "01-06", "query": { "subjectAny": ["a"] } }""",
            """{ "id": "x", "title": "X", "from": "12-01", "query": { "subjectAny": ["a"] } }""",
            """{ "id": "x", "title": "X", "easter": [1], "query": { "subjectAny": ["a"] } }""",
            """{ "id": "x", "title": "X", "from": "12-01", "to": "12-31", "easter": [0, 1], "query": { "subjectAny": ["a"] } }""",
        ).forEach { bad ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { loader.parse(withSeasonal(bad)) }
        }
    }

    @Test fun bundledConfigHasFiveSeasons() {
        val catalog = loader.parse(File("src/main/assets/browse_config.json").readText())
        assertThat(catalog.seasonal.map { it.title })
            .containsExactly("Christmas", "Halloween", "Thanksgiving", "Easter", "New Year").inOrder()
    }

    @Test fun seasonalRowsIncludeCommunityUploads() {
        // Owner choice "B" (2026-11-23): curated collections alone leave most holidays under the 8-item minimum.
        val catalog = loader.parse(File("src/main/assets/browse_config.json").readText())
        catalog.seasonal.forEach { assertThat(it.query.collections).contains("opensource_movies") }
    }
}

