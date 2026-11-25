package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.SearchDocDto
import com.archivetv.domain.AdultMeta
import com.archivetv.domain.AdultRule
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.ItemFacts
import com.archivetv.domain.averageStars
import com.archivetv.domain.displayYear
import com.archivetv.domain.itemTags
import com.archivetv.domain.ItemKind
import com.archivetv.domain.LiveInfo
import com.archivetv.domain.artistIdOf
import com.archivetv.domain.dateOf
import com.archivetv.domain.showPart
import com.archivetv.domain.itemKind
import com.archivetv.domain.PlayableFile

/** An item and its playable files in play order (one for a film, many for a series). */
data class ItemDetail(
    val item: ArchiveItem,
    val files: List<PlayableFile>,
    val kind: ItemKind = ItemKind.VIDEO,
    val isLibriVox: Boolean = false,
    val live: LiveInfo? = null,
    val facts: ItemFacts = ItemFacts(),
    /** The item's collections, in metadata order. */
    val collections: List<String> = emptyList(),
)

data class BrowsePage(val items: List<ArchiveItem>, val total: Int)

enum class BrowseSort(val label: String, val param: String) {
    POPULAR("Popular", "downloads desc"),
    /** Rating alone puts one-review titles first, so ties go to the most reviewed. */
    BEST_RATED("Best rated", "avg_rating desc,num_reviews desc"),
    MOST_REVIEWED("Most reviewed", "num_reviews desc"),
    TRENDING("Trending", "week desc"),
    RECENT("Recently added", "addeddate desc"),
    OLDEST("Oldest first", "year asc"),
    /** Archive's shuffle; the order holds for a few minutes, so paging stays consistent. */
    RANDOM("Random", "random"),
}

