package com.archivetv.data

/**
 * Converts the HTML fragments Internet Archive uses in titles and descriptions
 * into plain text for display. Pure Kotlin so it runs in JVM unit tests.
 */
object HtmlText {
    private val dropBlocks = Regex("(?is)<(script|style)\\b.*?</\\1\\s*>")
    private val lineBreak = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote)\\s*>|<(p|div|li|tr)\\b[^>]*>")
    private val tag = Regex("<[^>]+>")
    private val entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "copy" to "©", "eacute" to "é", "egrave" to "è",
    )

    fun toPlain(html: String): String {
        val text = html
            .replace(dropBlocks, "")
            .replace(lineBreak, "\n")
            .replace(blockEnd, "\n\n")
            .replace(tag, "")
            .replace(entity) { decode(it.groupValues[1]) ?: it.value }
            .replace(' ', ' ')
        return text.lines()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decode(body: String): String? = when {
        body.startsWith("#x", ignoreCase = true) ->
            body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        body.startsWith("#") ->
            body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> named[body]
    }
}
