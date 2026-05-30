package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Spring
import androidx.compose.ui.util.lerp
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState
import kotlin.math.abs

internal data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

internal fun computeSheetVerticalDragFrame(
    currentTranslationY: Float,
    dragAmount: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    val newY = (currentTranslationY + dragAmount)
        .coerceIn(
            expandedY - miniHeightPx * 0.2f,
            collapsedY + miniHeightPx * 0.2f
        )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return SheetVerticalDragFrame(
        translationY = newY,
        expansionFraction = newFraction
    )
}

internal fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED &&
            accumulatedDragY <= 0f -> PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

internal fun collapseSpringDampingForFraction(currentFraction: Float): Float {
    return lerp(
        start = Spring.DampingRatioNoBouncy,
        stop = Spring.DampingRatioLowBouncy,
        fraction = currentFraction
    )
}

internal fun collapseInitialSquashForFraction(currentFraction: Float): Float {
    return lerp(1.0f, 0.97f, currentFraction)
}
