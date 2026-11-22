package com.archivetv.domain

import java.util.Locale

/** What Detail shows on its metadata line, read from `/metadata` (no extra requests). */
data class ItemFacts(
    /** Display year after [displayYear]; null when untrustworthy. */
    val year: String? = null,
    /** Length of the one file, or of all files together when [totalRuntime]. */
    val runtimeSec: Double? = null,
    val totalRuntime: Boolean = false,
    /** Mean of the review stars; null when nobody rated it. */
    val rating: Double? = null,
    val reviews: Int = 0,
    /** "Silent", "B&W", "Color". */
    val tags: List<String> = emptyList(),
)

private val titleYear = Regex("""[(\[]\s*((?:18|19|20)\d\d)\s*[)\]\-–]""")

/**
 * The year to show for an item (research spec 3.3). The `year` field is often
 * the upload year, so a year written in the title, "(1949)" or "[1974]", wins.
 * Otherwise the field is shown only when it falls between 1880 and 1999.
 */
fun displayYear(title: String, year: String?): String? {
    titleYear.find(title)?.let { return it.groupValues[1] }
    val y = year?.trim()?.take(4)?.toIntOrNull() ?: return null
    return if (y in 1880..1999) y.toString() else null
}

/** "silent" sound and the `color` key become short tags; anything unrecognised is dropped. */
fun itemTags(sound: String?, color: String?): List<String> = buildList {
    if (sound?.trim()?.lowercase() == "silent") add("Silent")
    val c = color?.trim()?.lowercase().orEmpty()
    when {
        c.isEmpty() -> Unit
        c.startsWith("b") || "black" in c || "white" in c -> add("B&W")
        c.startsWith("colo") -> add("Color")
    }
}

/** Mean of review stars; values outside 1..5 are ignored. */
fun averageStars(stars: List<Double>): Pair<Double?, Int> {
    val valid = stars.filter { it in 1.0..5.0 }
    return (if (valid.isEmpty()) null else valid.average()) to valid.size
}

/** 1530 s -> "25 min"; 5520 s -> "1 h 32 min". */
fun durationText(seconds: Double): String {
    val total = (seconds / 60).toInt().coerceAtLeast(1)
    return if (total < 60) "$total min" else "${total / 60} h ${total % 60} min"
}

/** "1940 · 1 h 31 min · ★ 4.2 (30) · B&W"; empty parts are skipped. */
fun metadataLine(facts: ItemFacts): String = listOfNotNull(
    facts.year,
    facts.runtimeSec?.let { durationText(it) + if (facts.totalRuntime) " total" else "" },
    facts.rating?.let { "★ " + String.format(Locale.US, "%.1f", it) + " (${facts.reviews})" },
).plus(facts.tags).joinToString(" · ")

/** Parent and community collections that make poor "More like this" sources. */
private val broadCollections = setOf(
    "movies", "moviesandfilms", "audio", "etree", "texts", "opensource_movies", "opensource",
    "opensource_audio", "hifidelity", "community", "stream_only", "additional_collections",
)

/** The collection a "More like this" row draws from: the item's first specific one. */
fun moreLikeThisCollection(collections: List<String>): String? =
    collections.firstOrNull { c ->
        c.isNotBlank() && c.lowercase() !in broadCollections && !c.startsWith("fav-")
    }
