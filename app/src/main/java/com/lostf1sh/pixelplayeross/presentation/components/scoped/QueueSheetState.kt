package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineScope

internal data class QueueSheetState(
    val showQueueSheet: Boolean,
    val allowQueueSheetInteraction: Boolean,
    val queueSheetOffset: Animatable<Float, AnimationVector1D>,
    val queueSheetHeightPx: Float,
    val queueHiddenOffsetPx: Float,
    val queueSheetController: QueueSheetController,
    val onQueueSheetHeightPxChange: (Float) -> Unit
)

@Composable
internal fun rememberQueueSheetState(
    scope: CoroutineScope,
    screenHeightPx: Float,
    density: Density,
    currentBottomPadding: Dp,
    showPlayerContentArea: Boolean,
    currentSheetContentState: PlayerSheetState
): QueueSheetState {
    var showQueueSheet by remember { mutableStateOf(false) }
    val allowQueueSheetInteraction by remember(showPlayerContentArea, currentSheetContentState) {
        derivedStateOf {
            showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        }
    }
    val queueSheetOffset = remember(screenHeightPx) { Animatable(screenHeightPx) }
    var queueSheetHeightPx by remember { mutableFloatStateOf(0f) }
    val queueHiddenOffsetPx by remember(currentBottomPadding, queueSheetHeightPx, density) {
        derivedStateOf {
            val basePadding = with(density) { currentBottomPadding.toPx() }
            if (queueSheetHeightPx == 0f) 0f else queueSheetHeightPx + basePadding
        }
    }
    val minQueueDragThresholdPx = remember(density) { with(density) { 14.dp.toPx() } }
    val maxQueueDragThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    val queueDragThresholdPx by remember(
        queueHiddenOffsetPx,
        minQueueDragThresholdPx,
        maxQueueDragThresholdPx
    ) {
        derivedStateOf {
            (queueHiddenOffsetPx * 0.05f).coerceIn(minQueueDragThresholdPx, maxQueueDragThresholdPx)
        }
    }
    val queueMinFlingTravelPx by remember(density) {
        derivedStateOf { with(density) { 12.dp.toPx() } }
    }

    val queueHiddenOffsetPxState = rememberUpdatedState(queueHiddenOffsetPx)
    val allowQueueSheetInteractionState = rememberUpdatedState(allowQueueSheetInteraction)
    val queueMinFlingTravelPxState = rememberUpdatedState(queueMinFlingTravelPx)
    val queueDragThresholdPxState = rememberUpdatedState(queueDragThresholdPx)
    val showQueueSheetState = rememberUpdatedState(showQueueSheet)
    val queueSheetController = remember(
        scope,
        queueSheetOffset
    ) {
        QueueSheetController(
            scope = scope,
            queueSheetOffset = queueSheetOffset,
            hiddenOffsetProvider = { queueHiddenOffsetPxState.value },
            allowInteractionProvider = { allowQueueSheetInteractionState.value },
            minFlingTravelPxProvider = { queueMinFlingTravelPxState.value },
            dragThresholdPxProvider = { queueDragThresholdPxState.value },
            showQueueSheetProvider = { showQueueSheetState.value },
            onShowQueueSheetChange = { showQueueSheet = it }
        )
    }

    return QueueSheetState(
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        queueSheetOffset = queueSheetOffset,
        queueSheetHeightPx = queueSheetHeightPx,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        queueSheetController = queueSheetController,
        onQueueSheetHeightPxChange = { queueSheetHeightPx = it }
    )
}
