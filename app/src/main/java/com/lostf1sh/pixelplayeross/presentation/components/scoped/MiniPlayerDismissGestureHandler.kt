package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class MiniDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

internal enum class MiniPlayerGestureOutcome {
    None,
    Previous,
    Next,
    DismissLeft,
    DismissRight
}

private const val MINI_PLAYER_SKIP_DISTANCE_DP = 56f
private const val MINI_PLAYER_SKIP_MAX_DISTANCE_DP = 120f
private const val MINI_PLAYER_FLING_MIN_DISTANCE_DP = 24f
private const val MINI_PLAYER_FLING_VELOCITY_DP_PER_SECOND = 900f
private const val MINI_PLAYER_DISMISS_SCREEN_FRACTION = 0.4f

/**
 * Classifies a completed mini-player gesture without depending on pointer input state.
 *
 * Previous/next use physical directions on purpose: right is previous and left is next in
 * both LTR and RTL, matching transport gestures in other music players. A deliberate drag
 * past 40% of the screen keeps the existing queue-dismiss interaction.
 */
internal fun resolveMiniPlayerGestureOutcome(
    displacementX: Float,
    velocityX: Float,
    screenWidthPx: Float,
    density: Float,
    layoutDirection: LayoutDirection
): MiniPlayerGestureOutcome {
    val absoluteDistance = abs(displacementX)
    if (
        screenWidthPx > 0f &&
        absoluteDistance > screenWidthPx * MINI_PLAYER_DISMISS_SCREEN_FRACTION
    ) {
        return if (displacementX < 0f) {
            MiniPlayerGestureOutcome.DismissLeft
        } else {
            MiniPlayerGestureOutcome.DismissRight
        }
    }

    val safeDensity = density.coerceAtLeast(0.1f)
    if (absoluteDistance > MINI_PLAYER_SKIP_MAX_DISTANCE_DP * safeDensity) {
        return MiniPlayerGestureOutcome.None
    }
    val crossedDistanceThreshold = absoluteDistance >= MINI_PLAYER_SKIP_DISTANCE_DP * safeDensity
    val crossedFlingThreshold =
        absoluteDistance >= MINI_PLAYER_FLING_MIN_DISTANCE_DP * safeDensity &&
            abs(velocityX) >= MINI_PLAYER_FLING_VELOCITY_DP_PER_SECOND * safeDensity
    if (!crossedDistanceThreshold && !crossedFlingThreshold) {
        return MiniPlayerGestureOutcome.None
    }

    // Intentionally enumerate both directions so RTL behavior is explicit and testable.
    return when (layoutDirection) {
        LayoutDirection.Ltr,
        LayoutDirection.Rtl -> if (displacementX > 0f) {
            MiniPlayerGestureOutcome.Previous
        } else {
            MiniPlayerGestureOutcome.Next
        }
    }
}

/**
 * Keeps mini-player transport and dismiss gestures isolated from the sheet host.
 */
internal class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val layoutDirection: LayoutDirection,
    private val onDismissPlaylistAndShowUndo: () -> Unit,
    private val onDismissStarted: () -> Unit = {},
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit
) {
    private var dragPhase: MiniDismissDragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f
    private var offsetJob: Job? = null
    private var hasPerformedGestureHaptic = false

    fun onDragStart() {
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        hasPerformedGestureHaptic = false
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                hasPerformedGestureHaptic = true
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            MiniDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd(velocityX: Float) {
        val completedDragX = accumulatedDragX
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = completedDragX,
            velocityX = velocityX,
            screenWidthPx = screenWidthPx,
            density = density.density,
            layoutDirection = layoutDirection
        )
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()
        accumulatedDragX = 0f

        when (outcome) {
            MiniPlayerGestureOutcome.DismissLeft,
            MiniPlayerGestureOutcome.DismissRight -> {
                performGestureHapticOnce()
                onDismissStarted()
                val targetDismissOffset = when (outcome) {
                    MiniPlayerGestureOutcome.DismissLeft -> -screenWidthPx
                    else -> screenWidthPx
                }
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = targetDismissOffset,
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing
                        )
                    )
                    onDismissPlaylistAndShowUndo()
                    offsetAnimatable.snapTo(0f)
                }
            }

            MiniPlayerGestureOutcome.Previous -> {
                performGestureHapticOnce()
                onPrevious()
                animateBackToRest()
            }

            MiniPlayerGestureOutcome.Next -> {
                performGestureHapticOnce()
                onNext()
                animateBackToRest()
            }

            MiniPlayerGestureOutcome.None -> animateBackToRest()
        }
    }

    fun onDragCancel() {
        dragPhase = MiniDismissDragPhase.IDLE
        accumulatedDragX = 0f
        offsetJob?.cancel()
        animateBackToRest()
    }

    private fun performGestureHapticOnce() {
        if (hasPerformedGestureHaptic) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        hasPerformedGestureHaptic = true
    }

    private fun animateBackToRest() {
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

@Composable
internal fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissPlaylistAndShowUndo: () -> Unit,
    onDismissStarted: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
): MiniPlayerDismissGestureHandler {
    val layoutDirection = LocalLayoutDirection.current
    val onDismissPlaylistAndShowUndoState = rememberUpdatedState(onDismissPlaylistAndShowUndo)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    val onPreviousState = rememberUpdatedState(onPrevious)
    val onNextState = rememberUpdatedState(onNext)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx, layoutDirection) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            layoutDirection = layoutDirection,
            onDismissPlaylistAndShowUndo = { onDismissPlaylistAndShowUndoState.value() },
            onDismissStarted = { onDismissStartedState.value() },
            onPrevious = { onPreviousState.value() },
            onNext = { onNextState.value() }
        )
    }
}

internal fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        val velocityTracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                handler.onDragStart()
            },
            onHorizontalDrag = { change, dragAmount ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd(velocityTracker.calculateVelocity().x) },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
