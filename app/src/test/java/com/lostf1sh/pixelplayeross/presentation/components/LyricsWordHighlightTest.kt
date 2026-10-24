package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsWordHighlightTest {

    private val whisper = listOf(
        SyncedWord(time = 3_000, word = "whis"),
        SyncedWord(time = 3_400, word = "per", startsNewWord = false)
    )

    @Test
    fun buildLyricClusters_drawsIssue131SyllablesAsWholeWords() {
        val words = LyricsUtils.parseLyrics(
            "[00:07.00]<00:07.00>Kee<00:07.40>ping <00:07.90>me <00:08.30>rest<00:08.80>less"
        ).synced!!.single().words

        val clusters = requireNotNull(buildLyricClusters(words))

        assertEquals(listOf("Keeping", "me", "restless"), clusters.map { it.text })
    }

    @Test
    fun buildLyricClusters_dropsALeadingVoiceMarkerLeftInTheWords() {
        val clusters = buildLyricClusters(
            listOf(SyncedWord(1_000, "v1:"), SyncedWord(1_200, "fall", startsNewWord = false))
        )

        assertEquals(listOf("fall"), clusters!!.map { it.text })
    }

    @Test
    fun buildLyricClusters_returnsNullWithoutTimedWords() {
        assertNull(buildLyricClusters(null))
        assertNull(buildLyricClusters(emptyList()))
    }

    @Test
    fun buildClusterText_recordsWhereEachSyllableStarts() {
        val cluster = buildClusterText(whisper)

        assertEquals("whisper", cluster.text)
        assertArrayEquals(intArrayOf(0, 4), cluster.syllableStarts)
        assertEquals(4, cluster.syllableEnd(0))
        assertEquals(7, cluster.syllableEnd(1))
        assertEquals(3_000L, cluster.startTimeMs)
    }

    @Test
    fun highlightFrame_beforeTheWordStarts_highlightsNothing() {
        val frame = resolveClusterHighlightFrame(whisper, positionMs = 2_999, nextClusterStartMs = 4_000, lineEndMs = 6_000)

        assertEquals(ClusterHighlightFrame.NotStarted, frame)
    }

    @Test
    fun highlightFrame_midSyllable_followsTheTimestamps() {
        val frame = resolveClusterHighlightFrame(whisper, positionMs = 3_100, nextClusterStartMs = 4_000, lineEndMs = 6_000)

        assertEquals(0, frame.syllableIndex)
        assertEquals(0.25f, frame.fraction, 0.001f)
    }

    @Test
    fun highlightFrame_lastSyllableSweepsUntilTheNextWordStarts() {
        val frame = resolveClusterHighlightFrame(whisper, positionMs = 3_700, nextClusterStartMs = 4_000, lineEndMs = 6_000)

        assertEquals(1, frame.syllableIndex)
        assertEquals(0.5f, frame.fraction, 0.001f)
    }

    @Test
    fun highlightFrame_knownEndTimeFinishesTheSyllableEarly() {
        val words = listOf(SyncedWord(time = 1_000, word = "short", endTime = 1_200))

        val frame = resolveClusterHighlightFrame(words, positionMs = 1_300, nextClusterStartMs = 3_000, lineEndMs = 5_000)

        assertEquals(1f, frame.fraction, 0f)
    }

    @Test
    fun highlightFrame_lastWordOfALineWithoutEnd_isCappedSoItDoesNotCrawl() {
        val words = listOf(SyncedWord(time = 10_000, word = "bones"))

        val frame = resolveClusterHighlightFrame(words, positionMs = 10_600, nextClusterStartMs = null, lineEndMs = 20_000)

        assertEquals(10_000L + MAX_TRAILING_SYLLABLE_SWEEP_MS, resolveSyllableEndMs(words, 0, null, 20_000))
        assertEquals(0.5f, frame.fraction, 0.001f)
    }

    @Test
    fun highlightFrame_neverSweepsPastTheLineEnd() {
        val words = listOf(SyncedWord(time = 10_000, word = "quick"))

        assertEquals(10_400L, resolveSyllableEndMs(words, 0, nextClusterStartMs = null, lineEndMs = 10_400))
    }

    @Test
    fun highlightFrame_sameTimestamps_doNotDivideByZero() {
        val words = listOf(SyncedWord(time = 2_000, word = "a"), SyncedWord(time = 2_000, word = "b", startsNewWord = false))

        val frame = resolveClusterHighlightFrame(words, positionMs = 2_000, nextClusterStartMs = 2_000, lineEndMs = 2_000)

        assertEquals(1, frame.syllableIndex)
        assertTrue(frame.fraction in 0f..1f)
    }

    private data class LitPart(val line: Int, val left: Float, val right: Float, val alpha: Float)

    private fun litParts(span: SyllableSpan, syllableAlpha: Float): List<LitPart> = buildList {
        forEachLitPart(span, syllableAlpha) { line, left, right, alpha -> add(LitPart(line, left, right, alpha)) }
    }

    private val open = Float.POSITIVE_INFINITY

    @Test
    fun litParts_instantSyllableWrappedOverLines_isLitThroughItsLastCharacterAtOnce() {
        // A long unspaced phrase: the syllable starts at x=40 on row 0 and ends at x=25 on row 2.
        val span = SyllableSpan(startLine = 0, endLine = 2, fromX = 40f, toX = 25f, rtl = false)

        assertEquals(
            listOf(LitPart(0, -open, open, 1f), LitPart(1, -open, open, 1f), LitPart(2, -open, 25f, 1f)),
            litParts(span, syllableAlpha = 1f)
        )
    }

    @Test
    fun litParts_fadingSyllableWrappedOverLines_fadesAllOfItAndKeepsEarlierSyllablesLit() {
        val span = SyllableSpan(startLine = 1, endLine = 3, fromX = 40f, toX = 25f, rtl = false)

        assertEquals(
            listOf(
                LitPart(0, -open, open, 1f),
                LitPart(1, -open, 40f, 1f),
                LitPart(1, 40f, open, 0.3f),
                LitPart(2, -open, open, 0.3f),
                LitPart(3, -open, 25f, 0.3f)
            ),
            litParts(span, syllableAlpha = 0.3f)
        )
    }

    @Test
    fun litParts_fadingRightToLeftSyllableWrappedOverLines_fadesFromTheRight() {
        val span = SyllableSpan(startLine = 0, endLine = 1, fromX = 60f, toX = 80f, rtl = true)

        assertEquals(
            listOf(
                LitPart(0, 60f, open, 1f),
                LitPart(0, -open, 60f, 0.5f),
                LitPart(1, 80f, open, 0.5f)
            ),
            litParts(span, syllableAlpha = 0.5f)
        )
    }

    @Test
    fun litParts_syllableOnOneLine_fadesOnlyItsOwnGlyphs() {
        val span = SyllableSpan(startLine = 1, endLine = 1, fromX = 10f, toX = 30f, rtl = false)

        assertEquals(
            listOf(LitPart(0, -open, open, 1f), LitPart(1, -open, 10f, 1f), LitPart(1, 10f, 30f, 0.25f)),
            litParts(span, syllableAlpha = 0.25f)
        )
        assertEquals(
            listOf(LitPart(0, -open, open, 1f), LitPart(1, -open, 30f, 1f)),
            litParts(span, syllableAlpha = 1f)
        )
    }

    @Test
    fun litParts_instantRightToLeftSyllable_isLitFromTheRightEdge() {
        val span = SyllableSpan(startLine = 0, endLine = 0, fromX = 90f, toX = 60f, rtl = true)

        assertEquals(listOf(LitPart(0, 60f, open, 1f)), litParts(span, syllableAlpha = 1f))
    }

    @Test
    fun activeClusterIndex_picksTheLastStartedWordInsideTheLine() {
        val starts = listOf(10_000L, 10_250L, 10_500L)

        assertEquals(2, resolveActiveClusterIndex(starts, positionMs = 10_900, rangeStartMs = 10_000, rangeEndMs = 11_000))
        assertEquals(0, resolveActiveClusterIndex(starts, positionMs = 10_000, rangeStartMs = 10_000, rangeEndMs = 11_000))
        assertEquals(-1, resolveActiveClusterIndex(starts, positionMs = 11_000, rangeStartMs = 10_000, rangeEndMs = 11_000))
        assertEquals(-1, resolveActiveClusterIndex(starts, positionMs = 9_999, rangeStartMs = 10_000, rangeEndMs = 11_000))
    }

    @Test
    fun activeClusterIndex_beforeTheFirstWordOfACurrentLine_isNone() {
        assertEquals(-1, resolveActiveClusterIndex(listOf(5_400L), positionMs = 3_000, rangeStartMs = 2_000, rangeEndMs = 7_000))
    }

    @Test
    fun wordEmphasis_popsAtTheStartAndSettlesQuickly() {
        assertEquals(1.08f, resolveWordEmphasisScale(1_000, 1_000, 3_000, motionEnabled = true), 0.001f)
        assertEquals(1f, resolveWordEmphasisScale(1_300, 1_000, 3_000, motionEnabled = true), 0f)
        assertEquals(1f, resolveWordEmphasisScale(1_000, 1_000, 3_000, motionEnabled = false), 0f)
    }

    @Test
    fun lineAlignment_putsOddSingersLeftEvenSingersRightAndGroupsInTheMiddle() {
        assertEquals("left", resolveLineAlignment(voice = 1, userAlignment = "center", duetLayout = true))
        assertEquals("right", resolveLineAlignment(voice = 2, userAlignment = "center", duetLayout = true))
        assertEquals("left", resolveLineAlignment(voice = 3, userAlignment = "center", duetLayout = true))
        assertEquals("center", resolveLineAlignment(voice = 1_000, userAlignment = "left", duetLayout = true))
    }

    @Test
    fun lineAlignment_keepsTheUserChoiceForUnmarkedLinesOrWhenDuetLayoutIsOff() {
        assertEquals("center", resolveLineAlignment(voice = null, userAlignment = "center", duetLayout = true))
        assertEquals("right", resolveLineAlignment(voice = 1, userAlignment = "right", duetLayout = false))
    }

    @Test
    fun hasMultipleVoices_needsTwoDifferentSingers() {
        assertFalse(hasMultipleVoices(listOf(SyncedLine(0, "a", voice = 1), SyncedLine(1, "b", voice = 1))))
        assertFalse(hasMultipleVoices(listOf(SyncedLine(0, "a"), SyncedLine(1, "b", voice = 2))))
        assertTrue(hasMultipleVoices(listOf(SyncedLine(0, "a", voice = 1), SyncedLine(1, "b"), SyncedLine(2, "c", voice = 2))))
    }
}
