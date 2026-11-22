package com.archivetv.data

/** Lucene text for the Live Music Archive (`etree`) queries. */
object LiveQueries {
    private val builder = SearchQueryBuilder()

    const val ARTISTS = "collection:(etree) AND mediatype:collection"
    private const val RECORDINGS = "collection:(etree) AND mediatype:etree"
    const val TOP_RATED = "$RECORDINGS AND num_reviews:[50 TO 100000]"
    const val MOST_PLAYED = RECORDINGS

    fun artistRecordings(artistId: String): String = "${builder.anyOf(listOf(artistId))} AND mediatype:etree"

    fun artistYear(artistId: String, year: Int): String = "${artistRecordings(artistId)} AND year:$year"

    fun concert(artistId: String, date: String): String {
        val day = date.filter { it.isDigit() || it == '-' }
        return "${artistRecordings(artistId)} AND date:[$day TO $day]"
    }

    /** Recordings whose title carries "-MM-DD" (the API rejects wildcards on `date`). */
    fun thisDay(month: Int, day: Int): String = "$RECORDINGS AND title:\"-%02d-%02d\"".format(month, day)

    /** Empty when [text] has no searchable words. */
    fun artistSearch(text: String): String {
        val terms = builder.terms(text)
        return if (terms.isEmpty()) "" else "$ARTISTS AND title:($terms)"
    }

    /** Empty when [text] has no searchable words. */
    fun recordingSearch(text: String): String {
        val terms = builder.terms(text)
        return if (terms.isEmpty()) "" else "$RECORDINGS AND title:($terms)"
    }
}
