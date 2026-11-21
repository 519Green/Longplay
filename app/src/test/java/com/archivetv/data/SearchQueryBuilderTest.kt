package com.archivetv.data

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

    @Test fun userSearchOfOnlySyntaxIsBlank() {
        assertThat(b.userSearch(" :()\"^ ")).isEmpty()
    }
}
