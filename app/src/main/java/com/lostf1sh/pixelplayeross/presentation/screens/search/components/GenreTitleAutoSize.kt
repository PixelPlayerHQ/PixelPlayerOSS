package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import kotlin.math.floor

/** How a genre title lays out at one font size. */
internal enum class GenreTitleFit {
    /** The whole name fits on one line. */
    SingleLine,

    /** The name fits within the allowed lines, breaking only where a line may break. */
    Wrapped,

    /** The name is cut off, ellipsized, or split inside a word. */
    Overflow
}

/**
 * Fits a genre tile title by shrinking its display style within bounds, wrapping only between
 * words (or after a hyphen), never inside one.
 *
 * [TextAutoSize.StepBased] accepts any layout that is not cut off, and the line breaker splits a
 * word that is too wide for the line ("Alternativ" / "e"), so it would keep a size that breaks a
 * name apart. This strategy rejects those layouts, prefers a single line while the name needs at
 * most a small shrink down to [singleLineMinFontSizeSp], and then takes the largest size that
 * fits within the Text's maxLines. When nothing fits, the Text ellipsizes at [minFontSizeSp].
 *
 * Every candidate size reuses the card's one typeface, so the search costs a few text layouts in
 * the layout phase and no font loads.
 */
data class GenreTitleAutoSize(
    val maxFontSizeSp: Float,
    val minFontSizeSp: Float,
    val singleLineMinFontSizeSp: Float,
    val stepSp: Float = DEFAULT_STEP_SP
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString
    ): TextUnit {
        var lineBreaks: BreakIterator? = null
        val isBreakAllowed = { offset: Int ->
            val breaks = lineBreaks ?: BreakIterator.getLineInstance().also {
                it.setText(text.text)
                lineBreaks = it
            }
            breaks.isBoundary(offset)
        }
        return chooseGenreTitleFontSize(
            maxFontSizeSp = maxFontSizeSp,
            minFontSizeSp = minFontSizeSp,
            singleLineMinFontSizeSp = singleLineMinFontSizeSp,
            stepSp = stepSp
        ) { fontSizeSp ->
            performLayout(constraints, text, fontSizeSp.sp).genreTitleFit(isBreakAllowed)
        }.sp
    }

    companion object {
        const val DEFAULT_STEP_SP = 0.5f
    }
}

/**
 * Picks the font size for a genre title, stepping down from [maxFontSizeSp] by [stepSp]:
 * the largest single-line size no smaller than [singleLineMinFontSizeSp], else the largest size
 * that fits at all, else [minFontSizeSp]. [fitAt] lays the title out at a size; a smaller size
 * never fits worse, so each search is a binary search.
 */
internal fun chooseGenreTitleFontSize(
    maxFontSizeSp: Float,
    minFontSizeSp: Float,
    singleLineMinFontSizeSp: Float,
    stepSp: Float,
    fitAt: (fontSizeSp: Float) -> GenreTitleFit
): Float {
    require(stepSp > 0f) { "stepSp must be positive" }
    val lowest = minFontSizeSp.coerceAtMost(maxFontSizeSp)
    val totalSteps = stepsBetween(maxFontSizeSp, lowest, stepSp)
    val singleLineSteps = stepsBetween(
        maxFontSizeSp,
        singleLineMinFontSizeSp.coerceIn(lowest, maxFontSizeSp),
        stepSp
    )
    val fits = arrayOfNulls<GenreTitleFit>(totalSteps + 1)
    fun fitAtStep(step: Int): GenreTitleFit =
        fits[step] ?: fitAt(maxFontSizeSp - step * stepSp).also { fits[step] = it }

    val atMax = fitAtStep(0)
    if (atMax == GenreTitleFit.SingleLine) return maxFontSizeSp

    val singleLineStep = firstStepThatFits(1, singleLineSteps) {
        fitAtStep(it) == GenreTitleFit.SingleLine
    }
    if (singleLineStep != null) return maxFontSizeSp - singleLineStep * stepSp
    if (atMax == GenreTitleFit.Wrapped) return maxFontSizeSp

    val fittingStep = firstStepThatFits(1, totalSteps) {
        fitAtStep(it) != GenreTitleFit.Overflow
    } ?: totalSteps
    return maxFontSizeSp - fittingStep * stepSp
}

/**
 * Classifies a laid-out title. [lineEnd] gives the end offset of a line and [isBreakAllowed]
 * says whether a line may break at an offset of the text.
 */
internal fun genreTitleFit(
    lineCount: Int,
    didOverflow: Boolean,
    isLastLineEllipsized: Boolean,
    lineEnd: (line: Int) -> Int,
    isBreakAllowed: (offset: Int) -> Boolean
): GenreTitleFit {
    if (didOverflow || isLastLineEllipsized) return GenreTitleFit.Overflow
    for (line in 0 until lineCount - 1) {
        if (!isBreakAllowed(lineEnd(line))) return GenreTitleFit.Overflow
    }
    return if (lineCount <= 1) GenreTitleFit.SingleLine else GenreTitleFit.Wrapped
}

private fun TextLayoutResult.genreTitleFit(isBreakAllowed: (Int) -> Boolean): GenreTitleFit =
    genreTitleFit(
        lineCount = lineCount,
        didOverflow = didOverflowWidth || didOverflowHeight,
        isLastLineEllipsized = lineCount > 0 && isLineEllipsized(lineCount - 1),
        lineEnd = { line -> getLineEnd(line) },
        isBreakAllowed = isBreakAllowed
    )

private fun stepsBetween(from: Float, to: Float, step: Float): Int =
    floor((from - to) / step + STEP_TOLERANCE).toInt().coerceAtLeast(0)

/** The smallest step in [from]..[to] that [fits], given that every larger step fits too. */
private inline fun firstStepThatFits(from: Int, to: Int, fits: (Int) -> Boolean): Int? {
    var low = from
    var high = to
    var found: Int? = null
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (fits(mid)) {
            found = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }
    return found
}

private const val STEP_TOLERANCE = 1e-3f
