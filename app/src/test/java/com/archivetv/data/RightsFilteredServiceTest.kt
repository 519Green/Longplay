package com.archivetv.data

import com.archivetv.data.api.FieldDocDto
import com.archivetv.data.api.FieldsBodyDto
import com.archivetv.data.api.FieldsResponseDto
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.LiveBodyDto
import com.archivetv.data.api.LiveDocDto
import com.archivetv.data.api.LiveResponseDto
import com.archivetv.data.api.MetadataResponseDto
import com.archivetv.data.api.SearchBodyDto
import com.archivetv.data.api.SearchDocDto
import com.archivetv.data.api.SearchResponseDto
import com.archivetv.domain.RightsRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Records what it is asked and returns one licensed doc and one private "U.S. ..." doc from every search. */
private class RecordingService : InternetArchiveService {
    val queries = mutableListOf<String>()
    val fieldLists = mutableListOf<List<String>>()
    val meta = MetadataResponseDto()

    override suspend fun search(query: String, rows: Int, sort: String, page: Int): SearchResponseDto {
        queries += query
        return SearchResponseDto(
            SearchBodyDto(
                numFound = 2,
                docs = listOf(
                    SearchDocDto("open", licenseurl = listOf("http://creativecommons.org/licenses/by/3.0/")),
                    SearchDocDto("closed", creator = listOf("U.S. Example Steel Corporation")),
                ),
            ),
        )
    }

    override suspend fun searchLive(query: String, fields: List<String>, rows: Int, sort: String, page: Int): LiveResponseDto {
        queries += query
        fieldLists += fields
        return LiveResponseDto(
            LiveBodyDto(2, listOf(LiveDocDto("open", date = listOf("1925-01-01")), LiveDocDto("closed", date = listOf("1975-01-01")))),
        )
    }

    override suspend fun searchFields(query: String, fields: List<String>, rows: Int, sort: String): FieldsResponseDto {
        queries += query
        fieldLists += fields
        return FieldsResponseDto(
            FieldsBodyDto(2, listOf(FieldDocDto("open", sponsor = listOf("U.S. Office of Example")), FieldDocDto("closed"))),
        )
    }

    override suspend fun metadata(identifier: String): MetadataResponseDto = meta
}

class RightsFilteredServiceTest {
    private val day = LocalDate.of(2026, 11, 22)
    private val inner = RecordingService()
    private fun service(on: Boolean) = RightsFilteredService(inner, { on }, { day })

    @Test fun onAddsTheClauseAndDropsFailingDocs() = runTest {
        val result = service(on = true).search("collection:(x)", rows = 10, sort = "downloads desc")
        assertThat(result.response.docs.map { it.identifier }).containsExactly("open")
        assertThat(result.response.numFound).isEqualTo(2)
        assertThat(inner.queries.single()).startsWith("(collection:(x)) AND (licenseurl:*")
    }

    @Test fun offPassesEverythingThrough() = runTest {
        val result = service(on = false).search("collection:(x)", rows = 10, sort = "downloads desc")
        assertThat(result.response.docs.map { it.identifier }).containsExactly("open", "closed")
        assertThat(inner.queries.single()).isEqualTo("collection:(x)")
    }

    @Test fun liveAndFieldsSearchesRequestRightsFields() = runTest {
        val s = service(on = true)
        val live = s.searchLive("q", listOf("identifier", "title"), rows = 10, sort = "date desc")
        val fields = s.searchFields("q", listOf("title"), rows = 10, sort = "downloads desc")
        assertThat(live.response.docs.map { it.identifier }).containsExactly("open")
        assertThat(fields.response.docs.map { it.identifier }).containsExactly("open")
        inner.fieldLists.forEach { assertThat(it).containsAtLeastElementsIn(RightsRule.FIELDS) }
        assertThat(inner.fieldLists[0]).containsAtLeast("identifier", "title")
        inner.queries.forEach { assertThat(it).startsWith("(q) AND (licenseurl:*") }
    }

    @Test fun offLeavesFieldListsAlone() = runTest {
        service(on = false).searchLive("q", listOf("identifier"), rows = 10, sort = "date desc")
        assertThat(inner.fieldLists.single()).containsExactly("identifier")
    }

    @Test fun metadataIsNeverFiltered() = runTest {
        assertThat(service(on = true).metadata("anything")).isSameInstanceAs(inner.meta)
    }
}
