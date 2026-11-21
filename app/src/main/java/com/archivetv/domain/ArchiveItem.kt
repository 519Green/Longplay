package com.archivetv.domain

data class ArchiveItem(
    val identifier: String,
    val title: String,
    val year: String? = null,
    val description: String? = null,
    /** Watched fraction 0..1 for Continue Watching, else null. */
    val progress: Float? = null,
) {
    val thumbnailUrl: String get() = "https://archive.org/services/img/$identifier"
}
