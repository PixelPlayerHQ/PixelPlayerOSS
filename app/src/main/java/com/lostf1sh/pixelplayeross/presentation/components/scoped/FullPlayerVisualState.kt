package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

/**
 * Holds references needed to compute full-player visual properties lazily.
 *
 * **Key design**: [contentAlpha] and [translationY] are computed on-demand via getters
 * that read [Animatable.value]. When called inside `graphicsLayer { }`, these reads
 * happen during the draw phase and trigger **re-draw only** — not recomposition.
 * This eliminates per-frame recomposition of the parent composable during gestures.
 */
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /** Full-player fade-in: invisible until 25 % expanded, fully opaque at 100 %. */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return (f - 0.25f).coerceIn(0f, 0.75f) / 0.75f
        }

    /** Slide-up entrance driven by [contentAlpha]. */
    val translationY: Float
        get() = lerp(initialOffsetY, 0f, contentAlpha)
}

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
