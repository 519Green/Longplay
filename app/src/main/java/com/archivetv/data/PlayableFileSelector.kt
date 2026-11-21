package com.archivetv.data

import com.archivetv.data.api.MetadataFileDto
import com.archivetv.domain.PlayableFile

class PlayableFileSelector {
    private val byExtensionRank = listOf(".mp4", ".m4v", ".webm", ".ogv", ".mov", ".mkv")

    /**
     * IA derivative formats, best first. "h.264" is the full-size derivative,
     * "MPEG4" is usually an uploaded original, "512Kb MPEG4" is a 240p fallback.
     * Files with any other format rank after these, by extension.
     */
    private val byFormatRank = listOf("h.264", "h.264 ia", "mpeg4", "512kb mpeg4")

    fun select(identifier: String, files: List<MetadataFileDto>): PlayableFile? {
        val best = files
            .filter { extensionRank(it) >= 0 }
            .sortedWith(
                compareBy<MetadataFileDto> { formatRank(it) }
                    .thenBy { extensionRank(it) }
                    .thenByDescending { it.height.firstOrNull()?.toIntOrNull() ?: 0 }
            )
            .firstOrNull() ?: return null
        return PlayableFile(identifier = identifier, fileName = best.name)
    }

    private fun extensionRank(f: MetadataFileDto): Int =
        byExtensionRank.indexOf("." + f.name.substringAfterLast('.', "").lowercase())

    private fun formatRank(f: MetadataFileDto): Int {
        val i = byFormatRank.indexOf(f.format?.trim()?.lowercase())
        return if (i >= 0) i else byFormatRank.size
    }
}
