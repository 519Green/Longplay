package com.archivetv.domain

/**
 * Rules that build a catalog group from Internet Archive metadata instead of
 * hand-picked names (owner rule, 2026-11-21). The rules name collections,
 * fields and thresholds; never shows, studios or sponsors.
 */
sealed interface DeriveRule {
    val limit: Int

    /** Series found by grouping episode titles on their shared prefix. */
    data class TitleSeries(
        val collections: List<String>,
        val mediatype: String,
        val sample: Int,
        val minItems: Int,
        override val limit: Int,
    ) : DeriveRule

    /** Producers or sponsors found by counting a metadata field ("creator" or "sponsor"). */
    data class FieldValues(
        val field: String,
        val collections: List<String>,
        val mediatype: String,
        val sample: Int,
        val minItems: Int,
        override val limit: Int,
    ) : DeriveRule

    /** The archive's own sub-collections of [parent]. */
    data class Subcollections(val parent: String, val mediatype: String, override val limit: Int) : DeriveRule
}

/** One tile of a derived group. [key] is route-safe (`[a-z0-9-]`). */
data class DerivedEntry(val key: String, val label: String, val query: CategoryQuery)

data class TitleDoc(val title: String, val downloads: Int)
data class CollectionDoc(val identifier: String, val title: String, val downloads: Int)

private val nonSlug = Regex("[^a-z0-9]+")

fun slug(text: String): String = text.lowercase().replace(nonSlug, "-").trim('-')

// ---- Title series ----

private val seriesSeparator = Regex(
    """\s+[-–—]\s+|:|\(|\[|\||\bS\d+\s*E\d+|\bSeason\b|\bEpisode\b|\bEp\.?\s*\d|#\d""",
    RegexOption.IGNORE_CASE,
)
private val quoteChars = charArrayOf('\'', '"', '‘', '’', '“', '”')
private val leadingArticle = Regex("^(the|a)\\s+")
private val spaces = Regex("\\s+")

/** The display part of an episode title before its first separator, quotes trimmed. */
private fun seriesPrefix(title: String): String {
    val cut = seriesSeparator.find(title)?.range?.first ?: title.length
    return title.substring(0, cut).trim().trim(*quoteChars).trim()
}

/** Words of the prefix, lowercase, punctuation dropped ("Climax!" and "Climax" are one series). */
fun seriesKey(title: String): String {
    val words = seriesPrefix(title).lowercase().split(nonWord).filter { it.isNotEmpty() }
    return (if (words.size > 1 && words.first() in setOf("the", "a")) words.drop(1) else words).joinToString(" ")
}

private fun seriesEntriesRanked(docs: List<TitleDoc>, rule: DeriveRule.TitleSeries): List<DerivedEntry> =
    docs.groupBy { seriesKey(it.title) }
        .filterKeys { it.isNotEmpty() }
        .filterValues { it.size >= rule.minItems }
        .map { (key, group) ->
            val label = mostFrequent(group.map { seriesPrefix(it.title) })
            Triple(key, label, group.sumOf { it.downloads })
        }
        .sortedWith(compareByDescending<Triple<String, String, Int>> { it.third }.thenBy { it.second })
        .take(rule.limit)
        .map { (key, label, _) ->
            DerivedEntry(
                key = slug(key),
                label = displayLabel(label),
                query = CategoryQuery(collections = rule.collections, titleAny = listOf(label), mediatype = rule.mediatype),
            )
        }

// ---- Field values (creator, sponsor) ----

private val nonNames = setOf("unknown", "various", "anonymous", "public domain", "pd", "n/a", "none", "newsreel")
private val stopWords = setOf(
    "films", "film", "inc", "incorporated", "corporation", "corp", "company", "co", "division", "motor",
    "productions", "production", "pictures", "organization", "instructional", "educational", "the",
)
private val parenAfterFirstWord = Regex("""^(\S+)\s+\(([^)]+)\)(.*)$""")
private val nonWord = Regex("[^\\p{L}\\p{N}]+")

fun valueKey(value: String): String {
    val lower = value.trim().lowercase()
    if (lower in nonNames || "internet archive" in lower) return ""
    // "Firm Name, Inc." loses its tail; "Surname, Forename" (one word before the comma) stays whole.
    val head = if (lower.substringBefore(',').trim().split(spaces).size > 1) lower.substringBefore(',') else lower
    val reordered = parenAfterFirstWord.matchEntire(head)?.let { m ->
        "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
    } ?: head
    return reordered.replace("ae", "e")
        .split(nonWord)
        .filter { it.isNotEmpty() && it !in stopWords }
        .joinToString(" ")
}

