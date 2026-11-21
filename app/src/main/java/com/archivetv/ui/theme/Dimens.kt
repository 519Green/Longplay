package com.archivetv.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    /** TV overscan safe margins (Android TV layout guide: 5%, 48dp sides / 27dp top-bottom). */
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    /** Width the collapsed navigation drawer occupies at the left edge. */
    val DrawerCollapsed = 80.dp

    /**
     * Start padding inside top-level screens. AppNavHost already offsets their
     * content by [DrawerCollapsed]; this is the gap after the drawer rail.
     */
    val ContentStart = 16.dp

    /** 4-card layout width; IA thumbnails are about 1.45:1 (180 x 124 px). */
    val CardWidth = 196.dp
    const val CardImageAspect = 1.45f
    val CardSpacing = 20.dp

    /** Vertical room inside rows so a focus-scaled card is not clipped. */
    val RowFocusRoom = 12.dp
}
