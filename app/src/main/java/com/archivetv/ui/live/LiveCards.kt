package com.archivetv.ui.live

import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.Concert

/** A concert shown as a card: it opens the best recording's item page. */
fun Concert.toCardItem(): ArchiveItem = ArchiveItem(
    identifier = best.identifier,
    title = artistName,
    year = concertSubtitle(this),
    isAudio = true,
)

private fun partLabel(showPart: String) = when (showPart) {
    "early" -> " (Early Show)"
    "late" -> " (Late Show)"
    else -> ""
}

/** "1977-05-08 · Barton Hall"; the city stands in for a missing venue, or follows it when [withCity]. */
fun concertSubtitle(c: Concert, withCity: Boolean = false): String {
    val place = if (withCity) listOfNotNull(c.venue, c.city) else listOfNotNull(c.venue ?: c.city)
    return (listOf(c.date + partLabel(c.showPart)) + place.filter { it.isNotBlank() }).joinToString(" · ")
}
