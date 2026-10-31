package com.lostf1sh.pixelplayeross.presentation.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.LocalMaterialTheme
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.secondaryContainer,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    var playPauseTapCount by remember { mutableIntStateOf(0) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val tapsBeforeDelay = playPauseTapCount
        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        // A play/pause tap during the delay is newer than this "not playing" report.
        if (playPauseTapCount == tapsBeforeDelay && !latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    // A tap flips the icon right away. If the player never follows (playback could not
    // start), fall back to its real state.
    LaunchedEffect(playPauseTapCount) {
        if (playPauseTapCount == 0) return@LaunchedEffect
        delay(PLAY_PAUSE_CONFIRM_TIMEOUT_MS)
        playPauseVisualState = latestIsPlayingProvider()
    }

    fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
        button -> expansionWeight
        null -> baseWeight
        else -> compressionWeight
    }

    val prevWeight = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PREVIOUS),
        animationSpec = pressAnimationSpec,
        label = "prevWeight"
    )
    val playWeight = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
        animationSpec = pressAnimationSpec,
        label = "playWeight"
    )
    val nextWeight = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.NEXT),
        animationSpec = pressAnimationSpec,
        label = "nextWeight"
    )

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        content = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_track),
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "playCorner"
            )
            Box(
                modifier = Modifier
                    // Read the animated corner in the draw phase so the play/pause morph
                    // doesn't recompose the controls on every frame.
                    .graphicsLayer {
                        clip = true
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = playCorner,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = playCorner,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = playCorner,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = playCorner,
                            smoothnessAsPercentBR = 60
                        )
                    }
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        playPauseVisualState = !playPauseVisualState
                        // A state held back by a skip predates this tap.
                        pendingPlayPauseState = null
                        playPauseTapCount++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = playPauseVisualState,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next_track),
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    ) { measurables, constraints ->
        // Weights are read during layout so the press stretch relayouts instead of
        // recomposing the controls on every frame.
        val weights = floatArrayOf(prevWeight.value, playWeight.value, nextWeight.value)
        val gap = ButtonGap.roundToPx()
        val available = (constraints.maxWidth - gap * (measurables.size - 1)).coerceAtLeast(0)
        val totalWeight = weights.sum()
        var cumulativeWeight = 0f
        var start = 0
        val placeables = measurables.mapIndexed { index, measurable ->
            cumulativeWeight += weights[index]
            val end = (available * cumulativeWeight / totalWeight).roundToInt()
            measurable.measure(Constraints.fixed(end - start, constraints.maxHeight))
                .also { start = end }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + gap
            }
        }
    }
}

private val ButtonGap = 6.dp
private const val PLAY_PAUSE_CONFIRM_TIMEOUT_MS = 1_000L

