package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState

internal data class SheetBackAndDragState(
    val isDragging: Boolean,
    val isDraggingPlayerArea: Boolean,
    val predictiveBackEnabled: Boolean,
    val onDraggingChange: (Boolean) -> Unit,
    val onDraggingPlayerAreaChange: (Boolean) -> Unit
)

@Composable
internal fun rememberSheetBackAndDragState(
    showPlayerContentArea: Boolean,
    currentSheetContentState: PlayerSheetState
): SheetBackAndDragState {
    var isDragging by remember { mutableStateOf(false) }
    var isDraggingPlayerArea by remember { mutableStateOf(false) }
    val predictiveBackEnabled by remember(
        showPlayerContentArea,
        currentSheetContentState,
        isDragging
    ) {
        derivedStateOf {
            showPlayerContentArea &&
                currentSheetContentState == PlayerSheetState.EXPANDED &&
                !isDragging
        }
    }

    val onDraggingChange = remember {
        { dragging: Boolean ->
            isDragging = dragging
        }
    }
    val onDraggingPlayerAreaChange = remember {
        { dragging: Boolean ->
            isDraggingPlayerArea = dragging
        }
    }

    return SheetBackAndDragState(
        isDragging = isDragging,
        isDraggingPlayerArea = isDraggingPlayerArea,
        predictiveBackEnabled = predictiveBackEnabled,
        onDraggingChange = onDraggingChange,
        onDraggingPlayerAreaChange = onDraggingPlayerAreaChange
    )
}
