package com.archivetv.domain

import java.time.LocalDate

/** The rights-relevant metadata of one Archive item, as the search API returns it. */
data class RightsMeta(
    val licenseurl: String? = null,
    val years: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val sponsors: List<String> = emptyList(),
)

/**
 * The "public domain and openly licensed only" rule. An item passes when its own metadata shows at least one of:
 * a license URL, a year or date from 1880 to (current year - 96), or a US-government producer.
 * Generic over metadata; it names no title, studio or producer.
 */
object RightsRule {
    private const val FIRST_YEAR = 1880
    private const val TERM_YEARS = 96

    val FIELDS: List<String> = listOf("licenseurl", "year", "date", "creator", "publisher", "sponsor")

    private val govPrefix = Regex("""^\s*(U\.?\s?S\.?|United States)\b""", RegexOption.IGNORE_CASE)

    // Kinds of government organization, never names of producers.
    private val orgWord = Regex(
        """\b(?:Army|Navy|Air Force|Marine|Coast Guard|Department|Dept|Office|Bureau|Agency|Administration|""" +
            """Service|Commission|Corps|Survey|Board|Council|Institute|Center|Information|War|Government|""" +
            """Congress|Senate)s?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun cutoffYear(today: LocalDate): Int = today.year - TERM_YEARS

    fun isGovernmentProducer(name: String): Boolean =
        govPrefix.containsMatchIn(name) && orgWord.containsMatchIn(name)

    fun passes(meta: RightsMeta, today: LocalDate): Boolean {
        if (!meta.licenseurl.isNullOrBlank()) return true
        val cutoff = cutoffYear(today)
        val old = (meta.years + meta.dates).any { value ->
            val head = value.trim().take(4)
            head.length == 4 && head.all { it.isDigit() } && head.toInt() in FIRST_YEAR..cutoff
        }
        if (old) return true
        return (meta.creators + meta.publishers + meta.sponsors).any(::isGovernmentProducer)
    }

    /** Server-side clause. Looser than [passes] on purpose; results are re-checked with [passes]. */
    fun clause(today: LocalDate): String {
        val c = cutoffYear(today)
        val gov = """("U.S." OR "US" OR "United States")"""
        return "(licenseurl:* OR year:[$FIRST_YEAR TO $c] OR date:[$FIRST_YEAR-01-01 TO $c-12-31]" +
            " OR creator:$gov OR publisher:$gov OR sponsor:$gov)"
    }
}
