package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight iOS-inspired Liquid Glass treatment for Compose surfaces.
 * Uses layered translucency, a moving specular highlight and a soft rim,
 * without requiring platform-specific blur APIs.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    tintColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tintAlpha: Float = 0.45f,
    animated: Boolean = true
): Modifier {
    val transition = rememberInfiniteTransition(label = "liquidGlass")
    val sheen by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200),
            repeatMode = RepeatMode.Restart
        ),
        label = "glassSheen"
    )
    val sheenStart = if (animated) sheen else 0.15f
    val surface = MaterialTheme.colorScheme.surface
    val surfaceTint = MaterialTheme.colorScheme.surfaceTint
    val outline = MaterialTheme.colorScheme.outlineVariant

    return this
        .clip(shape)
        .background(
            color = tintColor.copy(alpha = tintAlpha.coerceIn(0f, 1f)),
            shape = shape
        )
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    surface.copy(alpha = 0.10f),
                    surfaceTint.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.06f)
                ),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(900f, 900f)
            ),
            shape = shape
        )
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                start = androidx.compose.ui.geometry.Offset(sheenStart * 900f, 0f),
                end = androidx.compose.ui.geometry.Offset((sheenStart + 0.35f) * 900f, 900f)
            ),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    outline.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.12f)
                )
            ),
            shape = shape
        )
}