private fun valueEntriesRanked(values: List<String>, rule: DeriveRule.FieldValues): List<DerivedEntry> {
    // Spellings grouped by key; keys with the same tokens in another order are one group.
    val byKey = values.map { it.trim() }.filter { it.isNotEmpty() }
        .groupBy { valueKey(it) }.filterKeys { it.isNotEmpty() }
    val canonical = byKey.keys.groupBy { it.split(' ').sorted() }
        .values.associateBy({ it }, { keys -> keys.maxBy { byKey.getValue(it).size } })
    val groups = mutableMapOf<String, MutableList<String>>()
    canonical.forEach { (keys, root) -> keys.forEach { groups.getOrPut(root) { mutableListOf() } += byKey.getValue(it) } }

    // A shorter key joins a longer one only when every key extending it ends in the same group.
    val parent = mutableMapOf<String, String>()
    fun root(k: String): String = parent[k]?.let(::root) ?: k
    val tokens = groups.keys.associateWith { it.split(' ') }
    for (k in groups.keys.sortedByDescending { tokens.getValue(it).size }) {
        val t = tokens.getValue(k)
        val roots = groups.keys
            .filter { other -> tokens.getValue(other).size > t.size && tokens.getValue(other).subList(0, t.size) == t }
            .map(::root).distinct()
        // A one-word key is too vague to claim a longer name ("Union" is not "Union Pacific").
        if (roots.size == 1 && t.size > 1) parent[k] = roots.single()
    }
    val merged = groups.entries.groupBy({ root(it.key) }, { it.value }).mapValues { (_, lists) -> lists.flatten() }

    return merged.filterValues { it.size >= rule.minItems }
        .map { (key, spellings) -> Triple(key, mostFrequent(spellings), spellings) }
        .sortedWith(compareByDescending<Triple<String, String, List<String>>> { it.third.size }.thenBy { it.second })
        .take(rule.limit)
        .map { (key, label, spellings) ->
            val distinct = spellings.distinct().sorted()
            val base = CategoryQuery(collections = rule.collections, mediatype = rule.mediatype)
            DerivedEntry(
                key = slug(key),
                label = displayLabel(label),
                query = if (rule.field == "sponsor") base.copy(sponsorAny = distinct) else base.copy(creatorAny = distinct),
            )
        }
}

// ---- Sub-collections ----

private fun subcollectionEntriesRanked(docs: List<CollectionDoc>, rule: DeriveRule.Subcollections): List<DerivedEntry> =
    docs.filter { !it.identifier.startsWith("fav-") && it.downloads > 0 }
        .sortedByDescending { it.downloads }
        .take(rule.limit)
        .map {
            DerivedEntry(
                key = slug(it.identifier),
                label = it.title.ifBlank { it.identifier },
                query = CategoryQuery(collections = listOf(it.identifier), mediatype = rule.mediatype),
            )
        }

private val minorWords = setOf("a", "an", "and", "the", "of", "in", "on", "at", "for", "to", "or", "with")

/**
 * A metadata value as a tile label: library-style inverted names read naturally
 * ("Smith (John) Productions" -> "John Smith Productions"), and values in all
 * capitals, all lower case or sentence case get title case. Queries keep the raw value.
 */
fun displayLabel(raw: String): String {
    val text = parenAfterFirstWord.matchEntire(raw.trim())?.let { m ->
        "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
    } ?: raw.trim()
    val words = text.split(' ').filter { it.isNotEmpty() }
    val letters = text.filter { it.isLetter() }
    // A single all-caps word is usually an acronym (an agency's initials); leave it.
    val allUpper = words.size > 1 && letters.isNotEmpty() && letters.all { it.isUpperCase() }
    val allLower = letters.isNotEmpty() && letters.all { it.isLowerCase() }
    val sentenceCase = words.size > 1 && words.drop(1).all { w -> w.first().let { !it.isLetter() || it.isLowerCase() } }
    if (!allUpper && !allLower && !sentenceCase) return text
    return words.mapIndexed { i, w ->
        val lower = w.lowercase()
        if (i > 0 && lower in minorWords) lower else lower.replaceFirstChar { it.titlecase() }
    }.joinToString(" ")
}

/** Route keys must be unique and non-empty: "" becomes "entry", repeats get "-2", "-3". */
private fun List<DerivedEntry>.withUniqueKeys(): List<DerivedEntry> {
    val used = mutableSetOf<String>()
    return map { e ->
        val base = e.key.ifEmpty { "entry" }
        var key = base
        var n = 2
        while (!used.add(key)) key = "$base-${n++}"
        e.copy(key = key)
    }
}

/** The most common value; ties go to the alphabetically first. */
private fun mostFrequent(values: List<String>): String =
    values.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first().key

fun seriesEntries(docs: List<TitleDoc>, rule: DeriveRule.TitleSeries): List<DerivedEntry> =
    seriesEntriesRanked(docs, rule).withUniqueKeys()

fun valueEntries(values: List<String>, rule: DeriveRule.FieldValues): List<DerivedEntry> =
    valueEntriesRanked(values, rule).withUniqueKeys()

fun subcollectionEntries(docs: List<CollectionDoc>, rule: DeriveRule.Subcollections): List<DerivedEntry> =
    subcollectionEntriesRanked(docs, rule).withUniqueKeys()
