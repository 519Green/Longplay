package com.archivetv.domain

/** A content row: a catalog leaf [id] with its rendered Lucene [query]. */
data class BrowseRow(
    val id: String,
    val title: String,
    val query: String,
    /** Fewer loaded items than this hides the row (seasonal rows use 8). */
    val minItems: Int = 1,
)
