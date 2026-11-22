package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.LiveDocDto
import com.archivetv.domain.Artist
import com.archivetv.domain.Concert
import com.archivetv.domain.Recording
import com.archivetv.domain.YearCount
import com.archivetv.domain.bestOf
import com.archivetv.domain.dateOf
import com.archivetv.domain.groupConcerts
import com.archivetv.domain.searchKey
import com.archivetv.domain.showPart
import com.archivetv.domain.yearCounts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Live Music Archive: artists, concerts grouped from recordings, and discovery rows. Live queries only. */
class LiveMusicRepository(private val service: InternetArchiveService) {
    private val artistFields = listOf("identifier", "title")
    // Not `collection`: search docs carry every user's fav- list (up to thousands
    // of entries), which made responses 4-24x larger. Artist pages already know
    // the artist id; discovery and search rows group by the creator name.
    private val recordingFields = listOf(
        "identifier", "title", "date", "venue", "coverage", "source",
        "avg_rating", "num_reviews", "downloads", "creator",
    )

    private val artistsLock = Mutex()
    private var allArtists: List<Artist>? = null

    /** Every LMA artist, fetched once per app session, sorted by [searchKey]. */
    suspend fun artists(): List<Artist> = artistsLock.withLock {
        allArtists ?: service.searchLive(LiveQueries.ARTISTS, artistFields, rows = 10_000, sort = "titleSorter asc")
            .response.docs.mapNotNull { it.toArtist() }
            .sortedBy { searchKey(it.name) }
            .also { allArtists = it }
    }

    suspend fun popularArtists(limit: Int = 20): List<Artist> =
        service.searchLive(LiveQueries.ARTISTS, artistFields, rows = limit, sort = "downloads desc")
            .response.docs.mapNotNull { it.toArtist() }

    /** The year of the artist's most-downloaded recording. */
    suspend fun startYear(artistId: String): Int? =
        service.searchLive(LiveQueries.artistRecordings(artistId), listOf("date"), rows = 1, sort = "downloads desc")
            .response.docs.firstOrNull()?.let { dateOf(it.date.firstOrNull()) }?.take(4)?.toIntOrNull()

    suspend fun years(artistId: String): List<YearCount> =
        yearCounts(
            service.searchLive(LiveQueries.artistRecordings(artistId), listOf("date"), rows = 25_000, sort = "date asc")
                .response.docs.mapNotNull { dateOf(it.date.firstOrNull()) },
        )

    suspend fun concerts(artistId: String, year: Int): List<Concert> =
        groupConcerts(recordingsFor(LiveQueries.artistYear(artistId, year), rows = 2000, sort = "date asc", artistId = artistId))
            .sortedWith(compareBy({ it.date }, { it.showPart }))

    /** Every recording of one concert, best first. */
    suspend fun recordings(artistId: String, date: String, showPart: String): List<Recording> =
        bestOf(
            recordingsFor(LiveQueries.concert(artistId, date), rows = 500, sort = "downloads desc", artistId = artistId)
                .filter { it.showPart == showPart },
        )

    suspend fun thisDay(month: Int, day: Int, limit: Int = 40): List<Concert> =
        topConcerts(LiveQueries.thisDay(month, day), "downloads desc", limit)

    suspend fun topRated(limit: Int = 40): List<Concert> =
        topConcerts(LiveQueries.TOP_RATED, "avg_rating desc", limit)

    suspend fun mostPlayed(limit: Int = 40): List<Concert> =
        topConcerts(LiveQueries.MOST_PLAYED, "downloads desc", limit)

    suspend fun searchArtists(text: String, limit: Int = 10): List<Artist> {
        val query = LiveQueries.artistSearch(text)
        if (query.isEmpty()) return emptyList()
        return service.searchLive(query, artistFields, rows = limit, sort = "downloads desc")
            .response.docs.mapNotNull { it.toArtist() }
    }

    /** The 100 most-downloaded matching recordings, grouped into concerts. */
    suspend fun searchConcerts(text: String, limit: Int = 20): List<Concert> {
        val query = LiveQueries.recordingSearch(text)
        if (query.isEmpty()) return emptyList()
        return groupConcerts(recordingsFor(query, rows = 100, sort = "downloads desc")).take(limit)
    }

    /** Fetches enough recordings to fill [limit] concerts after grouping. */
    private suspend fun topConcerts(query: String, sort: String, limit: Int): List<Concert> =
        groupConcerts(recordingsFor(query, rows = limit * 5, sort = sort)).take(limit)

    /** [artistId] is the artist collection when the query is per artist; null for discovery and search. */
    private suspend fun recordingsFor(query: String, rows: Int, sort: String, artistId: String? = null): List<Recording> =
        service.searchLive(query, recordingFields, rows = rows, sort = sort).response.docs.mapNotNull { it.toRecording(artistId) }

    private fun LiveDocDto.toArtist(): Artist? {
        val id = identifier ?: return null
        val name = title.map(HtmlText::toPlain).firstOrNull { it.isNotBlank() }?.trim() ?: id
        return Artist(id, name)
    }

    private fun LiveDocDto.toRecording(knownArtistId: String?): Recording? {
        val id = identifier ?: return null
        val day = dateOf(date.firstOrNull()) ?: return null
        val text = title.map(HtmlText::toPlain).firstOrNull { it.isNotBlank() } ?: id
        val name = creator.firstText()
            ?: text.substringBefore(" Live at ", missingDelimiterValue = "").trim().ifEmpty { null }
            ?: knownArtistId
            ?: return null
        // Grouping key: the artist collection when known, else the artist name.
        val artistId = knownArtistId ?: name
        return Recording(
            identifier = id,
            title = text,
            date = day,
            showPart = showPart(text),
            venue = venue.firstText(),
            city = coverage.firstText(),
            source = source.firstText(),
            rating = avgRating,
            reviews = numReviews ?: 0,
            downloads = downloads ?: 0,
            artistId = artistId,
            artistName = name,
        )
    }

    private fun List<String>.firstText(): String? =
        map(HtmlText::toPlain).firstOrNull { it.isNotBlank() }?.trim()
}
