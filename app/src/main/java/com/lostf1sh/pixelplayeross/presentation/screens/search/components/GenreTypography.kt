package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.R
import java.text.BreakIterator
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

object GenreTypography {

    /**
     * A genre tile title: the name, its display style, how many lines it may take, and the
     * bounds [autoSize] shrinks the style within so the name fits the tile.
     */
    data class TitlePresentation(
        val text: String,
        val style: TextStyle,
        val maxLines: Int,
        val autoSize: GenreTitleAutoSize
    )

    @OptIn(ExperimentalTextApi::class)
    fun getGenreStyle(genreId: String, genreName: String): TextStyle {
        val normalizedName = normalize(genreName)
        val profile = GenreTitleProfile.from(normalizedName)
        val hash = genreId.hashCode().toLong().absoluteValue
        return buildStyleCandidates(hash = hash, profile = profile, isGridView = true).first()
    }

    /**
     * Resolves the card title without synchronous text measurement.
     *
     * An earlier card path tried several variable-font styles and every word break through
     * TextMeasurer during composition, loading a typeface per style; a handful of visible cards
     * was enough to block a low-end device's first Search frame. This picks one style from the
     * name's shape instead, and [GenreTitleAutoSize] fits it to the tile in the layout phase by
     * changing only the font size: grid tiles may wrap onto a second line between words, list
     * rows keep one line, and a name that still does not fit at the smallest size ellipsizes.
     */
    fun resolveTitlePresentationFast(
        genreId: String,
        genreName: String,
        isGridView: Boolean,
    ): TitlePresentation {
        val normalizedName = normalize(genreName)
        val profile = GenreTitleProfile.from(normalizedName)
        val hash = genreId.hashCode().toLong().absoluteValue
        val styles = buildStyleCandidates(hash = hash, profile = profile, isGridView = isGridView)
        val style = styles[
            when {
                profile.isVeryDense -> styles.lastIndex
                profile.isDense -> 1.coerceAtMost(styles.lastIndex)
                else -> 0
            }
        ]
        val maxFontSizeSp = style.fontSize.value
        // A name with nowhere to break gets one line: past the smallest size, the line breaker
        // would split it inside the word, and one line ellipsizes instead.
        val maxLines = if (isGridView && hasLineBreakOpportunity(normalizedName)) {
            GRID_TITLE_MAX_LINES
        } else {
            SINGLE_TITLE_LINE
        }
        return TitlePresentation(
            text = normalizedName,
            style = style.scalingWithFontSize(),
            maxLines = maxLines,
            autoSize = GenreTitleAutoSize(
                maxFontSizeSp = maxFontSizeSp,
                minFontSizeSp = (maxFontSizeSp * MIN_TITLE_SCALE)
                    .coerceAtLeast(MIN_TITLE_FONT_SIZE_SP)
                    .coerceAtMost(maxFontSizeSp),
                singleLineMinFontSizeSp = maxFontSizeSp * SINGLE_LINE_MIN_TITLE_SCALE
            )
        )
    }

    private val whitespace = Regex("\\s+")

    private fun normalize(genreName: String): String = genreName.trim().replace(whitespace, " ")

    private fun hasLineBreakOpportunity(text: String): Boolean {
        if (text.isEmpty()) return false
        val lineBreaks = BreakIterator.getLineInstance().apply { setText(text) }
        val firstBreak = lineBreaks.following(0)
        return firstBreak != BreakIterator.DONE && firstBreak < text.length
    }

    /**
     * Expresses the spacing in em so it follows the size auto-sizing picks, and wraps only at
     * word boundaries with no inserted hyphens.
     */
    private fun TextStyle.scalingWithFontSize(): TextStyle = copy(
        lineHeight = TITLE_LINE_HEIGHT_EM.em,
        letterSpacing = (letterSpacing.value / fontSize.value).em,
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.None
    )

