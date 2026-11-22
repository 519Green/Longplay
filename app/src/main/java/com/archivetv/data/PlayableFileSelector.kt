package com.archivetv.data

import com.archivetv.data.api.MetadataFileDto
import com.archivetv.domain.PlayableFile
import com.archivetv.domain.SubtitleFile

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
     * Every distinct video in the item, in natural file-name order; if the item
     * has no video, every distinct audio file instead (see [selectAudio]). IA
     * stores several derivatives of each upload (h.264, 512kb, Ogg) that point
     * back to it through `original`; each group contributes its best file.
     */
    fun selectAll(identifier: String, files: List<MetadataFileDto>): List<PlayableFile> =
        if (hasVideo(files)) selectVideo(identifier, files) else selectAudio(identifier, files)

    fun hasVideo(files: List<MetadataFileDto>): Boolean = files.any { extensionRank(it) >= 0 }

    private fun selectVideo(identifier: String, files: List<MetadataFileDto>): List<PlayableFile> {
        val videos = files
            .filter { extensionRank(it) >= 0 }
            .groupBy { groupKey(it) }
            .map { (key, group) -> key to group.sortedWith(best).first() }
            .sortedWith(compareBy(NaturalOrder) { it.second.name })
        val captions = captionFiles(identifier, files)
        val byVideo = captions.groupBy { (stems, _) ->
            videos.firstOrNull { (key, f) -> stems.any { it == key || it == stem(f.name) } }?.first
        }
        return videos.map { (key, f) ->
            // An item with one video owns every caption file, whatever its name.
            val own = if (videos.size == 1) captions else byVideo[key].orEmpty()
            f.toPlayable(identifier, f.title).copy(subtitles = own.map { it.second })
        }
    }

    /**
     * SubRip and WebVTT files, each with the video stems its name could
     * belong to (with and without a language tag). When one caption exists in
     * both formats, SubRip wins.
     */
    private fun captionFiles(identifier: String, files: List<MetadataFileDto>): List<Pair<List<String>, SubtitleFile>> =
        files.mapNotNull { f ->
            val lower = f.name.lowercase()
            val vtt = lower.endsWith(".vtt") || f.format.equals("Web Video Text Tracks", ignoreCase = true)
            val srt = lower.endsWith(".srt") || f.format.equals("SubRip", ignoreCase = true)
            if (!vtt && !srt) return@mapNotNull null
            var base = lower.substringBeforeLast('.')
            val auto = base.endsWith(".asr")
            base = base.removeSuffix(".asr")
            val tag = languageTag.find(base)
            // Only a tag Locale knows ("en", "pt-br"), not a word like ".dvd".
            val language = tag?.groupValues?.get(1)?.takeIf { t ->
                java.util.Locale.forLanguageTag(t).getDisplayLanguage(java.util.Locale.ENGLISH)
                    .let { it.isNotBlank() && !it.equals(t.substringBefore('-'), ignoreCase = true) }
            }
            val stems = listOfNotNull(base, tag?.let { base.substring(0, it.range.first) })
            val caption = SubtitleFile(identifier, f.name, language, auto, webVtt = !srt)
            Triple(stems, caption, srt)
        }
            .groupBy { (stems, c, _) -> Triple(stems.last(), c.language, c.auto) }
            .map { (_, same) -> same.maxBy { if (it.third) 1 else 0 } }
            .map { (stems, c, _) -> stems to c }

    private val languageTag = Regex("""\.([a-z]{2,3}(?:-[a-z0-9]{2,8})?)$""")

    private fun stem(name: String) =
        name.substringBeforeLast('.').removeSuffix("_512kb").lowercase()

    private val audioExtensions = listOf(".mp3", ".ogg", ".m4a", ".flac")
    private val audioFormatRank = listOf("vbr mp3", "128kbps mp3", "64kbps mp3")

    private fun audioRank(f: MetadataFileDto): Int {
        val byFormat = audioFormatRank.indexOf(f.format?.trim()?.lowercase())
        if (byFormat >= 0) return byFormat
        return audioFormatRank.size + audioExtensions.indexOf(extension(f))
    }

    /**
     * Chapters, episodes or tracks. MP3 beats Ogg, M4A and FLAC (bandwidth);
     * the whole-book `.m4b` LibriVox adds is used only when nothing else plays.
     * Album tracks follow `track` when every file has a distinct one, since
     * file names are often alphabetical rather than in play order.
     */
    private fun selectAudio(identifier: String, files: List<MetadataFileDto>): List<PlayableFile> {
        val groups = files.filter { extension(it) in audioExtensions }.groupBy { groupKey(it) }.values
        if (groups.isEmpty()) {
            return files.filter { extension(it) == ".m4b" }
                .sortedWith(compareBy(NaturalOrder) { it.name })
                .map { it.toPlayable(identifier, it.title) }
        }
        val picked = groups.map { g -> g.minBy(::audioRank) to g.flatMap { it.title } }
        val tracks = picked.map { (f, _) -> trackNumber(f) }
        val byTrack = tracks.none { it == null } && tracks.toSet().size == tracks.size
        val ordered = if (byTrack) {
            picked.sortedBy { (f, _) -> trackNumber(f) }
        } else {
            picked.sortedWith(compareBy(NaturalOrder) { (f, _) -> f.name })
        }
        return ordered.map { (f, titles) -> f.toPlayable(identifier, f.title + titles) }
    }

    private fun trackNumber(f: MetadataFileDto): Int? =
        f.track.firstOrNull()?.let { leadingNumber.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private val leadingNumber = Regex("^\\s*(\\d+)")

    private fun MetadataFileDto.toPlayable(identifier: String, titles: List<String>) = PlayableFile(
        identifier = identifier,
        fileName = name,
        title = titles.firstOrNull { it.isNotBlank() }?.trim() ?: displayName(name),
        durationSec = parseDurationSec(length.firstOrNull()),
    )

    private fun extension(f: MetadataFileDto) = "." + f.name.substringAfterLast('.', "").lowercase()

    /**
     * Files that are versions of the same video share a key: the original's
     * name for derivatives, else the file's own name; either way without its
     * extension or IA's "_512kb" suffix, so groups still form when `original`
     * metadata is missing.
     */
    private fun groupKey(f: MetadataFileDto): String {
        val base = if (f.source == "derivative" && f.original != null) f.original else f.name
        return base.substringBeforeLast('.').removeSuffix("_512kb").removeSuffix("_64kb").lowercase()
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
