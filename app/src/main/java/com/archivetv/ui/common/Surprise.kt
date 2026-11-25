package com.archivetv.ui.common

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.Catalog
import com.archivetv.domain.CategoryNode
import kotlin.random.Random

/** Catalog leaves offered as "Surprise me" doors on Home, in tile order. */
private val surpriseLeaves = listOf(
    "movies-popular" to "Feature film",
    "silent" to "Silent film",
    "film-noir" to "Film noir",
    "tv-popular" to "TV episode",
    "vintage-animation" to "Cartoon",
    "short-films" to "Short film",
    "newsreels" to "Newsreel",
    "edu-popular" to "Educational film",
    "commercials" to "Commercial",
    "home-movies" to "Home movie",
    "listen-radio-popular" to "Radio show",
)

/** The (label, leaf) doors this catalog has. */
fun surpriseDoors(catalog: Catalog): List<Pair<String, CategoryNode.Leaf>> =
    surpriseLeaves.mapNotNull { (id, label) -> catalog.leaf(id)?.let { label to it } }

/**
 * Picks one random item from a leaf. [fetch] returns a page in random order (through the filtered service);
 * the picker then avoids items it already showed for that leaf until every fetched item has had a turn.
 */
class SurprisePicker(
    private val fetch: suspend (CategoryNode.Leaf) -> List<ArchiveItem>,
    private val random: Random = Random.Default,
) {
    private val shown = mutableMapOf<String, MutableSet<String>>()

    suspend fun pick(leaf: CategoryNode.Leaf): ArchiveItem? {
        val items = fetch(leaf)
        if (items.isEmpty()) return null
        val seen = shown.getOrPut(leaf.id) { mutableSetOf() }
        val fresh = items.filter { it.identifier !in seen }.ifEmpty {
            seen.clear()
            items
        }
        return fresh.random(random).also { seen += it.identifier }
    }
}
