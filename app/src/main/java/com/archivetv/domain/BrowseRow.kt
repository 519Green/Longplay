package com.archivetv.domain

data class BrowseRow(
    val title: String,
    val collectionId: String,
    val items: List<ArchiveItem> = emptyList(),
)
