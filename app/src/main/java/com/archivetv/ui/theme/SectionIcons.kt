package com.archivetv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Drawer icons for the catalog sections. material-icons-core lacks these and
 * material-icons-extended is large without minification, so the four Material
 * "filled" shapes are defined here (24 x 24 viewport).
 */
object SectionIcons {
    val Movie = icon(
        "Movie",
        "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18" +
            "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z",
    )
    val Tv = icon(
        "Tv",
        "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2" +
            "L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z",
    )
    val Animation = icon(
        "Animation",
        "M15,2c-2.71,0 -5.05,1.54 -6.22,3.78c-1.28,0.67 -2.34,1.72 -3,3C3.54,9.95 2,12.29 2,15" +
            "c0,3.87 3.13,7 7,7c2.71,0 5.05,-1.54 6.22,-3.78c1.28,-0.67 2.34,-1.72 3,-3" +
            "C20.46,14.05 22,11.71 22,9C22,5.13 18.87,2 15,2zM9,20c-2.76,0 -5,-2.24 -5,-5" +
            "c0,-1.12 0.37,-2.16 1,-3c0,3.87 3.13,7 7,7C11.16,19.63 10.12,20 9,20zM12,17" +
            "c-2.76,0 -5,-2.24 -5,-5c0,-1.12 0.37,-2.16 1,-3c0,3.86 3.13,6.99 7,7" +
            "C14.16,16.63 13.12,17 12,17zM16.7,13.7C16.17,13.89 15.6,14 15,14c-2.76,0 -5,-2.24 -5,-5" +
            "c0,-0.6 0.11,-1.17 0.3,-1.7C10.83,7.11 11.4,7 12,7c2.76,0 5,2.24 5,5" +
            "C17,12.6 16.89,13.17 16.7,13.7zM19,12c0,-3.86 -3.13,-6.99 -7,-7c0.84,-0.63 1.87,-1 3,-1" +
            "c2.76,0 5,2.24 5,5C20,10.12 19.63,11.16 19,12z",
    )
    val School = icon(
        "School",
        "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6l9,-4.91V17h2V9L12,3z",
    )

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
