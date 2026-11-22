package com.archivetv.ui.common

import com.archivetv.domain.Catalog

/** Catalog groups offered as a Browse tile row (Home, and Search before anything is typed). */
private val browseTileGroups = listOf(
    "movie-genres" to "Movie Genres",
    "movie-decades" to "Movies by Decade",
    "tv-decades" to "TV by Decade",
    "tv-genres" to "TV Genres",
    "tv-shows" to "TV Shows",
    "edu-studios" to "Educational Studios",
)

/** The tile row's (label, action) pairs for the groups this catalog has. */
fun browseTiles(catalog: Catalog, onOpenGroup: (String) -> Unit): List<Pair<String, () -> Unit>> =
    browseTileGroups.filter { catalog.node(it.first) != null }
        .map { (id, label) -> label to { onOpenGroup(id) } }
