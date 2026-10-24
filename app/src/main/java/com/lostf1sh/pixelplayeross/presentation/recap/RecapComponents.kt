@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.recap

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import java.text.NumberFormat
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.isActive

/** How far apart, in reveal progress, consecutive elements of a slide start appearing. */
private const val REVEAL_STAGGER = 0.08f

/** How much reveal progress one element takes to appear. */
private const val REVEAL_WINDOW = 0.42f

/** One reveal pass of a slide, from its first element starting to its last one landing. */
internal const val RECAP_REVEAL_DURATION_MS = 1500

/** How much, in sp, a count-up's font shrinks per try until its final value fits. */
private const val COUNT_UP_SIZE_STEP = 2f

// M3 emphasized-decelerate: elements arrive quickly and settle gently.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

internal val RecapGutter = 28.dp
private val RecapMaxContentWidth = 560.dp

/**
 * Colours of one slide: [background] fills the page and [content] is drawn on it; [accent] marks
 * the hero element, with [onAccent] on top. Built from scheme roles, so it follows the app theme
 * (dynamic or not) in light and dark mode.
 */
@Immutable
internal data class RecapPalette(
    val background: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color
)

@Composable
internal fun rememberRecapPalettes(): ImmutableList<RecapPalette> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        // Some schemes use translucent containers; flatten them so pages never show through.
        fun Color.opaque() = compositeOver(scheme.surface)
        persistentListOf(
            RecapPalette(scheme.primary.opaque(), scheme.onPrimary, scheme.primaryContainer.opaque(), scheme.onPrimaryContainer),
            RecapPalette(scheme.primaryContainer.opaque(), scheme.onPrimaryContainer, scheme.primary.opaque(), scheme.onPrimary),
            RecapPalette(scheme.tertiaryContainer.opaque(), scheme.onTertiaryContainer, scheme.tertiary.opaque(), scheme.onTertiary),
            RecapPalette(scheme.secondaryContainer.opaque(), scheme.onSecondaryContainer, scheme.secondary.opaque(), scheme.onSecondary),
            RecapPalette(scheme.secondary.opaque(), scheme.onSecondary, scheme.secondaryContainer.opaque(), scheme.onSecondaryContainer),
            RecapPalette(scheme.tertiary.opaque(), scheme.onTertiary, scheme.tertiaryContainer.opaque(), scheme.onTertiaryContainer)
        )
    }
}

/** Palettes cycle by page, so neighbouring slides always differ whichever slides are present. */
internal fun ImmutableList<RecapPalette>.forPage(page: Int): RecapPalette = this[page.mod(size)]

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberRecapFlexFont(weight: Int, width: Float = 100f, rond: Float = 100f): FontFamily =
    remember(weight, width, rond) {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width.coerceIn(25f, 151f)),
                    FontVariation.Setting("ROND", rond)
                )
            )
        )
    }

/**
 * How far an element staggered [order] places into a slide has appeared, from 0 to 1, when the
 * slide's own reveal is at [progress].
 */
internal fun revealFraction(progress: Float, order: Float): Float {
    val local = ((progress - order * REVEAL_STAGGER) / REVEAL_WINDOW).coerceIn(0f, 1f)
    return EmphasizedDecelerate.transform(local)
}

/** Eased progress through the part of a slide's reveal from [start] to [start] + [length]. */
internal fun revealSpan(progress: Float, start: Float, length: Float): Float =
    EmphasizedDecelerate.transform(((progress - start) / length).coerceIn(0f, 1f))

/**
 * Fades and lifts an element in as its slide reveals. [progress] is only read in the draw phase,
 * so a revealing slide never recomposes.
 */
internal fun Modifier.recapReveal(progress: () -> Float, order: Int, distance: Dp = 32.dp): Modifier =
    graphicsLayer {
        val fraction = revealFraction(progress(), order.toFloat())
        alpha = fraction
        translationY = (1f - fraction) * distance.toPx()
    }

/**
 * False when the user turned animations off (animator duration scale 0). Follows the setting
 * while shown, since changing it does not recreate the activity: an open recap must stop moving
 * on by itself as soon as animations are switched off.
 */
@Composable
internal fun rememberRecapMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(resolver.isRecapMotionEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = resolver.isRecapMotionEnabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    // A change made while the app sat in the background may not have been delivered.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = resolver.isRecapMotionEnabled()
    }
    return enabled
}

private fun ContentResolver.isRecapMotionEnabled(): Boolean =
    Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/**
 * A slowly growing angle in degrees while [active]; it holds still otherwise. Callers pass
 * false when motion is off, or the loop would tick every frame for nothing.
 */
