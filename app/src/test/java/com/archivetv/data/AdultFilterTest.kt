package com.archivetv.data

import com.archivetv.data.api.FieldDocDto
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.LiveDocDto
import com.archivetv.data.api.LiveResponseDto
import com.archivetv.data.api.FieldsResponseDto
import com.archivetv.data.api.SearchDocDto
import com.archivetv.domain.AdultRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AdultFilterTest {
    private val day = LocalDate.of(2026, 11, 22)
    private val plain = SearchDocDto("plain", licenseurl = listOf("http://creativecommons.org/licenses/by/4.0/"))
    private val tagged = SearchDocDto("tagged", licenseurl = listOf("http://creativecommons.org/licenses/by/4.0/"), subject = listOf("porn"))
    private val flagged = SearchDocDto("flagged", licenseurl = listOf("http://creativecommons.org/licenses/by/4.0/"), adult = listOf("true"))

    @Test fun adultOnAddsItsClauseAndDropsFlaggedDocs() = runTest {
        val inner = FakeArchiveService(searchDocs = { listOf(plain, tagged) })
        val service = RightsFilteredService(inner, { false }, { day }, hideAdult = { true })
        val docs = service.search("q", rows = 10, sort = "downloads desc").response.docs
        assertThat(docs.map { it.identifier }).containsExactly("plain")
        assertThat(inner.queries.single()).startsWith("(q) AND (NOT adult:*")
    }

    @Test fun bothOnCarryBothClauses() = runTest {
        val inner = FakeArchiveService(searchDocs = { listOf(plain, flagged) })
        val service = RightsFilteredService(inner, { true }, { day }, hideAdult = { true })
        val docs = service.search("q", rows = 10, sort = "downloads desc").response.docs
        assertThat(docs.map { it.identifier }).containsExactly("plain")
        val q = inner.queries.single()
        assertThat(q).contains("licenseurl:*")
        assertThat(q).contains("NOT adult:*")
    }

    @Test fun bothOffPassThrough() = runTest {
        val inner = FakeArchiveService(searchDocs = { listOf(plain, flagged) })
        val service = RightsFilteredService(inner, { false }, { day }, hideAdult = { false })
        assertThat(service.search("q", 10, "downloads desc").response.docs).hasSize(2)
        assertThat(inner.queries.single()).isEqualTo("q")
    }

    @Test fun adultFieldsRequested() = runTest {
        val fields = mutableListOf<List<String>>()
        val inner = object : InternetArchiveService by FakeArchiveService() {
            override suspend fun searchLive(query: String, fields0: List<String>, rows: Int, sort: String, page: Int): LiveResponseDto {
                fields += fields0
                return FakeArchiveService(liveDocs = { listOf(LiveDocDto("a")) }).searchLive(query, fields0, rows, sort, page)
            }
            override suspend fun searchFields(query: String, fields0: List<String>, rows: Int, sort: String): FieldsResponseDto {
                fields += fields0
                return FakeArchiveService(fieldDocs = { listOf(FieldDocDto("a")) }).searchFields(query, fields0, rows, sort)
            }
        }
        val service = RightsFilteredService(inner, { false }, { day }, hideAdult = { true })
        service.searchLive("q", listOf("identifier"), 10, "date desc")
        service.searchFields("q", listOf("title"), 10, "downloads desc")
        fields.forEach { assertThat(it).containsAtLeastElementsIn(AdultRule.FIELDS) }
    }

    @Test fun flaggedIdsUsesTheLookupService() = runTest {
        val filtered = FakeArchiveService()
        val lookup = FakeArchiveService(searchDocs = {
            listOf(SearchDocDto("saved1"), SearchDocDto("saved2", rating = listOf("TV-MA")))
        })
        val repo = ArchiveRepository(filtered, PlayableFileSelector(), SearchQueryBuilder(), lookupService = lookup)
        assertThat(repo.flaggedIds(listOf("saved1", "saved2"))).containsExactly("saved2")
        assertThat(filtered.queries).isEmpty()
        assertThat(repo.flaggedIds(emptyList())).isEmpty()
    }
}
