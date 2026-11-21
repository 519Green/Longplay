package com.archivetv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

// TV apps are viewed in dim rooms, so ArchiveTV is always dark.
private val Background = Color(0xFF0E0E11)
private val Surface = Color(0xFF16161B)
private val SurfaceVariant = Color(0xFF24242B)
private val OnSurface = Color(0xFFECE9E4)
private val OnSurfaceVariant = Color(0xFFB4B0AA)
private val Primary = Color(0xFFE3B35C)
private val OnPrimary = Color(0xFF2A1C00)
private val Border = Color(0xFF4A4852)

private val TvColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    border = Border,
)

// Mobile Material3 components (the search OutlinedTextField) read their own theme.
private val M3Colors = m3DarkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Border,
)

@Composable
fun ArchiveTvTheme(content: @Composable () -> Unit) {
    M3Theme(colorScheme = M3Colors) {
        MaterialTheme(colorScheme = TvColors, content = content)
    }
}