@Composable
internal fun rememberSpin(active: Boolean, periodMillis: Int): State<Float> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(active, periodMillis) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            angle.snapTo(angle.value % 360f)
            angle.animateTo(angle.value + 360f, tween(periodMillis, easing = LinearEasing))
        }
    }
    return angle.asState()
}

/**
 * Full-page slide body: centred vertically when it fits, scrollable when it does not (landscape,
 * large fonts), and kept to a readable width on tablets.
 */
@Composable
internal fun RecapSlideColumn(
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .widthIn(max = RecapMaxContentWidth)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .heightIn(min = minHeight)
                .padding(contentPadding)
                .padding(horizontal = RecapGutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
internal fun RecapKicker(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = rememberRecapFlexFont(weight = 640, width = 112f),
        color = color.copy(alpha = 0.82f),
        textAlign = textAlign
    )
}

/** Big display text that shrinks to fit instead of wrapping past [maxLines]. */
@Composable
internal fun RecapHeadline(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 44.sp,
    minFontSize: TextUnit = 24.sp,
    maxLines: Int = 2,
    weight: Int = 820,
    textAlign: TextAlign = TextAlign.Start
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = rememberRecapFlexFont(weight = weight, width = 116f),
            fontSize = maxFontSize,
            lineHeight = maxFontSize * 1.08f,
            letterSpacing = (-0.4).sp,
            color = color,
            textAlign = textAlign
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize, stepSize = 2.sp)
    )
}

@Composable
internal fun RecapBody(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = rememberRecapFlexFont(weight = 480, width = 104f),
        color = color.copy(alpha = 0.9f),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * A number counting up to [target] as [progress] goes from 0 to 1. Only this text recomposes
 * while it counts, and only when the formatted value actually changes. Its size is chosen once,
 * for [target], so the number never shrinks partway through as it gains digits.
 */
@Composable
internal fun RecapCountUpText(
    target: Long,
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 96.sp,
    minFontSize: TextUnit = 40.sp,
    textAlign: TextAlign = TextAlign.Start
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val style = TextStyle(
        fontFamily = rememberRecapFlexFont(weight = 940, width = 122f),
        fontSize = maxFontSize,
        lineHeight = maxFontSize,
        letterSpacing = (-1.5).sp,
        color = color,
        textAlign = textAlign
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    // Passing the minimum width on lets a caller's fillMaxWidth (and so textAlign) reach the text.
    BoxWithConstraints(modifier = modifier, propagateMinConstraints = true) {
        val fittedStyle = remember(target, formatter, style, constraints.maxWidth, textMeasurer) {
            val fontSize = textMeasurer.largestFittingFontSize(
                text = formatter.format(target),
                style = style,
                maxWidth = constraints.maxWidth,
                minFontSize = minFontSize,
                maxFontSize = maxFontSize
            )
            style.copy(fontSize = fontSize, lineHeight = fontSize)
        }
        val text by remember(target, formatter) {
            derivedStateOf { formatter.format((target * progress().coerceIn(0f, 1f)).roundToLong()) }
        }
        BasicText(text = text, style = fittedStyle, maxLines = 1, softWrap = false)
    }
}

/**
 * The largest size from [maxFontSize] down to [minFontSize], in [COUNT_UP_SIZE_STEP] steps, at
 * which [text] fits on one line within [maxWidth] pixels; [minFontSize] when none does.
 */
private fun TextMeasurer.largestFittingFontSize(
    text: String,
    style: TextStyle,
    maxWidth: Int,
    minFontSize: TextUnit,
    maxFontSize: TextUnit
): TextUnit {
    if (maxWidth == Constraints.Infinity) return maxFontSize
    var size = maxFontSize.value
    while (size > minFontSize.value) {
        val width = measure(text, style.copy(fontSize = size.sp, lineHeight = size.sp), softWrap = false, maxLines = 1)
            .size
            .width
        if (width <= maxWidth) return size.sp
        size -= COUNT_UP_SIZE_STEP
    }
    return minFontSize
}

/** A small rounded tag on a slide, e.g. a play count or a duration. */
@Composable
internal fun RecapChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = rememberRecapFlexFont(weight = 650, width = 106f),
        color = color,
        maxLines = 1
    )
}

/** A labelled figure, e.g. "Daily average · 34m", optionally with a smaller [detail] line. */
@Composable
internal fun RecapStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = rememberRecapFlexFont(weight = 600, width = 104f),
            color = color.copy(alpha = 0.78f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            style = TextStyle(
                fontFamily = rememberRecapFlexFont(weight = 800, width = 112f),
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = color
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 1.sp)
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Initials for an image-less artist, or the app's placeholder when the name has none. */
@Composable
internal fun rememberMonogram(name: String): String {
    val placeholder = stringResource(R.string.presentation_batch_g_stats_avatar_placeholder)
    return remember(name, placeholder) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercaseChar().toString() }
            .ifBlank { placeholder }
    }
}

