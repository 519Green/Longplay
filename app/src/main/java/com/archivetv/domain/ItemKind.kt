package com.archivetv.domain

/** How an item plays: video in the full-screen player, or audio through the playback service. */
enum class ItemKind { VIDEO, SPOKEN, MUSIC }

/** Collections whose audio items are music rather than spoken word. */
private val musicCollections = setOf("audio_music", "78rpm", "georgeblood", "album_recordings", "hifidelity", "etree")

fun itemKind(collections: List<String>, hasVideo: Boolean): ItemKind = when {
    hasVideo -> ItemKind.VIDEO
    collections.any { it in musicCollections } -> ItemKind.MUSIC
    else -> ItemKind.SPOKEN
}
