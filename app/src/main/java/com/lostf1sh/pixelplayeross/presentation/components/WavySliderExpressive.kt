package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How long after the wave's amplitude target drops to 0 the indicator is certainly flat.
 * LinearWavyProgressIndicator fades amplitude over 500 ms, and it can start that fade up to 500 ms
 * late because it ignores a new target until a running amplitude animation (say, from a resume just
 * before the pause) finishes. The rest is slack for the frames it takes to pick the target up and
 * for dropped frames.
 */
private const val WaveFlattenMillis = 1_500L

/**
 * Normalizes [value] into 0..1. Non-finite input is treated as 0 so a single bad value can never
 * poison the rendered progress (NaN would survive every later interpolation).
 */
private fun normalizeValue(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float {
    if (!value.isFinite()) return 0f
    val span = valueRange.endInclusive - valueRange.start
    if (span == 0f) return 0f
    return ((value - valueRange.start) / span).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,

    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,

    waveAmplitudeWhenPlaying: Dp = 4.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    // Read through the latest provider instead of capturing the first one: callers may swap the
    // state object backing `value` (for example a remember() keyed on the current song), and a
    // captured lambda would keep reading the abandoned state forever.
    val latestValueProvider by rememberUpdatedState(value)

    val normalizedValueState = remember(valueRange) {
        derivedStateOf {
            val v = latestValueProvider()
            if (valueRange.endInclusive == valueRange.start) 0f
            else normalizeValue(v, valueRange)
        }
    }

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValueState = remember(safeSemanticsStep, normalizedValueState) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember(valueRange, semanticNormalizedValueState) {
        derivedStateOf {
            valueRange.start + semanticNormalizedValueState.value * (valueRange.endInclusive - valueRange.start)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )
    // LinearWavyProgressIndicator animates amplitude changes itself and ignores a new target while
    // one of those animations is running, so it must get this target rather than the animated value
    // below: fed the in-between values, it settled on a small non-zero amplitude after a pause, and
    // its wave offset animation then redrew the paused player every frame.
    val waveAmplitudeTarget = if (enabled && isPlaying && !isInteracting) 1f else 0f

    // While waveSpeed is above zero the indicator runs its wave offset animation every frame, even
    // after the wave has gone flat, which kept a paused player waking the main thread at the display
    // refresh rate. Stop it once the indicator's own amplitude fade must be over. A zero speed snaps
    // the wave phase back to 0, which can't be seen on a flat wave but would jump a visible one.
    var isWaveStopped by remember { mutableStateOf(waveAmplitudeTarget == 0f) }
    LaunchedEffect(waveAmplitudeTarget) {
        if (waveAmplitudeTarget > 0f) {
            isWaveStopped = false
        } else if (!isWaveStopped) {
            val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((WaveFlattenMillis * durationScale).toLong())
            isWaveStopped = true
        }
    }
    // Restarts in the same composition that raises the amplitude target, so the wave never grows
    // back while frozen.
    val indicatorWaveSpeed = if (waveAmplitudeTarget > 0f || !isWaveStopped) waveSpeed else 0.dp

    val animatedAmplitude by animateFloatAsState(
        targetValue = waveAmplitudeTarget,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    val renderedNormalizedProgress = remember {
        val initialNorm = if (valueRange.endInclusive == valueRange.start) 0f
            else normalizeValue(latestValueProvider(), valueRange)
        mutableFloatStateOf(initialNorm)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isInteracting, enabled, normalizedValueState) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            if (!enabled || isInteracting) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val start = renderedNormalizedProgress.floatValue
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val nowNanos = System.nanoTime()
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collect
            }

            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var startFrameNanos = 0L
            var liveTarget = target
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (startFrameNanos == 0L) startFrameNanos = frameNanos
                // Re-read the target every frame so the drawn position keeps converging even if an
                // upstream emission is missed while this smoothing pass runs.
                liveTarget = normalizedValueState.value
                val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                renderedNormalizedProgress.floatValue = start + (liveTarget - start) * fraction
                if (fraction >= 1f) break
            }
            renderedNormalizedProgress.floatValue = liveTarget
        }
    }

    val containerHeight = max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValueState.value,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit?.invoke(coerced)
                            ?: latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Media progress is never mirrored; forcing LTR also keeps the indicator (which would mirror)
        // aligned with the thumb and touch math below, which use absolute x coordinates.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (isVisible) {
                LinearWavyProgressIndicator(
                    progress = { renderedNormalizedProgress.floatValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                        .clearAndSetSemantics { },
                    color = activeTrackColor,
                    trackColor = inactiveTrackColor,
                    stroke = stroke,
                    trackStroke = stroke,
                    gapSize = 2f * dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
                    stopSize = 3.dp,
                    amplitude = { progress -> if (progress > 0f) waveAmplitudeTarget else 0f },
                    wavelength = wavelength,
                    waveSpeed = indicatorWaveSpeed
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(containerHeight))
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (!isVisible) return@Canvas
                val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePaddingPx
                val trackEnd = size.width - edgePaddingPx
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val thumbY = size.height / 2
                val renderedProgress = renderedNormalizedProgress.floatValue

                fun lerp(start: Float, stop: Float, fraction: Float): Float {
                    return start + (stop - start) * fraction
                }

                val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
                val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
                val rawThumbX = trackStart + (trackWidth * renderedProgress)
                val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
                val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
                val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(
                        thumbX - currentWidth / 2f,
                        thumbY - currentHeight / 2f
                    ),
                    size = Size(currentWidth, currentHeight),
                    cornerRadius = CornerRadius(currentWidth / 2f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, valueRange, trackEdgePaddingPx) {
                        if (!enabled) return@pointerInput

                        fun valueForX(rawX: Float): Float {
                            val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                            val trackStart = edgePadding
                            val trackEnd = size.width - edgePadding
                            val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                            val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                            return valueRange.start +
                                normalized * (valueRange.endInclusive - valueRange.start)
                        }

                        awaitEachGesture {
                            var gestureValue: Float? = null
                            var committed = false
                            try {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isPointerSeeking = true
                                down.consume()
                                var latestGestureValue = valueForX(down.position.x)
                                gestureValue = latestGestureValue
                                latestOnValueChange(latestGestureValue)

                                var pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                    pointerId = change.id
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    if (change.position != change.previousPosition) {
                                        change.consume()
                                        latestGestureValue = valueForX(change.position.x)
                                        gestureValue = latestGestureValue
                                        latestOnValueChange(latestGestureValue)
                                    }
                                }

                                committed = true
                                latestOnValueCommit?.invoke(latestGestureValue)
                                    ?: latestOnValueChangeFinished?.invoke()
                            } finally {
                                isPointerSeeking = false
                                // A gesture can be torn down without an up event (pointerInput restart
                                // or disposal). Always terminate it, otherwise callers that latch a
                                // "user is scrubbing" flag in onValueChange stay latched forever and
                                // stop following playback.
                                if (!committed) {
                                    gestureValue?.let { pendingValue ->
                                        latestOnValueCommit?.invoke(pendingValue)
                                            ?: latestOnValueChangeFinished?.invoke()
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}
