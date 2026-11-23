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

/** A hand-written service: answers each search by query text and records every query it sees. */
class FakeArchiveService(
    private val searchDocs: (String) -> List<SearchDocDto> = { emptyList() },
    private val liveDocs: (String) -> List<LiveDocDto> = { emptyList() },
    private val fieldDocs: (String) -> List<FieldDocDto> = { emptyList() },
) : InternetArchiveService {
    val queries = mutableListOf<String>()

    override suspend fun search(query: String, rows: Int, sort: String, page: Int): SearchResponseDto {
        queries += query
        val docs = searchDocs(query)
        return SearchResponseDto(SearchBodyDto(docs.size, docs))
    }

    override suspend fun searchLive(query: String, fields: List<String>, rows: Int, sort: String, page: Int): LiveResponseDto {
        queries += query
        val docs = liveDocs(query)
        return LiveResponseDto(LiveBodyDto(docs.size, docs))
    }

    override suspend fun searchFields(query: String, fields: List<String>, rows: Int, sort: String): FieldsResponseDto {
        queries += query
        val docs = fieldDocs(query)
        return FieldsResponseDto(FieldsBodyDto(docs.size, docs))
    }

    override suspend fun metadata(identifier: String): MetadataResponseDto = MetadataResponseDto()
}
