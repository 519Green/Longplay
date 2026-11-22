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

/** Live Music Archive search: the recording and artist fields the Live Music screens use. */
@JsonClass(generateAdapter = true)
data class LiveResponseDto(val response: LiveBodyDto)

@JsonClass(generateAdapter = true)
data class LiveBodyDto(
    @Json(name = "numFound") val numFound: Int,
    val docs: List<LiveDocDto>,
)

@JsonClass(generateAdapter = true)
data class LiveDocDto(
    val identifier: String? = null,
    @StringOrList val title: List<String> = emptyList(),
    @StringOrList val date: List<String> = emptyList(),
    @StringOrList val venue: List<String> = emptyList(),
    @StringOrList val coverage: List<String> = emptyList(),
    @StringOrList val source: List<String> = emptyList(),
    @Json(name = "avg_rating") val avgRating: Double? = null,
    @Json(name = "num_reviews") val numReviews: Int? = null,
    val downloads: Int? = null,
    @StringOrList val collection: List<String> = emptyList(),
)
