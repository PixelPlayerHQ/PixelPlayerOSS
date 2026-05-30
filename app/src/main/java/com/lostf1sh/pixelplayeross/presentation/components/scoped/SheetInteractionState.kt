package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineScope
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

internal data class SheetInteractionState(
    val playerShadowShape: Shape,
    val sheetVerticalDragGestureHandler: SheetVerticalDragGestureHandler,
    val canDragSheet: Boolean
)

@Composable
internal fun rememberSheetInteractionState(
    scope: CoroutineScope,
    velocityTracker: VelocityTracker,
    sheetMotionController: SheetMotionController,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    sheetExpandedTargetY: Float,
    miniPlayerContentHeightPx: Float,
    currentSheetContentState: PlayerSheetState,
    showPlayerContentArea: Boolean,
    overallSheetTopCornerRadiusProvider: () -> Dp,
    playerContentActualBottomRadiusProvider: () -> Dp,
    useSmoothCorners: Boolean,
    isDragging: Boolean,
    onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    onExpandSheetState: () -> Unit,
    onCollapseSheetState: () -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDraggingPlayerAreaChange: (Boolean) -> Unit
): SheetInteractionState {
    val useSmoothCornersState = rememberUpdatedState(useSmoothCorners)
    val isDraggingState = rememberUpdatedState(isDragging)
    val playerShadowShape = remember(
        overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider,
        playerContentExpansionFraction
    ) {
        PlayerSheetDynamicShape(
            topRadiusProvider = overallSheetTopCornerRadiusProvider,
            bottomRadiusProvider = playerContentActualBottomRadiusProvider,
            useSmoothShapeProvider = {
                useSmoothCornersState.value &&
                    !isDraggingState.value &&
                    !playerContentExpansionFraction.isRunning
            }
        )
    }

    val collapsedYState = rememberUpdatedState(sheetCollapsedTargetY)
    val expandedYState = rememberUpdatedState(sheetExpandedTargetY)
    val miniHeightState = rememberUpdatedState(miniPlayerContentHeightPx)
    val densityState = rememberUpdatedState(LocalDensity.current)
    val currentSheetState = rememberUpdatedState(currentSheetContentState)
    val onAnimateSheetState = rememberUpdatedState(onAnimateSheet)
    val onExpandSheetStateState = rememberUpdatedState(onExpandSheetState)
    val onCollapseSheetStateState = rememberUpdatedState(onCollapseSheetState)
    val onDraggingChangeState = rememberUpdatedState(onDraggingChange)
    val onDraggingPlayerAreaChangeState = rememberUpdatedState(onDraggingPlayerAreaChange)

    val sheetVerticalDragGestureHandler = remember(
        scope,
        velocityTracker,
        sheetMotionController,
        playerContentExpansionFraction,
        currentSheetTranslationY,
        visualOvershootScaleY
    ) {
        SheetVerticalDragGestureHandler(
            scope = scope,
            velocityTracker = velocityTracker,
            densityProvider = { densityState.value },
            sheetMotionController = sheetMotionController,
            playerContentExpansionFraction = playerContentExpansionFraction,
            currentSheetTranslationY = currentSheetTranslationY,
            expandedYProvider = { expandedYState.value },
            collapsedYProvider = { collapsedYState.value },
            miniHeightPxProvider = { miniHeightState.value },
            currentSheetStateProvider = { currentSheetState.value },
            visualOvershootScaleY = visualOvershootScaleY,
            onDraggingChange = { onDraggingChangeState.value(it) },
            onDraggingPlayerAreaChange = { onDraggingPlayerAreaChangeState.value(it) },
            onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
                onAnimateSheetState.value(targetExpanded, animationSpec, initialVelocity)
            },
            onExpandSheetState = { onExpandSheetStateState.value() },
            onCollapseSheetState = { onCollapseSheetStateState.value() }
        )
    }

    return SheetInteractionState(
        playerShadowShape = playerShadowShape,
        sheetVerticalDragGestureHandler = sheetVerticalDragGestureHandler,
        canDragSheet = showPlayerContentArea
    )
}

private class PlayerSheetDynamicShape(
    private val topRadiusProvider: () -> Dp,
    private val bottomRadiusProvider: () -> Dp,
    private val useSmoothShapeProvider: () -> Boolean
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val topRadius = topRadiusProvider().nonNegative()
        val bottomRadius = bottomRadiusProvider().nonNegative()
        if (topRadius <= 1.dp || bottomRadius <= 1.dp || !useSmoothShapeProvider()) {
            val topRadiusPx = with(density) { topRadius.toPx() }
            val bottomRadiusPx = with(density) { bottomRadius.toPx() }
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    topLeft = CornerRadius(topRadiusPx, topRadiusPx),
                    topRight = CornerRadius(topRadiusPx, topRadiusPx),
                    bottomRight = CornerRadius(bottomRadiusPx, bottomRadiusPx),
                    bottomLeft = CornerRadius(bottomRadiusPx, bottomRadiusPx)
                )
            )
        }

        val shape =
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusTR = topRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = bottomRadius,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = bottomRadius,
                smoothnessAsPercentTR = 60
            )
        return shape.createOutline(size, layoutDirection, density)
    }
}

private fun Dp.nonNegative(): Dp = takeIf { it.value.isFinite() && it.value > 0f } ?: 0.dp
