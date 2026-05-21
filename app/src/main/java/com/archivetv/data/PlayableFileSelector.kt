package com.archivetv.data

import com.archivetv.data.api.MetadataFileDto
import com.archivetv.domain.PlayableFile

class PlayableFileSelector {
    private val byExtensionRank = listOf(".mp4", ".m4v", ".webm", ".ogv", ".mov", ".mkv")

    fun select(identifier: String, files: List<MetadataFileDto>): PlayableFile? {
        val ranked = files
            .mapNotNull { f ->
                val ext = f.name.substringAfterLast('.', "").lowercase()
                val rank = byExtensionRank.indexOf(".$ext")
                if (rank >= 0) rank to f else null
            }
            .sortedBy { it.first }
        val best = ranked.firstOrNull()?.second ?: return null
        return PlayableFile(identifier = identifier, fileName = best.name)
    }
}
