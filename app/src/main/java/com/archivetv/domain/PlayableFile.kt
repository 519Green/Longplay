package com.archivetv.domain

import java.util.Locale

private fun downloadUrl(identifier: String, fileName: String) =
    "https://archive.org/download/$identifier/" +
        java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

/** One playable file inside an Internet Archive item (a film, an episode, a chapter). */
data class PlayableFile(
    val identifier: String,
    val fileName: String,
    /** Display name: the file's own title, else a cleaned-up file name. */
    val title: String = fileName,
    val durationSec: Double? = null,
    /** Caption files that belong to this video, side-loaded into the player. */
    val subtitles: List<SubtitleFile> = emptyList(),
) {
    val url: String get() = downloadUrl(identifier, fileName)
}

/**
 * A SubRip or WebVTT file in the item. IA names speech-recognised captions
 * `<video>.asr.srt`, sometimes with a language tag (`<video>.en.asr.srt`).
 */
data class SubtitleFile(
    val identifier: String,
    val fileName: String,
    /** BCP 47 tag from the file name ("en", "es"); null when the name has none. */
    val language: String? = null,
    /** Machine transcription ("asr"), not a human-made caption file. */
    val auto: Boolean = false,
    val webVtt: Boolean = false,
) {
    val url: String get() = downloadUrl(identifier, fileName)

    /** "English", "English (auto)", "Captions (auto)". */
    val label: String
        get() {
            val name = language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
                ?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
                ?: "Captions"
            return if (auto) "$name (auto)" else name
        }
}
