package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.data.model.BackgroundVocals
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsSheetLogicTest {

    @Test
    fun lyricsSheetCornerRadius_staysSquareAtRest() {
        assertNull(lyricsSheetCornerRadius(0f))
    }

    @Test
    fun lyricsSheetCornerRadius_staysSquareWhenEnterSpringOvershootsBelowZero() {
        assertNull(lyricsSheetCornerRadius(-0.05f))
    }

    @Test
    fun lyricsSheetCornerRadius_growsWithPredictiveBackProgress() {
        assertEquals(16.dp, lyricsSheetCornerRadius(0.5f))
        assertEquals(32.dp, lyricsSheetCornerRadius(1f))
    }

    @Test
    fun sanitizeSyncedWords_removesLeadingTags_preventsOverlap() {
        val words = listOf(
            SyncedWord(time = 0, word = "v1:"),
            SyncedWord(time = 120, word = "Hello"),
            SyncedWord(time = 240, word = "world")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(listOf("Hello", "world"), sanitized.map { it.word })
        assertEquals(listOf(120, 240), sanitized.map { it.time })
        assertTrue(sanitized.all { it.startsNewWord })
    }

    @Test
    fun highlightSnapOffsetPx_alignsLineWithHighlightZone() {
        val viewportHeight = 960
        val itemSize = 120
        val highlightOffsetPx = 80f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)

        val expectedCenter = viewportHeight / 2f - highlightOffsetPx
        assertEquals(expectedCenter - itemSize / 2f, offset.toFloat(), 0.5f)
    }

    @Test
    fun highlightSnapOffsetPx_clampsWithinViewportForEndOfList() {
        val viewportHeight = 420
        val itemSize = 320
        val highlightOffsetPx = -160f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)
        val center = offset + itemSize / 2f

        assertTrue(center <= viewportHeight.toFloat())
        assertTrue(center >= itemSize / 2f)
    }

    @Test
    fun highlightSnapOffsetPx_handlesOversizedItems() {
        val viewportHeight = 200
        val itemSize = 260

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx = 60f)

        assertEquals(0, offset)
    }

    @Test
    fun calculateHighlightMetrics_reservesBottomSpace() {
        val metrics = calculateHighlightMetrics(
            containerHeight = 480.dp,
            highlightZoneFraction = 0.22f,
            highlightOffset = 48.dp
        )

        assertTrue(metrics.bottomPadding > 0.dp)
        assertTrue(metrics.topPadding >= 0.dp)
        assertTrue(metrics.zoneHeight > 0.dp)
    }

    @Test
    fun sanitizeLyricLineText_stripsLrcTimestampTags() {
        val raw = "[00:26.42][01:12.34] Three in the morning, I ain't slept all weekend"

        val sanitized = sanitizeLyricLineText(raw)

        assertEquals("Three in the morning, I ain't slept all weekend", sanitized)
    }

    @Test
    fun normalizeWordEndTime_guaranteesForwardRangeWhenTimestampsMatch() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 2_000L,
            nextWordTimeMs = 2_000L,
            lineEndTimeMs = 2_800L
        )

        assertEquals(2_001L, endTime)
    }

    @Test
    fun normalizeWordEndTime_clampsToLineEnd() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 5_000L,
            nextWordTimeMs = 8_000L,
            lineEndTimeMs = 5_400L
        )

        assertEquals(5_400L, endTime)
    }

    @Test
    fun resolveLineEndTimeMs_extendsPastNextLineWhenLastWordStartsLater() {
        val line = SyncedLine(
            time = 1_000,
            line = "abc",
            words = listOf(
                SyncedWord(1_000, "a"),
                SyncedWord(1_600, "b"),
                SyncedWord(2_000, "c")
            )
        )

        val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs = 2_000)

        assertEquals(2_001L, lineEnd)
    }

    @Test
    fun resolveLineEndTimeMs_keepsLineCurrentWhileItsBackgroundVocalsAreSung() {
        val line = SyncedLine(
            time = 2_000,
            line = "in my bones",
            words = listOf(SyncedWord(2_000, "in"), SyncedWord(4_600, "bones")),
            background = BackgroundVocals(
                text = "(bones)",
                words = listOf(SyncedWord(time = 6_500, word = "(bones)", endTime = 7_400))
            )
        )

        assertEquals(7_400L, resolveLineEndTimeMs(line, nextLineStartMs = 7_000))
    }

    @Test
    fun resolveLineEndTimeMs_endsAtTheNextLineWhenTheLastSyllableEndsEarlier() {
        val line = SyncedLine(
            time = 2_000,
            line = "bones",
            words = listOf(SyncedWord(time = 2_000, word = "bones", endTime = 2_800))
        )

        assertEquals(7_000L, resolveLineEndTimeMs(line, nextLineStartMs = 7_000))
    }

    @Test
    fun resolveLineEndTimesMs_sameTimestampDuetLinesEndTogetherAtTheNextLaterLine() {
        val lines = listOf(
            SyncedLine(time = 18_000, line = "Together", voice = 1),
            SyncedLine(time = 18_000, line = "Together", voice = 2),
            SyncedLine(time = 20_000, line = "Next")
        )

        val endTimes = resolveLineEndTimesMs(lines)

        assertEquals(listOf(20_000L, 20_000L, Int.MAX_VALUE.toLong()), endTimes.toList())
    }

    @Test
    fun resolveLineEndTimesMs_firstSingerOfSameTimestampWordDuetSweepsItsLastWord() {
        val words = listOf(SyncedWord(18_000, "Both"), SyncedWord(18_500, "at"), SyncedWord(19_000, "once"))
        val lines = listOf(
            SyncedLine(time = 18_000, line = "Both at once", words = words, voice = 1),
            SyncedLine(time = 18_000, line = "Both at once", words = words, voice = 2),
            SyncedLine(time = 22_000, line = "Next")
        )

        assertEquals(22_000L, resolveLineEndTimesMs(lines).first())
    }

    @Test
    fun resolveLineEndTimesMs_hiddenBackgroundVocalsDoNotKeepTheLineLit() {
        val lines = listOf(
            SyncedLine(
                time = 2_000,
                line = "in my bones",
                words = listOf(SyncedWord(2_000, "in"), SyncedWord(4_600, "bones")),
                background = BackgroundVocals(
                    text = "(bones)",
                    words = listOf(SyncedWord(time = 6_500, word = "(bones)", endTime = 7_400))
                )
            ),
            SyncedLine(time = 7_000, line = "Next")
        )

        assertEquals(7_400L, resolveLineEndTimesMs(lines, includeBackground = true).first())
        assertEquals(7_000L, resolveLineEndTimesMs(lines, includeBackground = false).first())
    }

    @Test
    fun resolveLitLines_lightsBothSingersOfASameTimestampDuet() {
        val lines = listOf(
            SyncedLine(time = 10_000, line = "Before"),
            SyncedLine(time = 18_000, line = "Together", voice = 1),
            SyncedLine(time = 18_000, line = "Together", voice = 2),
            SyncedLine(time = 20_000, line = "Next")
        )
        val endTimes = resolveLineEndTimesMs(lines)

        val lit = resolveLitLines(lines, endTimes, position = 19_000L)

        assertEquals(LitLyricLines(first = 1, last = 2), lit)
        assertEquals(2, resolveCurrentLineIndex(lines, position = 19_000L))
        assertEquals(listOf(1, 0, 0, 1), lines.indices.map(lit::distanceTo))
    }

    @Test
    fun resolveLitLines_keepsAnOverlappingLineLitWithTheNextOne() {
        val lines = listOf(
            SyncedLine(
                time = 1_000,
                line = "Hold on",
                words = listOf(SyncedWord(1_000, "Hold"), SyncedWord(1_500, "on", endTime = 3_500))
            ),
            SyncedLine(time = 3_000, line = "Answer", voice = 2)
        )

        val lit = resolveLitLines(lines, resolveLineEndTimesMs(lines), position = 3_200L)

        assertEquals(LitLyricLines(first = 0, last = 1), lit)
    }

    @Test
    fun resolveLitLines_reportsNothingLitBeforeTheFirstLine() {
        val lines = listOf(SyncedLine(time = 5_000, line = "First"))

        val lit = resolveLitLines(lines, resolveLineEndTimesMs(lines), position = 1_000L)

        assertEquals(LitLyricLines.None, lit)
        assertEquals(-1, resolveCurrentLineIndex(lines, position = 1_000L))
        assertTrue(lit.distanceTo(0) > 2)
    }

    @Test
    fun sanitizeSyncedWords_promotesFirstVisibleWordAfterLeadingMarker() {
        val words = listOf(
            SyncedWord(time = 1000, word = "v1:"),
            SyncedWord(time = 1200, word = "fall", startsNewWord = false)
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(1, sanitized.size)
        assertEquals("fall", sanitized[0].word)
        assertTrue(sanitized[0].startsNewWord)
    }

    @Test
    fun clusterSyncedWords_keepsSyllablesInsideSameWord() {
        val clusters = clusterSyncedWords(
            listOf(
                SyncedWord(time = 1000, word = "to", startsNewWord = true),
                SyncedWord(time = 1100, word = "geth", startsNewWord = false),
                SyncedWord(time = 1200, word = "er", startsNewWord = false),
                SyncedWord(time = 1500, word = "now", startsNewWord = true)
            )
        )

        assertEquals(2, clusters.size)
        assertEquals(listOf("to", "geth", "er"), clusters[0].words.map { it.word })
        assertEquals(listOf("now"), clusters[1].words.map { it.word })
        assertEquals(0, clusters[0].startIndex)
        assertEquals(3, clusters[1].startIndex)
    }

    @Test
    fun resolveSeekPositionMs_subtractsPositiveLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(11_250L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_addsNegativeLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = -750
        )

        assertEquals(12_750L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_clampsToZeroWhenOffsetWouldGoNegative() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 300L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(0L, seekPosition)
    }
}
