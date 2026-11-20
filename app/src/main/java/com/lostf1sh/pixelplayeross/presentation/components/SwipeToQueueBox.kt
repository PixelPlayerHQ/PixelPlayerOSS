package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.SwipeToQueueAction
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val SwipeToQueueThreshold = 72.dp

/** The swipe-to-queue action for song rows, or null while the setting turns the swipe off. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberSwipeToQueue(playerViewModel: PlayerViewModel): ((Song) -> Unit)? {
    val action by playerViewModel.swipeToQueueAction.collectAsStateWithLifecycle()
    return remember(action, playerViewModel) {
        if (action == SwipeToQueueAction.OFF) null else playerViewModel::queueSongFromSwipe
    }
}

/**
 * Lets a song row slide towards the end edge; letting go past the threshold calls [onSwipe].
 * With a null [onSwipe], [content] gets [modifier] as is and no gesture is added.
 */
@Composable
fun SwipeToQueueBox(
    onSwipe: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (onSwipe == null) {
        content(modifier)
        return
    }

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SwipeToQueueThreshold.toPx() }
    // Offsets are kept towards the end edge, which is left in right-to-left layouts.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Box(modifier) {
        Icon(
            painter = painterResource(R.drawable.rounded_queue_music_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .graphicsLayer {
                    val progress = (offset.value / thresholdPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                }
        )
        content(
            Modifier
                .graphicsLayer { translationX = offset.value * direction }
                .pointerInput(direction, thresholdPx) {
                    var dragged = 0f
                    fun settle() {
                        dragged = 0f
                        scope.launch { offset.animateTo(0f) }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            if (dragged >= thresholdPx) currentOnSwipe()
                            settle()
                        },
                        onDragCancel = { settle() }
                    ) { change, dragAmount ->
                        change.consume()
                        val wasArmed = dragged >= thresholdPx
                        dragged = (dragged + dragAmount * direction).coerceIn(0f, thresholdPx * 1.5f)
                        if (!wasArmed && dragged >= thresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        val target = dragged
                        scope.launch { offset.snapTo(target) }
                    }
                }
        )
    }
}
