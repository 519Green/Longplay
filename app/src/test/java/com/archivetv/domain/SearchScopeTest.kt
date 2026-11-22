package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchScopeTest {
    private val catalog = Catalog(
        sections = listOf(
            CategoryNode.Group(
                "tv", "TV",
                listOf(
                    CategoryNode.Leaf("tv-50s", "1950s", CategoryQuery(collections = listOf("tv_a"))),
                    CategoryNode.Derived("shows", "Shows", DeriveRule.TitleSeries(listOf("tv_b"), "movies", 10, 2, 5)),
                ),
            ),
            CategoryNode.Group(
                "listen", "Listen",
                listOf(
                    CategoryNode.Leaf("books", "Books", CategoryQuery(collections = listOf("books_c"), mediatype = "audio")),
                    CategoryNode.Derived("radio", "Radio", DeriveRule.Subcollections("radio_d", "audio", 5)),
                ),
            ),
            CategoryNode.Group("empty", "Empty", emptyList()),
        ),
        home = emptyList(),
        facetGroups = emptyMap(),
    )

    @Test fun oneChipPerSectionBetweenAllAndEverything() {
        val scopes = searchScopes(catalog)
        assertThat(scopes.map { it.id }).containsExactly("all", "tv", "listen", "everything").inOrder()
        val tv = scopes[1]
        assertThat(tv.kind).isEqualTo(ScopeKind.VIDEO)
        assertThat(tv.collections).containsExactly("tv_a", "tv_b").inOrder()
        val listen = scopes[2]
        assertThat(listen.kind).isEqualTo(ScopeKind.AUDIO)
        assertThat(listen.collections).containsExactly("books_c", "radio_d").inOrder()
    }

    @Test fun whatEachScopeSearches() {
        assertThat(SearchScope.All.searchesLive).isTrue()
        assertThat(SearchScope.Everything.searchesLive).isFalse()
        val video = SearchScope("v", "V", ScopeKind.VIDEO)
        assertThat(video.searchesAudio).isFalse()
        assertThat(video.searchesVideo).isTrue()
    }
}
