package com.archivetv.data

import com.archivetv.data.api.FieldDocDto
import com.archivetv.data.api.FieldsResponseDto
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.LiveDocDto
import com.archivetv.data.api.LiveResponseDto
import com.archivetv.data.api.MetadataResponseDto
import com.archivetv.data.api.SearchDocDto
import com.archivetv.data.api.SearchResponseDto
import com.archivetv.domain.RightsMeta
import com.archivetv.domain.RightsRule
import java.time.LocalDate

/**
 * Every Archive search goes through here. When [openOnly] is on, the query is ANDed with [RightsRule.clause]
 * and each returned doc is re-checked with [RightsRule.passes] (the server match on "U.S." is loose).
 * A page can come back a little short; numFound stays as the server reported it.
 * [metadata] is never filtered: opening an item by identifier always works.
 */
class RightsFilteredService(
    private val inner: InternetArchiveService,
    private val openOnly: () -> Boolean,
    private val today: () -> LocalDate = LocalDate::now,
) : InternetArchiveService {

    private fun filtered(query: String) = "($query) AND ${RightsRule.clause(today())}"
    private fun withRightsFields(fields: List<String>) = fields + RightsRule.FIELDS.filterNot { it in fields }

    override suspend fun search(query: String, rows: Int, sort: String, page: Int): SearchResponseDto {
        if (!openOnly()) return inner.search(query, rows, sort, page)
        val day = today()
        val result = inner.search(filtered(query), rows, sort, page)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { RightsRule.passes(it.rightsMeta(), day) }),
        )
    }

    override suspend fun searchLive(query: String, fields: List<String>, rows: Int, sort: String, page: Int): LiveResponseDto {
        if (!openOnly()) return inner.searchLive(query, fields, rows, sort, page)
        val day = today()
        val result = inner.searchLive(filtered(query), withRightsFields(fields), rows, sort, page)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { RightsRule.passes(it.rightsMeta(), day) }),
        )
    }

    override suspend fun searchFields(query: String, fields: List<String>, rows: Int, sort: String): FieldsResponseDto {
        if (!openOnly()) return inner.searchFields(query, fields, rows, sort)
        val day = today()
        val result = inner.searchFields(filtered(query), withRightsFields(fields), rows, sort)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { RightsRule.passes(it.rightsMeta(), day) }),
        )
    }

    override suspend fun metadata(identifier: String): MetadataResponseDto = inner.metadata(identifier)
}

fun SearchDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
fun LiveDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
fun FieldDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
