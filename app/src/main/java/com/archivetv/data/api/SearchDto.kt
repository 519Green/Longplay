package com.archivetv.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResponseDto(val response: SearchBodyDto)

@JsonClass(generateAdapter = true)
data class SearchBodyDto(
    @Json(name = "numFound") val numFound: Int,
    val docs: List<SearchDocDto>,
)

@JsonClass(generateAdapter = true)
data class SearchDocDto(
    val identifier: String,
    @StringOrList val title: List<String> = emptyList(),
    @StringOrList val year: List<String> = emptyList(),
    val mediatype: String? = null,
    @StringOrList val collection: List<String> = emptyList(),
)
