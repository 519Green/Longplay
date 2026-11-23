package com.archivetv.domain

/** The content-warning metadata of one Archive item, as the search API returns it. */
data class AdultMeta(
    val adult: List<String> = emptyList(),
    val nsfw: List<String> = emptyList(),
    val ratings: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
)

/**
 * The parental filter's yes/no: an item is adult when its own metadata says so (an adult or nsfw flag, an
 * adult rating, or an adult subject tag). The word lists name kinds of content, never a title or producer.
 */
object AdultRule {
    val FIELDS: List<String> = listOf("adult", "nsfw", "rating", "subject")

    private val falseFlags = setOf("", "false", "no", "0")
    private val adultRatings = setOf("R", "NC17", "X", "TVMA")
    private val adultTags = setOf(
        "adult", "adults only", "adult content", "nsfw", "porn", "pornography", "xxx", "erotica", "erotic",
        "nudity", "nude", "hentai", "sexploitation", "x-rated",
    )
    private val nonToken = Regex("[^A-Z0-9]+")

    fun isAdult(meta: AdultMeta): Boolean =
        (meta.adult + meta.nsfw).any { it.trim().lowercase() !in falseFlags } ||
            meta.ratings.any(::isAdultRating) ||
            meta.subjects.any { value -> value.split(';', ',').any { it.trim().lowercase() in adultTags } }

    /** "TV-MA;L" -> TVMA, L; "Rated R" -> RATED, R. Hyphens join so "NC-17" stays one token. */
    private fun isAdultRating(value: String): Boolean =
        value.uppercase().replace("-", "").split(nonToken).any { it in adultRatings }

    /**
     * Server-side clause: the flags, the ratings, and only the subject words that are unambiguous on their own.
     * "adult", "nudity" and "erotic" also occur in innocent tags, so they are left to the exact [isAdult] check.
     */
    fun clause(): String =
        "(NOT adult:* AND NOT nsfw:* AND NOT rating:(R OR \"NC-17\" OR X OR \"TV-MA\") AND " +
            "NOT subject:(porn OR pornography OR xxx OR hentai OR nsfw OR sexploitation OR erotica))"
}