/**
 * Artwork clipped to [shape]; without a [model] it shows [monogram] (for artists) or the music
 * placeholder.
 */
@Composable
internal fun RecapArtwork(
    model: String?,
    shape: Shape,
    palette: RecapPalette,
    modifier: Modifier = Modifier,
    monogram: String? = null,
    targetSizePx: Int = 512
) {
    if (model.isNullOrBlank() && monogram != null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(palette.accent),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = monogram,
                modifier = Modifier.padding(8.dp),
                style = TextStyle(
                    fontFamily = rememberRecapFlexFont(weight = 860, width = 116f),
                    fontSize = 64.sp,
                    color = palette.onAccent,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 64.sp, stepSize = 2.sp)
            )
        }
    } else {
        SmartImage(
            model = model,
            contentDescription = null,
            shape = shape,
            targetSize = remember(targetSizePx) { coil.size.Size(targetSizePx, targetSizePx) },
            placeHolderBackgroundColor = palette.accent,
            modifier = modifier
        )
    }
}

/** One row of a ranked list under a slide's hero, e.g. "2  Artist name  12 h". */
@Composable
internal fun RecapRankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    trailing: String?,
    palette: RecapPalette,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val rankText = remember(rank, locale) { NumberFormat.getIntegerInstance(locale).format(rank) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.content.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = rankText,
            modifier = Modifier.widthIn(min = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = rememberRecapFlexFont(weight = 820, width = 110f),
            color = palette.content,
            textAlign = TextAlign.Center
        )
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = rememberRecapFlexFont(weight = 660, width = 104f),
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = rememberRecapFlexFont(weight = 680, width = 104f),
                color = palette.content.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}

/**
 * A large expressive shape that slowly rotates and morphs through [shapes] while [active],
 * drawn in one Canvas whose animation is only read in the draw phase.
 */
@Composable
internal fun RecapMorphBlob(
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    shapes: List<RoundedPolygon> = DefaultBlobShapes,
    periodMillis: Int = 16_000
) {
    val morphs = remember(shapes) { (shapes + shapes.first()).zipWithNext { a, b -> Morph(a, b) } }
    val scratch = remember { android.graphics.Path() }
    val spin = rememberSpin(active = active, periodMillis = periodMillis)
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        // One full turn walks through every morph once and returns to the first shape.
        val turn = (spin.value % 360f) / 360f * morphs.size
        val index = turn.toInt().coerceIn(0, morphs.lastIndex)
        val fraction = EmphasizedDecelerate.transform((turn - index).coerceIn(0f, 1f))
        scratch.rewind()
        morphs[index].toPath(fraction, scratch)
        val diameter = size.minDimension
        withTransform({
            translate(center.x, center.y)
            rotate(spin.value * 0.5f, pivot = Offset.Zero)
            scale(diameter, diameter, pivot = Offset.Zero)
            translate(-0.5f, -0.5f)
        }) {
            drawPath(scratch.asComposePath(), color)
        }
    }
}

private val DefaultBlobShapes = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Cookie9Sided
)

/**
 * The story's segmented progress: finished slides full, the current one filling with
 * [progress], later ones empty. Every value is read in the draw phase.
 */
@Composable
internal fun RecapProgressBar(
    count: Int,
    currentPage: () -> Int,
    progress: () -> Float,
    color: () -> Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (count <= 0) return@Canvas
        val gap = 4.dp.toPx()
        val segmentWidth = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(size.height / 2f)
        val tint = color()
        val page = currentPage()
        val current = progress().coerceIn(0f, 1f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (index in 0 until count) {
            val slot = if (rtl) count - 1 - index else index
            val left = slot * (segmentWidth + gap)
            drawRoundRect(
                color = tint.copy(alpha = 0.3f),
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius
            )
            val fill = when {
                index < page -> 1f
                index == page -> current
                else -> 0f
            }
            if (fill > 0f) {
                val width = segmentWidth * fill
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(if (rtl) left + segmentWidth - width else left, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

/** A rotating, decorative shape bleeding off a slide's corner. */
@Composable
internal fun RecapBackdropShape(
    shape: Shape,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 340.dp,
    periodMillis: Int = 40_000
) {
    val spin = rememberSpin(active = active, periodMillis = periodMillis)
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { }
            .graphicsLayer { rotationZ = spin.value }
            .clip(shape)
            .background(color)
    )
}