class ArchiveRepository(
    private val service: InternetArchiveService,
    private val fileSelector: PlayableFileSelector,
    private val queryBuilder: SearchQueryBuilder,
    /** Unfiltered: saved items (Continue Watching, My List) keep their titles whatever the rights setting. */
    private val lookupService: InternetArchiveService = service,
    /** The parental filter: [detail] refuses flagged items while it is on. */
    private val hideAdult: () -> Boolean = { false },
) {
    /** One page of a rendered catalog query (pages start at 1). */
    suspend fun browse(query: String, sort: BrowseSort, page: Int, rows: Int): BrowsePage {
        val body = service.search(query = query, rows = rows, sort = sort.param, page = page).response
        return BrowsePage(body.docs.map { it.toItem() }, body.numFound)
    }

    /** Result count only (rows=0), for facet chips. */
    suspend fun count(query: String): Int =
        service.search(query = query, rows = 0, sort = BrowseSort.POPULAR.param).response.numFound

    suspend fun search(text: String, limit: Int): List<ArchiveItem> {
        val query = queryBuilder.userSearch(text)
        if (query.isEmpty()) return emptyList()
        return service.search(
            query = query,
            rows = limit,
            sort = "downloads desc",
        ).response.docs.map { it.toItem() }
    }

    /** One Search chip's Watch ([video]) or Listen results, most downloaded first, with the total. */
    suspend fun searchScoped(scope: com.archivetv.domain.SearchScope, text: String, video: Boolean, limit: Int = 40): BrowsePage {
        val query = queryBuilder.scoped(scope, text, video)
        if (query.isEmpty()) return BrowsePage(emptyList(), 0)
        return browse(query, BrowseSort.POPULAR, page = 1, rows = limit)
    }

    suspend fun searchAudio(text: String, limit: Int): List<ArchiveItem> {
        val query = queryBuilder.audioSearch(text)
        if (query.isEmpty()) return emptyList()
        return service.search(query = query, rows = limit, sort = "downloads desc").response.docs.map { it.toItem() }
    }

    /** Popular items from [collection] of the same [mediatype], without [exclude] ("More like this"). */
    suspend fun moreLikeThis(collection: String, mediatype: String, exclude: String, limit: Int = 20): List<ArchiveItem> {
        val id = exclude.replace("\"", "")
        val query = queryBuilder.anyOf(listOf(collection)) + " AND mediatype:$mediatype AND NOT identifier:\"$id\""
        return browse(query, BrowseSort.POPULAR, page = 1, rows = limit).items.filter { it.identifier != exclude }
    }

    /** Looks up titles and years for known identifiers in one request. */
    /** Saved identifiers whose own metadata marks them adult ([AdultRule]); unfiltered lookup, one request. */
    suspend fun flaggedIds(ids: List<String>): Set<String> =
        // Chunked: a long My List in one identifier query would make an oversized URL.
        ids.distinct().chunked(LOOKUP_CHUNK).flatMapTo(HashSet()) { chunk ->
            lookupService.search(query = identifierQuery(chunk), rows = chunk.size, sort = "downloads desc")
                .response.docs.filter { AdultRule.isAdult(it.adultMeta()) }
                .map { it.identifier }
        }

    private fun identifierQuery(ids: List<String>) =
        ids.joinToString(" OR ", prefix = "identifier:(", postfix = ")") { "\"" + it.replace("\"", "") + "\"" }

    suspend fun itemsByIds(ids: List<String>): Map<String, ArchiveItem> {
        if (ids.isEmpty()) return emptyMap()
        return lookupService.search(query = identifierQuery(ids), rows = ids.size, sort = "downloads desc")
            .response.docs.map { it.toItem() }
            .associateBy { it.identifier }
    }

    suspend fun detail(identifier: String): ItemDetail {
        val dto = service.metadata(identifier)
        val meta = dto.metadata
        // Every way into a title (Detail, the player, audio, the hero) loads it here, so a flagged item stays
        // closed even when it is still on screen (a saved row filled while offline, a restored queue).
        if (meta != null && hideAdult() && AdultRule.isAdult(AdultMeta(meta.adult, meta.nsfw, meta.rating, meta.subject))) {
            throw HiddenByParentalFilter()
        }
        val description = meta?.description.orEmpty()
            .map(HtmlText::toPlain)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val item = ArchiveItem(
            identifier = identifier,
            title = cleanTitle(meta?.title.orEmpty(), identifier),
            // Some items carry only `date` ("1964" or "1964-05-01"); search derives year from it too.
            year = meta?.year?.firstOrNull { it.isNotBlank() }
                ?: meta?.date?.firstOrNull { it.isNotBlank() }?.trim()?.take(4)?.takeIf { it.all(Char::isDigit) },
            description = description.ifBlank { null },
        )
        val collections = meta?.collection.orEmpty()
        val files = fileSelector.selectAll(identifier, dto.files)
        val (rating, reviews) = averageStars(stars(dto.reviews))
        val durations = files.mapNotNull { it.durationSec }
        val facts = ItemFacts(
            year = displayYear(item.title, item.year),
            // A total is only honest when every file reports its length.
            runtimeSec = durations.takeIf { it.isNotEmpty() && it.size == files.size }?.sum(),
            totalRuntime = files.size > 1,
            rating = rating,
            reviews = reviews,
            tags = itemTags(meta?.sound?.firstOrNull(), meta?.color?.firstOrNull()),
        )
        return ItemDetail(
            item = item,
            files = files,
            facts = facts,
            collections = collections,
            kind = itemKind(collections, fileSelector.hasVideo(dto.files)),
            isLibriVox = "librivoxaudio" in collections,
            live = if (meta?.mediatype == "etree") liveInfo(collections, meta, item.title) else null,
        )
    }

    /** Star values from the loosely typed `reviews` array. */
    private fun stars(reviews: Any?): List<Double> =
        (reviews as? List<*>).orEmpty().mapNotNull { r ->
            when (val v = (r as? Map<*, *>)?.get("stars")) {
                is Number -> v.toDouble()
                is String -> v.trim().toDoubleOrNull()
                else -> null
            }
        }

    private fun liveInfo(collections: List<String>, meta: com.archivetv.data.api.MetadataBodyDto, title: String): LiveInfo? {
        val artistId = artistIdOf(collections) ?: return null
        val date = dateOf(meta.date.firstOrNull()) ?: return null
        fun first(values: List<String>) = values.map(HtmlText::toPlain).firstOrNull { it.isNotBlank() }?.trim()
        return LiveInfo(artistId, date, showPart(title), first(meta.venue), first(meta.coverage))
    }

    private fun SearchDocDto.toItem() = ArchiveItem(
        identifier = identifier,
        title = cleanTitle(title, identifier),
        year = year.firstOrNull { it.isNotBlank() },
        isAudio = mediatype == "audio" || mediatype == "etree",
        isCommunity = collection.any { it in communityCollections },
    )

    private val communityCollections = setOf("opensource_movies", "opensource", "hifidelity")

    private fun cleanTitle(titles: List<String>, identifier: String): String =
        titles.map(HtmlText::toPlain).firstOrNull { it.isNotBlank() } ?: identifier
}

/** Saved-item identifier lookups ask for at most this many identifiers per request. */
private const val LOOKUP_CHUNK = 50

/** Thrown by [ArchiveRepository.detail] for an item the parental filter hides. */
class HiddenByParentalFilter : Exception("Hidden by the parental filter")

/** What Detail and the player say when the parental filter blocks a title. */
const val PARENTAL_BLOCK_TEXT = "Hidden by the parental filter. Turn it off in Settings to open this title."
