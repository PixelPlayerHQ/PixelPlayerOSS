package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    tintColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tintAlpha: Float = 0.45f
): Modifier {
    val highlightRim = Color.White.copy(alpha = 0.40f)
    val shadowRim = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)

    return this
        .clip(shape)
        .background(
            color = tintColor.copy(alpha = tintAlpha),
            shape = shape
        )
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color.Transparent,
                    MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.08f)
                )
            ),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    highlightRim,
                    shadowRim,
                    highlightRim.copy(alpha = 0.10f)
                )
            ),
            shape = shape
        )
}
