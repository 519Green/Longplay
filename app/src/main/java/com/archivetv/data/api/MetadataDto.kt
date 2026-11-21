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
)

@JsonClass(generateAdapter = true)
data class MetadataFileDto(
    val name: String,
    val format: String? = null,
    val source: String? = null,
)
