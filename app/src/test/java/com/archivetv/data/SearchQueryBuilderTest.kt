package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQueryBuilderTest {
    private val b = SearchQueryBuilder()

    @Test fun collectionQueryFiltersByCollection() {
        assertThat(b.collection("feature_films"))
            .isEqualTo("collection:(feature_films) AND mediatype:(movies OR tv)")
    }

    @Test fun userSearchEscapesAndScopesToVideo() {
        assertThat(b.userSearch("night living"))
            .isEqualTo("(night living) AND mediatype:(movies OR tv)")
    }

    @Test fun userSearchTrimsBlank() {
        assertThat(b.userSearch("  metropolis  "))
            .isEqualTo("(metropolis) AND mediatype:(movies OR tv)")
    }
}
