package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import com.google.common.truth.Truth.assertThat
import java.text.BreakIterator
import org.junit.jupiter.api.Test

class GenreTitleAutoSizeTest {

    // A title box that fits one line at or below [singleLineUpTo] and, when wrapping is
    // possible, two lines at or below [wrappedUpTo].
    private class FakeTitle(
        private val singleLineUpTo: Float,
        private val wrappedUpTo: Float? = null
    ) {
        val laidOutAt = mutableListOf<Float>()

        fun fitAt(fontSizeSp: Float): GenreTitleFit {
            laidOutAt += fontSizeSp
            return when {
                fontSizeSp <= singleLineUpTo -> GenreTitleFit.SingleLine
                wrappedUpTo != null && fontSizeSp <= wrappedUpTo -> GenreTitleFit.Wrapped
                else -> GenreTitleFit.Overflow
            }
        }
    }

    private fun choose(title: FakeTitle) = chooseGenreTitleFontSize(
        maxFontSizeSp = 28f,
        minFontSizeSp = 16.5f,
        singleLineMinFontSizeSp = 22.5f,
        stepSp = 0.5f,
        fitAt = title::fitAt
    )

    @Test
    fun `name that fits keeps the display size after one layout`() {
        val title = FakeTitle(singleLineUpTo = 30f)

        assertThat(choose(title)).isEqualTo(28f)
        assertThat(title.laidOutAt).containsExactly(28f)
    }

    @Test
    fun `slightly too wide name shrinks onto one line instead of wrapping`() {
        // "Indie Rock" could wrap at full size, but a small shrink keeps it on one line.
        val title = FakeTitle(singleLineUpTo = 25.2f, wrappedUpTo = 40f)

        assertThat(choose(title)).isEqualTo(25f)
    }

    @Test
    fun `name that needs a large shrink wraps at the display size instead`() {
        val title = FakeTitle(singleLineUpTo = 19f, wrappedUpTo = 40f)

        assertThat(choose(title)).isEqualTo(28f)
    }

    @Test
    fun `single long word shrinks below the single line floor until it fits whole`() {
        // "Alternative" at a large font scale: it cannot wrap, so only shrinking helps.
        val title = FakeTitle(singleLineUpTo = 18.7f)

        assertThat(choose(title)).isEqualTo(18.5f)
    }

    @Test
    fun `wrapped name shrinks until both lines fit`() {
        val title = FakeTitle(singleLineUpTo = 12f, wrappedUpTo = 21.3f)

        assertThat(choose(title)).isEqualTo(21f)
    }

    @Test
    fun `name that never fits falls back to the minimum size to ellipsize`() {
        val title = FakeTitle(singleLineUpTo = 10f, wrappedUpTo = 12f)

        assertThat(choose(title)).isEqualTo(16.5f)
    }

    @Test
    fun `search lays out each size at most once`() {
        val title = FakeTitle(singleLineUpTo = 10f, wrappedUpTo = 20.2f)

        choose(title)

        assertThat(title.laidOutAt).containsNoDuplicates()
        assertThat(title.laidOutAt.size).isAtMost(12)
    }

    @Test
    fun `minimum above the maximum keeps the maximum`() {
        val size = chooseGenreTitleFontSize(
            maxFontSizeSp = 14f,
            minFontSizeSp = 18f,
            singleLineMinFontSizeSp = 16f,
            stepSp = 0.5f
        ) { GenreTitleFit.Overflow }

        assertThat(size).isEqualTo(14f)
    }

    @Test
    fun `line break inside a word overflows`() {
        // "Alternativ" / "e": the line breaker's last resort for a word wider than the line.
        val fit = fitOf("Alternative", lineEnds = listOf(10, 11))

        assertThat(fit).isEqualTo(GenreTitleFit.Overflow)
    }

    @Test
    fun `line breaks between words or after a hyphen wrap`() {
        assertThat(fitOf("Indie Rock", lineEnds = listOf(6, 10))).isEqualTo(GenreTitleFit.Wrapped)
        assertThat(fitOf("Hip-Hop", lineEnds = listOf(4, 7))).isEqualTo(GenreTitleFit.Wrapped)
        assertThat(fitOf("Rhythm and Blues", lineEnds = listOf(11, 16)))
            .isEqualTo(GenreTitleFit.Wrapped)
    }

    @Test
    fun `one line that is not cut off fits on a single line`() {
        assertThat(fitOf("Alternative", lineEnds = listOf(11))).isEqualTo(GenreTitleFit.SingleLine)
    }

    @Test
    fun `cut off or ellipsized titles overflow`() {
        assertThat(fitOf("Indie Rock", lineEnds = listOf(10), didOverflow = true))
            .isEqualTo(GenreTitleFit.Overflow)
        assertThat(fitOf("Indie Rock", lineEnds = listOf(10), isLastLineEllipsized = true))
            .isEqualTo(GenreTitleFit.Overflow)
        assertThat(fitOf("Rhythm and Blues", lineEnds = listOf(11, 16), isLastLineEllipsized = true))
            .isEqualTo(GenreTitleFit.Overflow)
    }

    private fun fitOf(
        text: String,
        lineEnds: List<Int>,
        didOverflow: Boolean = false,
        isLastLineEllipsized: Boolean = false
    ): GenreTitleFit {
        val lineBreaks = BreakIterator.getLineInstance().apply { setText(text) }
        return genreTitleFit(
            lineCount = lineEnds.size,
            didOverflow = didOverflow,
            isLastLineEllipsized = isLastLineEllipsized,
            lineEnd = lineEnds::get,
            isBreakAllowed = lineBreaks::isBoundary
        )
    }
}
