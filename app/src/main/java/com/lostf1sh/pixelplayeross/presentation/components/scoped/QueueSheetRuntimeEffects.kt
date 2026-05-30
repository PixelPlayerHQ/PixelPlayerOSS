package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Hosts lifecycle effects for queue sheet synchronization and haptic feedback.
 * Behavior mirrors the previous inline effects in UnifiedPlayerSheet.
 */
@Composable
internal fun QueueSheetRuntimeEffects(
    queueSheetController: QueueSheetController,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueHiddenOffsetPx: Float,
    showQueueSheet: Boolean,
    allowQueueSheetInteraction: Boolean,
    onTopEdgeReached: () -> Unit
) {
    val onTopEdgeReachedState = rememberUpdatedState(onTopEdgeReached)

    LaunchedEffect(queueHiddenOffsetPx) {
        queueSheetController.syncOffsetToVisibility()
    }

    LaunchedEffect(showQueueSheet, queueHiddenOffsetPx) {
        queueSheetController.syncCollapsedWhenHidden()
    }

    LaunchedEffect(queueHiddenOffsetPx, showQueueSheet) {
        if (queueHiddenOffsetPx == 0f) return@LaunchedEffect
        var hasHitTopEdge = showQueueSheet && queueSheetOffset.value <= 0.5f
        snapshotFlow { queueSheetOffset.value to showQueueSheet }
            .collectLatest { (offset, isShown) ->
                val isFullyOpen = isShown && offset <= 0.5f
                if (isFullyOpen && !hasHitTopEdge) {
                    onTopEdgeReachedState.value()
                    hasHitTopEdge = true
                } else if (!isFullyOpen) {
                    hasHitTopEdge = false
                }
            }
    }

    LaunchedEffect(allowQueueSheetInteraction, queueHiddenOffsetPx) {
        queueSheetController.forceCollapseIfInteractionDisabled()
    }
}
