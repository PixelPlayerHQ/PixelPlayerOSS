package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Marks a settings row as targetable by settings search.
 *
 * When [highlightKey] matches [itemKey] the row scrolls itself into view and pulses twice in the
 * primary colour, so a user arriving from a search result can see which of a dozen near-identical
 * rows the result meant. Every category page renders its rows inside a single lazy item, so
 * scrolling is done with a [BringIntoViewRequester] rather than by list index — that keeps this
 * working when rows are added, reordered, or conditionally hidden.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.settingHighlight(
    itemKey: String,
    highlightKey: String?,
    onHighlightFinished: (() -> Unit)? = null
): Modifier = composed {
    val isTarget = remember(itemKey, highlightKey) { highlightKey == itemKey }

    val highlightAlpha = remember { Animatable(0f) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isTarget) {
        if (!isTarget) return@LaunchedEffect

        // Let the screen transition settle before moving the list, otherwise the scroll is
        // computed against a layout that is still animating in.
        delay(350)
        bringIntoViewRequester.bringIntoView()

        highlightAlpha.animateTo(0.60f, tween(250, easing = FastOutSlowInEasing))
        highlightAlpha.animateTo(0.20f, tween(250, easing = FastOutSlowInEasing))
        highlightAlpha.animateTo(0.50f, tween(250, easing = FastOutSlowInEasing))
        highlightAlpha.animateTo(0f, tween(2000, easing = FastOutSlowInEasing))

        onHighlightFinished?.invoke()
    }

    val base = Modifier.bringIntoViewRequester(bringIntoViewRequester)

    if (highlightAlpha.value > 0f) {
        val color = primaryColor.copy(alpha = highlightAlpha.value)
        base.drawWithContent {
            drawContent()
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            )
        }
    } else {
        base
    }
}
