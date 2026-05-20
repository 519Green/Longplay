package com.archivetv.domain

data class PlayableFile(
    val identifier: String,
    val fileName: String,
) {
    val url: String
        get() = "https://archive.org/download/$identifier/" +
            java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
}
