package com.archivetv.data.config

import com.archivetv.domain.BrowseRow
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class BrowseConfigDto(val rows: List<BrowseRowDto>)

@JsonClass(generateAdapter = true)
data class BrowseRowDto(val title: String, val collectionId: String)

class BrowseConfigLoader {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(BrowseConfigDto::class.java)

    fun parse(json: String): List<BrowseRow> =
        adapter.fromJson(json)!!.rows.map { BrowseRow(it.title, it.collectionId) }
}
