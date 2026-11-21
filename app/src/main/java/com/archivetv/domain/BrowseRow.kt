package com.archivetv.domain

/** A content row: a catalog leaf [id] with its rendered Lucene [query]. */
data class BrowseRow(
    val id: String,
    val title: String,
    val query: String,
)
