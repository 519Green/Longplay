package com.archivetv.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetadataResponseDto(
    val metadata: MetadataBodyDto? = null,
    val files: List<MetadataFileDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MetadataBodyDto(
    @StringOrList val title: List<String> = emptyList(),
    @StringOrList val description: List<String> = emptyList(),
    @StringOrList val year: List<String> = emptyList(),
    @StringOrList val collection: List<String> = emptyList(),
    val mediatype: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetadataFileDto(
    val name: String,
    val format: String? = null,
    val source: String? = null,
    @StringOrList val height: List<String> = emptyList(),
    /** For derivatives: the name of the file they were made from. */
    val original: String? = null,
    /** Duration, usually seconds ("1500.53"), sometimes "H:MM:SS". */
    @StringOrList val length: List<String> = emptyList(),
    /** Per-file title (common on audiobooks and radio episodes). */
    @StringOrList val title: List<String> = emptyList(),
    /** Track number on albums ("1", "04", "4/12"); unreliable elsewhere. */
    @StringOrList val track: List<String> = emptyList(),
)
