package com.archivetv.domain

/** One playable file inside an Internet Archive item (a film, an episode, a chapter). */
data class PlayableFile(
    val identifier: String,
    val fileName: String,
    /** Display name: the file's own title, else a cleaned-up file name. */
    val title: String = fileName,
    val durationSec: Double? = null,
) {
    val url: String
        get() = "https://archive.org/download/$identifier/" +
            java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
}
