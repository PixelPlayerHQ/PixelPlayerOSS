package com.lostf1sh.pixelplayeross.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Resolves icon/content color for a playlist cover color using current Material scheme tokens.
 * Falls back to contrast-based color for non-scheme colors.
 */
fun resolvePlaylistCoverContentColor(
    colorArgb: Int,
    scheme: ColorScheme
): Color {
    return when (colorArgb) {
        scheme.primary.toArgb() -> scheme.onPrimary
        scheme.primaryContainer.toArgb() -> scheme.onPrimaryContainer
        scheme.secondary.toArgb() -> scheme.onSecondary
        scheme.secondaryContainer.toArgb() -> scheme.onSecondaryContainer
        scheme.tertiary.toArgb() -> scheme.onTertiary
        scheme.tertiaryContainer.toArgb() -> scheme.onTertiaryContainer
        scheme.error.toArgb() -> scheme.onError
        scheme.errorContainer.toArgb() -> scheme.onErrorContainer
        scheme.surfaceContainerHigh.toArgb() -> scheme.onSurface
        scheme.inverseSurface.toArgb() -> scheme.inverseOnSurface
        else -> getContrastColor(Color(colorArgb))
    }
}