    private const val GRID_TITLE_MAX_LINES = 2
    private const val SINGLE_TITLE_LINE = 1
    private const val TITLE_LINE_HEIGHT_EM = 0.92f
    private const val SINGLE_LINE_MIN_TITLE_SCALE = 0.8f
    private const val MIN_TITLE_SCALE = 0.6f
    private const val MIN_TITLE_FONT_SIZE_SP = 14f

    @OptIn(ExperimentalTextApi::class)
    private fun buildStyleCandidates(
        hash: Long,
        profile: GenreTitleProfile,
        isGridView: Boolean
    ): List<TextStyle> {
        val baseFontSize = when {
            isGridView && profile.isVeryDense -> 24f
            isGridView && profile.isDense -> 26f
            isGridView && profile.isCompact -> 30f
            isGridView -> 28f
            profile.isVeryDense -> 24f
            profile.isDense -> 27f
            else -> 30f
        }

        val expressiveWidth = when {
            profile.isCompact -> 146f
            profile.isDense -> 126f
            else -> 136f
        } + axisJitter(hash, divisor = 5L, span = 32f)

        val balancedWidth = when {
            profile.isVeryDense -> 104f
            profile.isDense -> 114f
            else -> 124f
        } + axisJitter(hash, divisor = 7L, span = 24f)

        val compactWidth = when {
            profile.longestWord >= 14 -> 84f
            profile.isVeryDense -> 90f
            else -> 98f
        } + axisJitter(hash, divisor = 11L, span = 18f)

        val heavyWeight = 650 + axisJitter(hash, divisor = 13L, span = 180f).roundToInt()
        val mediumWeight = 610 + axisJitter(hash, divisor = 17L, span = 150f).roundToInt()
        val compactWeight = 580 + axisJitter(hash, divisor = 19L, span = 120f).roundToInt()

        val slantExpressive = slantVariant(hash, divisor = 23L, options = floatArrayOf(0f, -4f, -8f, -12f))
        val slantBalanced = slantVariant(hash, divisor = 29L, options = floatArrayOf(0f, -3f, -6f, -9f))
        val slantCompact = slantVariant(hash, divisor = 31L, options = floatArrayOf(0f, -2f, -4f, -6f))

        val rondExpressive = 58f + axisJitter(hash, divisor = 37L, span = 72f)
        val rondBalanced = 44f + axisJitter(hash, divisor = 41L, span = 54f)
        val rondCompact = 36f + axisJitter(hash, divisor = 43L, span = 44f)

        val gradeExpressive = axisJitter(hash, divisor = 47L, span = 120f).roundToInt()
        val gradeBalanced = axisJitter(hash, divisor = 53L, span = 90f).roundToInt()
        val gradeCompact = axisJitter(hash, divisor = 59L, span = 70f).roundToInt()

        val xtraBase = 510f + axisJitter(hash, divisor = 61L, span = 42f)
        val yopqBase = 88f + axisJitter(hash, divisor = 67L, span = 22f)
        val ytlcBase = 500f + axisJitter(hash, divisor = 71L, span = 34f)

        return listOf(
            expressiveStyle(
                fontSize = baseFontSize + if (profile.isCompact) 1.5f else 0.5f,
                weight = heavyWeight.coerceIn(560, 820),
                width = expressiveWidth.coerceIn(102f, 166f),
                slant = slantExpressive,
                grade = gradeExpressive.coerceIn(-60, 120),
                rond = rondExpressive.coerceIn(18f, 120f),
                xtra = (xtraBase + 10f).coerceIn(490f, 560f),
                yopq = (yopqBase + 4f).coerceIn(76f, 110f),
                ytlc = (ytlcBase + 8f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize,
                weight = mediumWeight.coerceIn(540, 780),
                width = (balancedWidth + 10f).coerceIn(96f, 152f),
                slant = slantExpressive,
                grade = (gradeExpressive / 2).coerceIn(-50, 100),
                rond = (rondExpressive + 12f).coerceIn(24f, 126f),
                xtra = (xtraBase + 4f).coerceIn(490f, 560f),
                yopq = (yopqBase + 2f).coerceIn(76f, 110f),
                ytlc = ytlcBase.coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 0.5f,
                weight = heavyWeight.coerceIn(560, 820),
                width = balancedWidth.coerceIn(92f, 144f),
                slant = slantBalanced,
                grade = gradeBalanced.coerceIn(-50, 90),
                rond = rondBalanced.coerceIn(18f, 108f),
                xtra = xtraBase.coerceIn(490f, 560f),
                yopq = yopqBase.coerceIn(76f, 110f),
                ytlc = ytlcBase.coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 1.5f,
                weight = mediumWeight.coerceIn(540, 780),
                width = (balancedWidth - 10f).coerceIn(88f, 136f),
                slant = slantBalanced,
                grade = (gradeBalanced / 2).coerceIn(-40, 80),
                rond = (rondBalanced + 10f).coerceIn(24f, 112f),
                xtra = (xtraBase - 4f).coerceIn(490f, 560f),
                yopq = yopqBase.coerceIn(76f, 110f),
                ytlc = (ytlcBase - 4f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 2.5f,
                weight = compactWeight.coerceIn(520, 740),
                width = compactWidth.coerceIn(78f, 120f),
                slant = slantCompact,
                grade = gradeCompact.coerceIn(-35, 65),
                rond = rondCompact.coerceIn(18f, 96f),
                xtra = (xtraBase - 8f).coerceIn(490f, 560f),
                yopq = (yopqBase - 4f).coerceIn(76f, 110f),
                ytlc = (ytlcBase - 8f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 4f,
                weight = (compactWeight - 30).coerceIn(500, 700),
                width = (compactWidth - 12f).coerceIn(72f, 108f),
                slant = slantCompact,
                grade = (gradeCompact / 2).coerceIn(-30, 50),
                rond = (rondCompact + 8f).coerceIn(24f, 102f),
                xtra = (xtraBase - 12f).coerceIn(490f, 560f),
                yopq = (yopqBase - 6f).coerceIn(76f, 110f),
                ytlc = (ytlcBase - 10f).coerceIn(474f, 528f)
            )
        )
    }

    @OptIn(ExperimentalTextApi::class)
    private fun expressiveStyle(
        fontSize: Float,
        weight: Int,
        width: Float,
        slant: Float,
        grade: Int,
        rond: Float,
        xtra: Float,
        yopq: Float,
        ytlc: Float
    ): TextStyle {
        return TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.width(width),
                        FontVariation.slant(slant),
                        FontVariation.grade(grade),
                        FontVariation.Setting("ROND", rond),
                        FontVariation.Setting("XTRA", xtra),
                        FontVariation.Setting("YOPQ", yopq),
                        FontVariation.Setting("YTLC", ytlc)
                    )
                )
            ),
            fontWeight = FontWeight(weight),
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 0.92f).sp,
            letterSpacing = when {
                width >= 136f -> (-0.7).sp
                width >= 118f -> (-0.45).sp
                else -> (-0.18).sp
            }
        )
    }

    private fun slantVariant(hash: Long, divisor: Long, options: FloatArray): Float {
        val index = ((hash / divisor) % options.size).toInt()
        return options[index]
    }

    private fun axisJitter(hash: Long, divisor: Long, span: Float): Float {
        val normalized = ((hash / divisor) % 1000).toFloat() / 999f
        return (normalized - 0.5f) * span
    }

    private data class GenreTitleProfile(
        val totalChars: Int,
        val wordCount: Int,
        val longestWord: Int
    ) {
        val isCompact: Boolean = totalChars <= 8 && wordCount <= 2 && longestWord <= 8
        val isDense: Boolean = totalChars >= 14 || wordCount >= 3 || longestWord >= 10
        val isVeryDense: Boolean = totalChars >= 18 || wordCount >= 4 || longestWord >= 13

        companion object {
            fun from(text: String): GenreTitleProfile {
                val words = text.split(" ").filter { it.isNotBlank() }
                return GenreTitleProfile(
                    totalChars = text.length,
                    wordCount = words.size,
                    longestWord = words.maxOfOrNull { it.length } ?: text.length
                )
            }
        }
    }
}
