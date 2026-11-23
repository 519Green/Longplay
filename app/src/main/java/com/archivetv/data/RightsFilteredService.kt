package com.archivetv.data

import com.archivetv.data.api.FieldDocDto
import com.archivetv.data.api.FieldsResponseDto
import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.LiveDocDto
import com.archivetv.data.api.LiveResponseDto
import com.archivetv.data.api.MetadataResponseDto
import com.archivetv.data.api.SearchDocDto
import com.archivetv.data.api.SearchResponseDto
import com.archivetv.domain.AdultMeta
import com.archivetv.domain.AdultRule
import com.archivetv.domain.RightsMeta
import com.archivetv.domain.RightsRule
import java.time.LocalDate

/**
 * Every Archive search goes through here. Two independent filters:
 * - [openOnly]: the query is ANDed with [RightsRule.clause] and each doc re-checked with [RightsRule.passes]
 *   (the server match on "U.S." is loose);
 * - [hideAdult]: the query is ANDed with [AdultRule.clause] and each doc re-checked with [AdultRule.isAdult]
 *   (ambiguous subject words are client-only).
 * A page can come back a little short; numFound stays as the server reported it.
 * [metadata] is never filtered: opening an item by identifier always works.
 */
class RightsFilteredService(
    private val inner: InternetArchiveService,
    private val openOnly: () -> Boolean,
    private val today: () -> LocalDate = LocalDate::now,
    private val hideAdult: () -> Boolean = { false },
) : InternetArchiveService {

    /** The active filters for one request, read once so a toggle mid-request cannot mix modes. */
    private inner class Pass(val rights: Boolean, val adult: Boolean, val day: LocalDate) {
        val active get() = rights || adult

        fun query(q: String): String {
            val clauses = listOfNotNull(
                RightsRule.clause(day).takeIf { rights },
                AdultRule.clause().takeIf { adult },
            )
            return (listOf("($q)") + clauses).joinToString(" AND ")
        }

        fun fields(fields: List<String>): List<String> {
            val extra = (if (rights) RightsRule.FIELDS else emptyList()) + (if (adult) AdultRule.FIELDS else emptyList())
            return fields + extra.distinct().filterNot { it in fields }
        }

        fun keeps(rightsMeta: () -> RightsMeta, adultMeta: () -> AdultMeta): Boolean =
            (!rights || RightsRule.passes(rightsMeta(), day)) && (!adult || !AdultRule.isAdult(adultMeta()))
    }

    private fun pass() = Pass(openOnly(), hideAdult(), today())

    override suspend fun search(query: String, rows: Int, sort: String, page: Int): SearchResponseDto {
        val f = pass()
        if (!f.active) return inner.search(query, rows, sort, page)
        val result = inner.search(f.query(query), rows, sort, page)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { f.keeps(it::rightsMeta, it::adultMeta) }),
        )
    }

    override suspend fun searchLive(query: String, fields: List<String>, rows: Int, sort: String, page: Int): LiveResponseDto {
        val f = pass()
        if (!f.active) return inner.searchLive(query, fields, rows, sort, page)
        val result = inner.searchLive(f.query(query), f.fields(fields), rows, sort, page)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { f.keeps(it::rightsMeta, it::adultMeta) }),
        )
    }

    override suspend fun searchFields(query: String, fields: List<String>, rows: Int, sort: String): FieldsResponseDto {
        val f = pass()
        if (!f.active) return inner.searchFields(query, fields, rows, sort)
        val result = inner.searchFields(f.query(query), f.fields(fields), rows, sort)
        return result.copy(
            response = result.response.copy(docs = result.response.docs.filter { f.keeps(it::rightsMeta, it::adultMeta) }),
        )
    }

    override suspend fun metadata(identifier: String): MetadataResponseDto = inner.metadata(identifier)
}

fun SearchDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
fun LiveDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
fun FieldDocDto.rightsMeta() = RightsMeta(licenseurl.firstOrNull(), year, date, creator, publisher, sponsor)
fun SearchDocDto.adultMeta() = AdultMeta(adult, nsfw, rating, subject)
fun LiveDocDto.adultMeta() = AdultMeta(adult, nsfw, rating, subject)
fun FieldDocDto.adultMeta() = AdultMeta(adult, nsfw, rating, subject)
