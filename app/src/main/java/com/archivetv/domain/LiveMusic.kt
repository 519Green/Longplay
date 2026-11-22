package com.archivetv.domain

/** A Live Music Archive artist: a sub-collection of `etree`. */
data class Artist(val id: String, val name: String)

/** One recording (tape, soundboard, matrix) of a concert. Dates are "YYYY-MM-DD". */
data class Recording(
    val identifier: String,
    val title: String,
    val date: String,
    /** "", "early" or "late". */
    val showPart: String,
    val venue: String?,
    val city: String?,
    val source: String?,
    val rating: Double?,
    val reviews: Int,
    val downloads: Int,
    val artistId: String,
    val artistName: String,
)

/** One show: every recording of one artist on one date (and show part), with the best picked. */
data class Concert(
    val artistId: String,
    val artistName: String,
    val date: String,
    val showPart: String,
    val venue: String?,
    val city: String?,
    val best: Recording,
    val recordingCount: Int,
)

data class YearCount(val year: Int, val concerts: Int)

private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}""")

/** The date part of an IA date, never time-zone converted ("1977-05-26T00:00:00Z" -> "1977-05-26"). */
fun dateOf(raw: String?): String? = raw?.trim()?.let { isoDate.find(it)?.value }

fun showPart(title: String): String {
    val lower = title.lowercase()
    return when {
        "(early" in lower -> "early"
        "(late" in lower -> "late"
        else -> ""
    }
}

private val housekeeping = setOf("etree", "stream_only")

/** The artist collection of a recording: the first that is not `etree`, `stream_only` or a `fav-` list. */
fun artistIdOf(collections: List<String>): String? =
    collections.firstOrNull { it !in housekeeping && !it.startsWith("fav-") }

private const val PRIOR_RATING = 4.5
private const val PRIOR_WEIGHT = 10
private const val UNRATED = PRIOR_RATING - 0.001

/** Bayesian rating; unrated (null or 0) recordings sit just below the prior. */
fun score(r: Recording): Double {
    val rating = r.rating
    if (rating == null || rating <= 0.0) return UNRATED
    return (rating * r.reviews + PRIOR_RATING * PRIOR_WEIGHT) / (r.reviews + PRIOR_WEIGHT)
}

/** Best first: by [score], then by downloads. */
fun bestOf(recordings: List<Recording>): List<Recording> =
    recordings.sortedWith(compareByDescending<Recording> { score(it) }.thenByDescending { it.downloads })

/** Groups recordings into concerts, in order of each concert's first recording. */
fun groupConcerts(recordings: List<Recording>): List<Concert> =
    recordings.groupBy { Triple(it.artistId, it.date, it.showPart) }.map { (key, group) ->
        val best = bestOf(group).first()
        Concert(
            artistId = key.first, artistName = best.artistName, date = key.second, showPart = key.third,
            venue = best.venue, city = best.city, best = best, recordingCount = group.size,
        )
    }

/** Distinct dates per year, oldest year first. */
fun yearCounts(dates: List<String>): List<YearCount> =
    dates.distinct()
        .mapNotNull { it.take(4).toIntOrNull() }
        .groupingBy { it }.eachCount()
        .toSortedMap()
        .map { (year, n) -> YearCount(year, n) }

private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/** Lowercase letters and digits only, words separated by single spaces. */
fun searchKey(name: String): String = name.lowercase().replace(nonWord, " ").trim()

/** 'A'..'Z' from the first letter of [searchKey], else '#'. */
fun jumpLetter(name: String): Char {
    val c = searchKey(name).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}
