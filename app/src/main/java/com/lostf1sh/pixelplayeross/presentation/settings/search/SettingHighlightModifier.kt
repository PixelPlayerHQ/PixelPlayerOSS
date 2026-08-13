package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

fun Modifier.settingHighlight(
    itemKey: String,
    highlightKey: String?,
    onHighlightFinished: (() -> Unit)? = null
): Modifier = composed {
    val isTarget = remember(itemKey, highlightKey) {
        highlightKey != null && highlightKey == itemKey
    }

    val highlightAlpha = remember { Animatable(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isTarget) {
        if (isTarget) {
            // Wait for autoscroll animation to settle onto the targeted item
            kotlinx.coroutines.delay(350)

            // Pulse 1: Initial bright glow burst
            highlightAlpha.animateTo(0.60f, animationSpec = tween(250, easing = FastOutSlowInEasing))
            highlightAlpha.animateTo(0.20f, animationSpec = tween(250, easing = FastOutSlowInEasing))

            // Pulse 2: Sustained glow holding focus then fading out gracefully
            highlightAlpha.animateTo(0.50f, animationSpec = tween(250, easing = FastOutSlowInEasing))
            highlightAlpha.animateTo(0f, animationSpec = tween(2000, easing = FastOutSlowInEasing))

            onHighlightFinished?.invoke()
        }
    }

    if (highlightAlpha.value > 0f) {
        val color = primaryColor.copy(alpha = highlightAlpha.value)
        drawWithContent {
            drawContent()
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            )
        }
    } else {
        this
    }
}
