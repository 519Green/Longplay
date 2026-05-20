package com.archivetv.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetadataResponseDto(
    val metadata: MetadataBodyDto? = null,
    val files: List<MetadataFileDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MetadataBodyDto(
    val title: String? = null,
    val description: String? = null,
    val year: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetadataFileDto(
    val name: String,
    val format: String? = null,
    val source: String? = null,
)
