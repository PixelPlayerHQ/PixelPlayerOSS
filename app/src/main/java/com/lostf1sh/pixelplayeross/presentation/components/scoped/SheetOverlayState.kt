package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val QUEUE_SCRIM_MAX_ALPHA = 0.45f
private const val QUEUE_OCCLUDES_PLAYER_FRACTION = 0.08f

/**
 * [queueVisualOpenFractionProvider] and [queueScrimAlphaProvider] change on every frame of a queue
 * drag or back gesture, so they must only be read in the layout or draw phase. Composition reads
 * the threshold flags instead, which flip only when the queue starts or stops covering the player.
 */
internal data class SheetOverlayState(
    val internalIsKeyboardVisible: Boolean,
    val actuallyShowSheetContent: Boolean,
    val isQueueVisible: Boolean,
    val queueVisualOpenFractionProvider: () -> Float,
    val queueScrimAlphaProvider: () -> Float,
    val showQueueScrim: Boolean,
    val isQueueOccludingPlayer: Boolean
)

/**
 * How far the queue sheet visually covers the player, from 0 (hidden) to 1 (fully open).
 *
 * Follows the live sheet offset, so the scrim and the player behind it track the finger, and
 * fades out as the predictive back gesture progresses. A committed back holds its progress while
 * the sheet slides out (see [settleQueueBackProgress]), so the fraction only falls as it closes.
 */
internal fun computeQueueVisualOpenFraction(
    isQueueShown: Boolean,
    queueSheetOffsetPx: Float,
    queueHiddenOffsetPx: Float,
    predictiveBackProgress: Float
): Float {
    if (!isQueueShown || queueHiddenOffsetPx <= 0f) return 0f
    val dragFraction = (1f - queueSheetOffsetPx / queueHiddenOffsetPx).coerceIn(0f, 1f)
    return dragFraction * (1f - predictiveBackProgress.coerceIn(0f, 1f))
}

/**
 * Whether the queue and song info host stays composed. A keyboard opened elsewhere drops it, but
 * one opened from inside the open queue (the custom speed field) must not: unmounting the host
 * would dispose the dialog that owns the keyboard and drop the user back to the queue.
 */
internal fun computeShouldRenderQueueHost(
    isKeyboardVisible: Boolean,
    isQueueShown: Boolean,
    isSongInfoShown: Boolean
): Boolean = !isKeyboardVisible || isQueueShown || isSongInfoShown

@Composable
internal fun rememberSheetOverlayState(
    density: Density,
    showPlayerContentArea: Boolean,
    hideMiniPlayer: Boolean,
    showQueueSheet: Boolean,
    queueHiddenOffsetPx: Float,
    screenHeightPx: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queuePredictiveBackProgress: Animatable<Float, AnimationVector1D>
): SheetOverlayState {
    var internalIsKeyboardVisible by remember { mutableStateOf(false) }

    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .distinctUntilChanged()
            .collectLatest { isVisible ->
                if (internalIsKeyboardVisible != isVisible) {
                    internalIsKeyboardVisible = isVisible
                }
            }
    }

    val shouldShowSheet by remember(showPlayerContentArea, hideMiniPlayer) {
        derivedStateOf { showPlayerContentArea && !hideMiniPlayer }
    }

    val actuallyShowSheetContent by remember(shouldShowSheet) {
        derivedStateOf { shouldShowSheet }
    }

    val isQueueVisible by remember(showQueueSheet, queueHiddenOffsetPx, screenHeightPx) {
        derivedStateOf {
            showQueueSheet &&
                queueHiddenOffsetPx > 0f &&
                screenHeightPx > 0f
        }
    }

    // Read through updated states so the derived fraction and the providers below keep one
    // identity across queue open/close, instead of handing new lambdas to the layers each time.
    val latestShowQueueSheet = rememberUpdatedState(showQueueSheet)
    val latestQueueHiddenOffsetPx = rememberUpdatedState(queueHiddenOffsetPx)
    val queueVisualOpenFraction = remember(queueSheetOffset, queuePredictiveBackProgress) {
        derivedStateOf {
            computeQueueVisualOpenFraction(
                isQueueShown = latestShowQueueSheet.value,
                queueSheetOffsetPx = queueSheetOffset.value,
                queueHiddenOffsetPx = latestQueueHiddenOffsetPx.value,
                predictiveBackProgress = queuePredictiveBackProgress.value
            )
        }
    }
    val queueVisualOpenFractionProvider = remember(queueVisualOpenFraction) {
        { queueVisualOpenFraction.value }
    }
    val queueScrimAlphaProvider = remember(queueVisualOpenFraction) {
        { queueVisualOpenFraction.value * QUEUE_SCRIM_MAX_ALPHA }
    }
    val showQueueScrim by remember(queueVisualOpenFraction) {
        derivedStateOf { queueVisualOpenFraction.value > 0f }
    }
    val isQueueOccludingPlayer by remember(queueVisualOpenFraction) {
        derivedStateOf { queueVisualOpenFraction.value >= QUEUE_OCCLUDES_PLAYER_FRACTION }
    }

    return SheetOverlayState(
        internalIsKeyboardVisible = internalIsKeyboardVisible,
        actuallyShowSheetContent = actuallyShowSheetContent,
        isQueueVisible = isQueueVisible,
        queueVisualOpenFractionProvider = queueVisualOpenFractionProvider,
        queueScrimAlphaProvider = queueScrimAlphaProvider,
        showQueueScrim = showQueueScrim,
        isQueueOccludingPlayer = isQueueOccludingPlayer
    )
}
