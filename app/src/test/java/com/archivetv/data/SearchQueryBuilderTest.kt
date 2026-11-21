package com.archivetv.data

import com.archivetv.domain.CategoryQuery
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQueryBuilderTest {
    private val b = SearchQueryBuilder()

    private val curated = "collection:(feature_films OR classic_tv OR animationandcartoons OR " +
        "short_films OR cliffhangers OR prelinger OR educationalfilms)"
    private val community = "collection:(opensource_movies OR opensource)"

    @Test fun collectionQueryFiltersByCollection() {
        assertThat(b.collection("feature_films"))
            .isEqualTo("collection:(feature_films) AND mediatype:movies")
    }

    @Test fun userSearchCombinesCuratedAndCommunityTitleMatches() {
        assertThat(b.userSearch("night living")).isEqualTo(
            "(($curated AND (title:(night living) OR subject:(night living) OR creator:(night living))) " +
                "OR ($community AND title:(night living))) AND mediatype:movies"
        )
    }

    @Test fun userSearchTrimsAndCollapsesWhitespace() {
        assertThat(b.userSearch("  metropolis   1927 ")).contains("title:(metropolis 1927)")
    }

    @Test fun userSearchStripsLuceneSyntax() {
        val q = b.userSearch("""He-Man: "Masters" (of) [the] {Universe}^2 ~*? \ + ! / &&""")
        assertThat(q).contains("title:(He Man Masters of the Universe 2)")
        assertThat(q).doesNotContain("\"")
        assertThat(q).doesNotContain("^")
    }

    @Test fun userSearchDropsBareBooleanOperators() {
        assertThat(b.userSearch("cats AND dogs OR NOT mice")).contains("title:(cats dogs mice)")
    }

    @Test fun renderCollectionsOnly() {
        assertThat(b.render(CategoryQuery(collections = listOf("Film_Noir"))))
            .isEqualTo("collection:(Film_Noir) AND mediatype:movies")
    }

    @Test fun renderAllClausesInFixedOrder() {
        val q = CategoryQuery(
            collections = listOf("prelinger", "educationalfilms"),
            subjectAny = listOf("labor", "industry"),
            creatorAny = listOf("coronet", "national film board"),
            sponsorAny = listOf("chevrolet"),
            yearFrom = 1940, yearTo = 1949,
            subjectNone = listOf("home movies"),
            excludeCollections = listOf("feature_films_unsorted"),
        )
        assertThat(b.render(q)).isEqualTo(
            "collection:(prelinger OR educationalfilms)" +
                " AND (subject:\"labor\" OR subject:\"industry\")" +
                " AND (creator:\"coronet\" OR creator:\"national film board\")" +
                " AND (sponsor:\"chevrolet\")" +
                " AND year:[1940 TO 1949]" +
                " AND NOT subject:\"home movies\"" +
                " AND NOT collection:(feature_films_unsorted)" +
                " AND mediatype:movies"
        )
    }

    @Test fun renderTitleOrSubjectForShows() {
        val q = CategoryQuery(
            collections = listOf("classic_tv"),
            titleAny = listOf("Dragnet"),
            subjectAny = listOf("dragnet"),
            matchTitleOrSubject = true,
        )
        assertThat(b.render(q)).isEqualTo(
            "collection:(classic_tv) AND (title:\"Dragnet\" OR subject:\"dragnet\") AND mediatype:movies"
        )
    }

    @Test fun renderTitlesAndSubjectsSeparatelyByDefault() {
        val q = CategoryQuery(titleAny = listOf("a"), subjectAny = listOf("b"))
        assertThat(b.render(q))
            .isEqualTo("(subject:\"b\") AND (title:\"a\") AND mediatype:movies")
    }

    @Test fun renderOpenEndedYears() {
        assertThat(b.render(CategoryQuery(collections = listOf("c"), yearTo = 1929)))
            .isEqualTo("collection:(c) AND year:[* TO 1929] AND mediatype:movies")
        assertThat(b.render(CategoryQuery(collections = listOf("c"), yearFrom = 1980)))
            .isEqualTo("collection:(c) AND year:[1980 TO *] AND mediatype:movies")
    }

    @Test fun renderAppendsFacet() {
        val leaf = CategoryQuery(collections = listOf("Film_Noir"))
        val facet = CategoryQuery(yearFrom = 1940, yearTo = 1949)
        assertThat(b.render(leaf, facet))
            .isEqualTo("collection:(Film_Noir) AND year:[1940 TO 1949] AND mediatype:movies")
    }

    @Test fun renderEscapesPhrases() {
        val q = CategoryQuery(subjectAny = listOf("""say "hi" \ now"""))
        assertThat(b.render(q))
            .isEqualTo("""(subject:"say \"hi\" \\ now") AND mediatype:movies""")
    }

    @Test fun renderDropsUnsafeCollectionIds() {
        // Collection ids are identifiers; anything outside [A-Za-z0-9_.-] is removed.
        assertThat(b.render(CategoryQuery(collections = listOf("ok_1", "bad) OR (x"))))
            .isEqualTo("collection:(ok_1 OR badORx) AND mediatype:movies")
    }

    @Test fun userSearchOfOnlySyntaxIsBlank() {
        assertThat(b.userSearch(" :()\"^ ")).isEmpty()
    }

    @Test fun renderUsesLeafMediatype() {
        val q = CategoryQuery(collections = listOf("librivoxaudio"), mediatype = "audio")
        assertThat(b.render(q)).isEqualTo("collection:(librivoxaudio) AND mediatype:audio")
    }

    @Test fun renderDefaultsToMovies() {
        assertThat(b.render(CategoryQuery(collections = listOf("feature_films"))))
            .isEqualTo("collection:(feature_films) AND mediatype:movies")
    }

    @Test fun facetInheritsLeafMediatype() {
        val leaf = CategoryQuery(collections = listOf("78rpm"), mediatype = "audio")
        assertThat(b.render(leaf, CategoryQuery(yearFrom = 1920, yearTo = 1929)))
            .isEqualTo("collection:(78rpm) AND year:[1920 TO 1929] AND mediatype:audio")
    }

    @Test fun audioSearchCoversCuratedAudioAndHifidelityTitles() {
        assertThat(b.audioSearch("leonard cohen")).isEqualTo(
            "((collection:(librivoxaudio OR oldtimeradio OR 78rpm OR album_recordings) AND " +
                "(title:(leonard cohen) OR subject:(leonard cohen) OR creator:(leonard cohen))) " +
                "OR (collection:(hifidelity) AND (title:(leonard cohen) OR creator:(leonard cohen)))) AND mediatype:audio"
        )
    }

    @Test fun audioSearchEmptyForSyntaxOnly() {
        assertThat(b.audioSearch("\"()\"")).isEmpty()
    }
}
