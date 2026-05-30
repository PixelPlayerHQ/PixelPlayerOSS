package com.lostf1sh.pixelplayeross.presentation.components.scoped

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.core.view.HapticFeedbackConstantsCompat
import com.lostf1sh.pixelplayeross.presentation.utils.AppHapticsConfig
import com.lostf1sh.pixelplayeross.presentation.utils.performAppCompatHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class QueueDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/**
 * Manages swipe-to-dismiss gesture for queue items with a multi-phase approach
 * that prevents accidental dismisses. Mirrors the MiniPlayer dismiss handler logic:
 *
 * 1. TENSION – drag up to [tensionThresholdDp] (60dp) with dampened visual feedback (max 20dp offset).
 *    The item resists movement, making accidental swipes while scrolling very unlikely.
 * 2. SNAPPING – once the tension threshold is exceeded, haptic fires and the item snaps
 *    to the real drag position with a spring animation.
 * 3. FREE_DRAG – the item tracks the finger 1:1 with a stiff spring.
 * 4. On release, if the accumulated drag exceeds 40% of the item width the item is dismissed;
 *    otherwise it bounces back.
 *
 * Only end-to-start (negative / left) swipes are honoured.
 */
internal class QueueItemDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticView: View,
    private val appHapticsConfig: AppHapticsConfig,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val itemWidthPx: Float,
    private val onDismiss: () -> Unit
) {
    private var dragPhase: QueueDismissDragPhase = QueueDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f

    /** Whether the drag has crossed into the commit zone (past dismiss threshold). */
    var isInDismissZone: Boolean by mutableStateOf(false)
        private set

    /** Whether a dismiss animation is currently running. */
    var isDismissing: Boolean by mutableStateOf(false)
        private set

    fun onDragStart() {
        if (isDismissing) return
        dragPhase = QueueDismissDragPhase.TENSION
        accumulatedDragX = 0f
        isInDismissZone = false
        scope.launch { offsetAnimatable.stop() }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        if (isDismissing) return
        accumulatedDragX += dragAmount
        // Only allow end-to-start (negative / left) swipes
        if (accumulatedDragX > 0f) {
            accumulatedDragX = 0f
            scope.launch { offsetAnimatable.snapTo(0f) }
            return
        }

        when (dragPhase) {
            QueueDismissDragPhase.TENSION -> {
                val tensionThresholdPx = 60f * density.density
                if (abs(accumulatedDragX) < tensionThresholdPx) {
                    // Dampened feedback: max 20dp visual offset while in tension zone
                    val maxTensionOffsetPx = 20f * density.density
                    val dragFraction = (abs(accumulatedDragX) / tensionThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = maxTensionOffsetPx * dragFraction
                    scope.launch {
                        offsetAnimatable.snapTo(-tensionOffset)
                    }
                } else {
                    dragPhase = QueueDismissDragPhase.SNAPPING
                }
            }

            QueueDismissDragPhase.SNAPPING -> {
                performAppCompatHapticFeedback(
                    hapticView,
                    appHapticsConfig,
                    HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                )
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                dragPhase = QueueDismissDragPhase.FREE_DRAG
            }

            QueueDismissDragPhase.FREE_DRAG -> {
                val dismissThreshold = itemWidthPx * 0.40f
                val nowInZone = abs(accumulatedDragX) > dismissThreshold
                if (nowInZone != isInDismissZone) {
                    isInDismissZone = nowInZone
                    performAppCompatHapticFeedback(
                        hapticView,
                        appHapticsConfig,
                        if (nowInZone) HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                        else HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                    )
                }
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            QueueDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        if (isDismissing) return
        dragPhase = QueueDismissDragPhase.IDLE
        val dismissThreshold = itemWidthPx * 0.40f

        if (abs(accumulatedDragX) > dismissThreshold) {
            // Dismiss: animate off-screen to the left
            isDismissing = true
            performAppCompatHapticFeedback(
                hapticView,
                appHapticsConfig,
                HapticFeedbackConstantsCompat.GESTURE_END
            )
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = -itemWidthPx,
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismiss()
                // Reset after dismiss callback
                offsetAnimatable.snapTo(0f)
                isDismissing = false
                isInDismissZone = false
            }
        } else {
            // Spring back
            isInDismissZone = false
            scope.launch {
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

    fun onDragCancel() {
        if (isDismissing) return
        dragPhase = QueueDismissDragPhase.IDLE
        isInDismissZone = false
        scope.launch {
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
internal fun rememberQueueItemDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticView: View,
    appHapticsConfig: AppHapticsConfig,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    itemWidthPx: Float,
    onDismiss: () -> Unit
): QueueItemDismissGestureHandler {
    return remember(scope, density, hapticView, appHapticsConfig, offsetAnimatable, itemWidthPx, onDismiss) {
        QueueItemDismissGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            appHapticsConfig = appHapticsConfig,
            offsetAnimatable = offsetAnimatable,
            itemWidthPx = itemWidthPx,
            onDismiss = onDismiss
        )
    }
}
