package com.archivetv.data

class SearchQueryBuilder {
    private val videoScope = "mediatype:(movies OR tv)"

    fun collection(collectionId: String): String =
        "collection:($collectionId) AND $videoScope"

    fun userSearch(text: String): String =
        "(${text.trim()}) AND $videoScope"
}
