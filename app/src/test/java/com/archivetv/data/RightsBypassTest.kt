package com.archivetv.data

import com.archivetv.data.api.FieldDocDto
import com.archivetv.data.api.LiveDocDto
import com.archivetv.data.api.SearchDocDto
import com.archivetv.domain.CategoryNode
import com.archivetv.domain.DeriveRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The two deliberate bypasses of the rights filter, and the collection-list intersection. */
class RightsBypassTest {
    // Plays the filtered service: answers only the passing-recordings query.
    private val passingRecordings = "collection:(etree) AND mediatype:etree"
    private fun filteredLive() = FakeArchiveService(liveDocs = { q ->
        if (q == passingRecordings) listOf(LiveDocDto("rec1", collection = listOf("artistA", "etree"))) else emptyList()
    })
    private fun rawLive() = FakeArchiveService(liveDocs = { q ->
        if (q == LiveQueries.ARTISTS) {
            listOf(LiveDocDto("artistA", title = listOf("Artist A")), LiveDocDto("artistB", title = listOf("Artist B")))
        } else emptyList()
    })

    @Test fun itemsByIdsUsesTheLookupService() = runTest {
        val filtered = FakeArchiveService()
        val lookup = FakeArchiveService(searchDocs = { listOf(SearchDocDto("saved1", title = listOf("Saved One"))) })
        val repo = ArchiveRepository(filtered, PlayableFileSelector(), SearchQueryBuilder(), lookupService = lookup)
        val items = repo.itemsByIds(listOf("saved1"))
        assertThat(items.keys).containsExactly("saved1")
        assertThat(filtered.queries).isEmpty()
        assertThat(lookup.queries).hasSize(1)
    }

    @Test fun openModeArtistsAreThoseWithPassingRecordings() = runTest {
        val repo = LiveMusicRepository(filteredLive(), rawLive()) { true }
        assertThat(repo.artists().map { it.id }).containsExactly("artistA")
        assertThat(repo.popularArtists().map { it.id }).containsExactly("artistA")
        assertThat(repo.searchArtists("artist").map { it.id }).isEmpty() // raw fake has no search answer
    }

    @Test fun closedModeUsesRawArtists() = runTest {
        val filtered = filteredLive()
        val repo = LiveMusicRepository(filtered, rawLive()) { false }
        assertThat(repo.artists().map { it.id }).containsExactly("artistA", "artistB")
        assertThat(filtered.queries).doesNotContain(passingRecordings)
    }

    @Test fun clearCacheRefetchesArtists() = runTest {
        val raw = rawLive()
        val filtered = filteredLive()
        val repo = LiveMusicRepository(filtered, raw) { true }
        repo.artists(); repo.artists()
        assertThat(raw.queries.count { it == LiveQueries.ARTISTS }).isEqualTo(1)
        assertThat(filtered.queries.count { it == passingRecordings }).isEqualTo(1)
        repo.clearCache()
        repo.artists()
        assertThat(raw.queries.count { it == LiveQueries.ARTISTS }).isEqualTo(2)
        assertThat(filtered.queries.count { it == passingRecordings }).isEqualTo(2)
    }

    private val radio = CategoryNode.Derived("radio", "Radio Shows", DeriveRule.Subcollections("parentcol", "audio", 40))
    private val rawSubs = "collection:(parentcol) AND mediatype:collection"
    private val passingItems = "collection:(parentcol) AND NOT mediatype:collection"
    private fun rawFields() = FakeArchiveService(fieldDocs = { q ->
        if (q == rawSubs) {
            listOf(FieldDocDto("subA", title = listOf("Sub A"), downloads = 10), FieldDocDto("subB", title = listOf("Sub B"), downloads = 20))
        } else emptyList()
    })
    private fun filteredFields() = FakeArchiveService(fieldDocs = { q ->
        if (q == passingItems) listOf(FieldDocDto("item1", collection = listOf("subA", "parentcol"))) else emptyList()
    })

    @Test fun openModeSubcollectionsNeedAPassingItem() = runTest {
        val groups = DerivedGroups(filteredFields(), rawFields()) { true }
        assertThat(groups.entries(radio).map { it.label }).containsExactly("Sub A")
    }

    @Test fun closedModeSubcollectionsAreAllListed() = runTest {
        val groups = DerivedGroups(filteredFields(), rawFields()) { false }
        assertThat(groups.entries(radio).map { it.label }).containsExactly("Sub B", "Sub A").inOrder()
    }

    @Test fun clearCacheRefetchesDerivedGroups() = runTest {
        val raw = rawFields()
        val groups = DerivedGroups(filteredFields(), raw) { true }
        groups.entries(radio); groups.entries(radio)
        assertThat(raw.queries.count { it == rawSubs }).isEqualTo(1)
        groups.clearCache()
        groups.entries(radio)
        assertThat(raw.queries.count { it == rawSubs }).isEqualTo(2)
    }
}
