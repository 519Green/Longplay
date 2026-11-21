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

    private val best = compareBy<MetadataFileDto> { formatRank(it) }
        .thenBy { extensionRank(it) }
        .thenByDescending { it.height.firstOrNull()?.toIntOrNull() ?: 0 }

    /** The first playable file, for single-file callers. */
    fun select(identifier: String, files: List<MetadataFileDto>): PlayableFile? =
        selectAll(identifier, files).firstOrNull()

    /**
     * Every distinct video in the item, in natural file-name order. IA stores
     * several derivatives of each upload (h.264, 512kb, Ogg) that point back to
     * it through `original`; each group contributes its best playable file.
     */
    fun selectAll(identifier: String, files: List<MetadataFileDto>): List<PlayableFile> =
        files
            .filter { extensionRank(it) >= 0 }
            .groupBy { groupKey(it) }
            .map { (_, group) -> group.sortedWith(best).first() }
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .map { f ->
                PlayableFile(
                    identifier = identifier,
                    fileName = f.name,
                    title = f.title.firstOrNull { it.isNotBlank() }?.trim() ?: displayName(f.name),
                    durationSec = parseDurationSec(f.length.firstOrNull()),
                )
            }

    /**
     * Files that are versions of the same video share a key: the original's
     * name for derivatives, else the file's own name; either way without its
     * extension or IA's "_512kb" suffix, so groups still form when `original`
     * metadata is missing.
     */
    private fun groupKey(f: MetadataFileDto): String {
        val base = if (f.source == "derivative" && f.original != null) f.original else f.name
        return base.substringBeforeLast('.').removeSuffix("_512kb").lowercase()
    }

    private fun extensionRank(f: MetadataFileDto): Int =
        byExtensionRank.indexOf("." + f.name.substringAfterLast('.', "").lowercase())

    private fun formatRank(f: MetadataFileDto): Int {
        val i = byFormatRank.indexOf(f.format?.trim()?.lowercase())
        return if (i >= 0) i else byFormatRank.size
    }
}

/** "G:/Videos/Show_Name - S01E01.mp4" -> "Show Name - S01E01". */
internal fun displayName(fileName: String): String =
    fileName.substringAfterLast('/')
        .substringBeforeLast('.')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Display labels for a list of episodes: drops the text every title shares
 * (usually the show name) up to the last " - " or ": " inside it, so
 * "Thundarr the Barbarian - S01E01 - Secret" shows as "S01E01 - Secret".
 */
fun episodeLabels(titles: List<String>): List<String> {
    if (titles.size < 2) return titles
    val shared = titles.reduce { acc, t -> acc.commonPrefixWith(t) }
    val cut = listOf(" - ", ": ").maxOf { sep ->
        shared.lastIndexOf(sep).let { if (it >= 0) it + sep.length else -1 }
    }
    if (cut <= 0) return titles
    val stripped = titles.map { it.substring(cut).trim() }
    return if (stripped.any { it.isEmpty() }) titles else stripped
}

/** IA `length` is seconds ("1500.53") or a clock ("25:30", "1:02:03"). */
fun parseDurationSec(raw: String?): Double? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    if (':' !in s) return s.toDoubleOrNull()
    val parts = s.split(':').map { it.toDoubleOrNull() ?: return null }
    return parts.fold(0.0) { acc, n -> acc * 60 + n }
}

/** Case-insensitive natural ordering: "Ep 2" < "Ep 10". */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val cmp = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return ca.size - cb.size
    }
}
