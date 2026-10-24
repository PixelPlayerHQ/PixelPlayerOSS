package com.lostf1sh.pixelplayeross.utils

import com.lostf1sh.pixelplayeross.data.model.BackgroundVocals
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnhancedLrcParsingTest {

    private fun parseSynced(lrc: String): List<SyncedLine> =
        requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

    @Test
    fun syllablesWithoutSpaceBetweenTags_formOneVisualWord() {
        // The line from issue #131: "whis" and "per" are one word because no space separates them.
        val line = parseSynced(
            "[00:45.00]<00:45.00>There's <00:45.40>a <00:45.73>whis<00:46.29>per <00:46.80>in"
        ).single()

        assertEquals("There's a whisper in", line.line)
        assertEquals(listOf("There's", "a", "whis", "per", "in"), line.words!!.map { it.word })
        assertEquals(listOf(true, true, true, false, true), line.words!!.map { it.startsNewWord })
    }

    @Test
    fun voicePrefixBeforeWordTags_setsVoiceAndIsStrippedFromText() {
        val line = parseSynced("[00:10.00]v1: <00:10.00>Hello <00:10.50>world").single()

        assertEquals(1, line.voice)
        assertEquals("Hello world", line.line)
        assertEquals(listOf("Hello", "world"), line.words!!.map { it.word })
        assertTrue(line.words!!.first().startsNewWord)
    }

    @Test
    fun voicePrefixAfterFirstWordTag_keepsTheTagTiming() {
        val line = parseSynced("[00:12.00]<00:12.00>v2:Hi <00:12.40>there").single()

        assertEquals(2, line.voice)
        assertEquals("Hi there", line.line)
        assertEquals(listOf(12_000, 12_400), line.words!!.map { it.time })
    }

    @Test
    fun voicePrefixOnPlainSyncedLine_isCaseInsensitive() {
        val line = parseSynced("[00:14.00]V2: Plain words").single()

        assertEquals(2, line.voice)
        assertEquals("Plain words", line.line)
        assertNull(line.words)
    }

    @Test
    fun linesWithoutPrefix_haveNoVoice() {
        assertNull(parseSynced("[00:14.00]Just a line").single().voice)
    }

    @Test
    fun differentVoicesAtSameTimestamp_areSeparateDuetLines() {
        val lines = parseSynced(
            """
            [00:18.00]v1:<00:18.00>Both <00:18.50>at <00:19.00>once
            [00:18.00]v2:<00:18.00>Both <00:18.50>at <00:19.00>once
            """.trimIndent()
        )

        assertEquals(listOf(1, 2), lines.map { it.voice })
        assertTrue(lines.all { it.translation == null })
    }

    @Test
    fun untaggedLineAtSameTimestamp_isStillTheTranslationOfAVoicedLine() {
        val line = parseSynced("[00:10.00]v1: Hello world\n[00:10.00]Hola mundo").single()

        assertEquals(1, line.voice)
        assertEquals("Hola mundo", line.translation)
    }

    @Test
    fun timedBackgroundLine_attachesToPreviousLineInsteadOfBeingDroppedAsMetadata() {
        val lines = parseSynced(
            """
            [ti:Bones]
            [00:02.00]<00:02.00>in <00:04.20>my <00:04.60>bones<00:05.40>
            [bg: <00:05.40>(whis<00:05.90>per) <00:06.30>bones<00:06.80>]
            [00:07.00]Next line
            """.trimIndent()
        )

        assertEquals(listOf("in my bones", "Next line"), lines.map { it.line })
        val background = requireNotNull(lines.first().background)
        assertEquals("(whisper) bones", background.text)
        assertEquals(listOf("(whis", "per)", "bones"), background.words!!.map { it.word })
        assertEquals(listOf(true, false, true), background.words!!.map { it.startsNewWord })
        assertEquals(6_800, background.words!!.last().endTime)
        assertNull(lines[1].background)
    }

    @Test
    fun backgroundLineAfterInstrumentalMarker_staysOnTheEmptyLine() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:58.00]Last words
            [01:00.00]
            [bg:<01:00.50>(ooh)<01:01.40>]
            [01:05.00]Next verse
            """.trimIndent()
        )

        val lines = requireNotNull(lyrics.synced)
        assertEquals(listOf("Last words", "", "Next verse"), lines.map { it.line })
        assertNull(lines[0].background)
        assertEquals("(ooh)", lines[1].background!!.text)
        assertEquals(61_400, lines[1].background!!.words!!.single().endTime)
        assertEquals("(ooh)", lyrics.plain!![1])
    }

    @Test
    fun plainBackgroundLine_attachesAsText() {
        val line = parseSynced("[00:02.00]Main line\n[bg: (ooh, ooh)]").single()

        assertEquals(BackgroundVocals(text = "(ooh, ooh)"), line.background)
    }

    @Test
    fun consecutiveBackgroundLines_areJoined() {
        val line = parseSynced(
            "[00:02.00]Main\n[bg:<00:03.00>first]\n[bg:<00:04.00>second]"
        ).single()

        val background = requireNotNull(line.background)
        assertEquals("first second", background.text)
        assertEquals(listOf(true, true), background.words!!.map { it.startsNewWord })
    }

    @Test
    fun timedBackgroundLineBeforeAnyLine_becomesAnEmptyLineCarryingTheBackingVocals() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [bg:<00:01.00>(oh <00:01.50>yeah)]
            [00:03.00]First line
            """.trimIndent()
        )

        val lines = requireNotNull(lyrics.synced)
        assertEquals(listOf(1_000, 3_000), lines.map { it.time })
        assertEquals(listOf("", "First line"), lines.map { it.line })
        val opening = lines.first()
        assertNull(opening.words)
        val background = requireNotNull(opening.background)
        assertEquals("(oh yeah)", background.text)
        assertEquals(listOf("(oh", "yeah)"), background.words!!.map { it.word })
        assertNull(lines[1].background)
        assertEquals("(oh yeah)", lyrics.plain!!.first())
    }

    @Test
    fun timedBackgroundLineBeforeALineAtTheSameTime_isSungUnderThatLineRatherThanPairedAsItsTranslation() {
        val lines = parseSynced(
            """
            [bg:<00:03.00>(oh)]
            [00:03.00]First line
            """.trimIndent()
        )

        val line = lines.single()
        assertEquals("First line", line.line)
        assertEquals("(oh)", line.background!!.text)
        assertNull(line.translation)
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
        val untimed = parseSynced(LyricsUtils.syncedToLrcString(lines)).single()
        assertEquals("First line", untimed.line)
        assertEquals(BackgroundVocals(text = "(oh)"), untimed.background)
        assertNull(untimed.translation)
    }

    /**
     * The lines read back from each way the app writes lyrics: the word-timed enhanced LRC of the
     * disk cache and database, the untimed copy stored beside it, and Save as .lrc.
     */
    private fun readBackFromEverySavePath(lines: List<SyncedLine>): Map<String, List<SyncedLine>> = mapOf(
        "enhanced LRC" to parseSynced(LyricsUtils.toEnhancedLrc(lines)),
        "untimed LRC" to parseSynced(LyricsUtils.toEnhancedLrc(lines, includeWordTimings = false)),
        "Save as .lrc" to parseSynced(LyricsUtils.toLrcString(Lyrics(synced = lines)))
    )

    @Test
    fun trLineUnderABackingOnlyLine_isItsTranslationThroughEverySavePath() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:10.00]v2:
            [bg:<00:10.00>(Ooh <00:10.50>ooh)<00:11.20>]
            [tr:(Uh uh)]
            [00:12.00]v1:Lead line
            """.trimIndent()
        )

        val lines = requireNotNull(lyrics.synced)
        assertEquals(listOf("", "Lead line"), lines.map { it.line })
        val backingLine = lines.first()
        assertEquals(2, backingLine.voice)
        assertEquals("(Uh uh)", backingLine.translation)
        assertEquals("(Ooh ooh)", backingLine.background!!.text)
        assertEquals("(Ooh ooh)\n(Uh uh)", lyrics.plain!!.first())
        assertTrue(LyricsUtils.syncedToLrcString(lines).lines().contains("[tr:(Uh uh)]"))
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
        readBackFromEverySavePath(lines).forEach { (path, readBack) ->
            assertEquals(listOf("", "Lead line"), readBack.map { it.line }, path)
            assertEquals(listOf("(Uh uh)", null), readBack.map { it.translation }, path)
            assertEquals("(Ooh ooh)", readBack.first().background!!.text, path)
            assertEquals(listOf(2, 1), readBack.map { it.voice }, path)
        }
    }

    @Test
    fun untimedLineAtTheTimeOfABackingOnlyLine_isSungRatherThanTakenAsItsTranslation() {
        val lines = parseSynced(
            """
            [00:10.00]v2:
            [bg:<00:10.00>(Ooh <00:10.50>ooh)<00:11.20>]
            [00:10.00]Sung line
            """.trimIndent()
        )

        assertEquals(listOf("", "Sung line"), lines.map { it.line })
        assertTrue(lines.all { it.translation == null })
        readBackFromEverySavePath(lines).forEach { (path, readBack) ->
            assertEquals(listOf("", "Sung line"), readBack.map { it.line }, path)
            assertTrue(readBack.all { it.translation == null }, path)
        }
    }

    @Test
    fun wordTimedLineAtTheTimeOfABackingOnlyLine_staysASungLineThroughEverySavePath() {
        val lines = parseSynced(
            """
            [00:10.00]
            [bg:<00:10.00>(ooh)]
            [00:10.00]<00:10.00>Sung <00:10.50>line
            """.trimIndent()
        )

        assertEquals(listOf("", "Sung line"), lines.map { it.line })
        assertTrue(lines.all { it.translation == null })
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
        readBackFromEverySavePath(lines).forEach { (path, readBack) ->
            // Save as .lrc drops the word tags; the line must still be sung, not a translation.
            assertEquals(listOf("", "Sung line"), readBack.map { it.line }, path)
            assertTrue(readBack.all { it.translation == null }, path)
            assertEquals("(ooh)", readBack.first().background!!.text, path)
            assertNull(readBack[1].background, path)
        }
    }

    @Test
    fun backingOnlyLineWithATranslation_andASungLineAtTheSameTime_keepBothThroughEverySavePath() {
        val lines = parseSynced(
            """
            [00:10.00]
            [bg:<00:10.00>(ooh)]
            [tr:(uh)]
            [00:10.00]<00:10.00>Sung <00:10.50>line
            [00:10.00]Línea cantada
            """.trimIndent()
        )

        assertEquals(listOf("", "Sung line"), lines.map { it.line })
        assertEquals(listOf("(uh)", "Línea cantada"), lines.map { it.translation })
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
        readBackFromEverySavePath(lines).forEach { (path, readBack) ->
            assertEquals(listOf("", "Sung line"), readBack.map { it.line }, path)
            assertEquals(listOf("(uh)", "Línea cantada"), readBack.map { it.translation }, path)
            assertEquals("(ooh)", readBack.first().background!!.text, path)
        }
    }

    @Test
    fun multilineTranslationOfABackingOnlyLine_isWrittenAsOneTrLinePerRow() {
        val lines = listOf(
            SyncedLine(
                time = 10_000,
                line = "",
                translation = "(uh)\nby: translator",
                background = BackgroundVocals(text = "(ooh)")
            ),
            SyncedLine(time = 10_000, line = "Sung line")
        )

        val lrc = LyricsUtils.syncedToLrcString(lines)

        assertEquals("[00:10.00]\n[bg:(ooh)]\n[tr:(uh)]\n[tr:by: translator]\n[00:10.00]Sung line", lrc)
        assertEquals(lines, parseSynced(lrc))
    }

    @Test
    fun trLinesUnderALineWithText_areSkippedSoTheLinesReadBackTheSameThroughEverySavePath() {
        val lines = parseSynced(
            """
            [00:10.00]Hello
            [tr:Hola]
            [tr:Saludos]
            [00:12.00]Next line
            [00:12.00]Siguiente línea
            [tr:Otra]
            """.trimIndent()
        )

        assertEquals(listOf("Hello", "Next line"), lines.map { it.line })
        assertEquals(listOf(null, "Siguiente línea"), lines.map { it.translation })
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
        readBackFromEverySavePath(lines).forEach { (path, readBack) ->
            assertEquals(listOf("Hello", "Next line"), readBack.map { it.line }, path)
            assertEquals(listOf(null, "Siguiente línea"), readBack.map { it.translation }, path)
        }
    }

    @Test
    fun trLineBeforeAnySyncedLine_isSkippedLikeAHeaderTag() {
        val line = parseSynced("[tr:Someone]\n[00:01.00]Hi").single()

        assertEquals("Hi", line.line)
        assertNull(line.translation)
    }

    @Test
    fun openingBackgroundLineWithATranslation_isNotMovedUnderTheLineAtItsTime() {
        val lines = parseSynced(
            """
            [bg:<00:03.00>(oh)]
            [tr:(uh)]
            [00:03.00]First line
            """.trimIndent()
        )

        assertEquals(listOf("", "First line"), lines.map { it.line })
        assertEquals(listOf("(uh)", null), lines.map { it.translation })
        assertEquals("(oh)", lines.first().background!!.text)
        assertNull(lines[1].background)
        assertEquals(lines, parseSynced(LyricsUtils.toEnhancedLrc(lines)))
    }

    @Test
    fun lineForAnotherSingerAtTheTimeOfABackingOnlyLine_staysASungLine() {
        val lines = parseSynced(
            """
            [00:10.00]v2:
            [bg:<00:10.00>(ooh)]
            [00:10.00]v1:Lead line
            """.trimIndent()
        )

        assertEquals(listOf("", "Lead line"), lines.map { it.line })
        assertEquals(listOf(2, 1), lines.map { it.voice })
        assertTrue(lines.all { it.translation == null })
    }

    @Test
    fun openingBackgroundLines_areJoinedOnTheirEmptyLine() {
        val line = parseSynced("[bg:<00:01.00>(oh)]\n[bg:<00:02.00>(yeah)]").single()

        assertEquals("", line.line)
        assertEquals("(oh) (yeah)", line.background!!.text)
    }

    @Test
    fun openingBackgroundLine_survivesAnEnhancedLrcRoundTrip() {
        val parsed = parseSynced("[bg:<00:01.00>(oh <00:01.50>yeah)<00:02.00>]\n[00:03.00]First line")

        assertEquals(parsed, parseSynced(LyricsUtils.toEnhancedLrc(parsed)))
    }

    @Test
    fun trailingEmptyTag_setsWhenTheLastSyllableEnds() {
        val words = parseSynced("[00:02.00]<00:02.00>in <00:04.60>bones<00:05.40>").single().words!!

        assertEquals(listOf(null, 5_400), words.map { it.endTime })
    }

    @Test
    fun closingTagFollowedBySpace_endsSyllableAndKeepsTheWordBreak() {
        val words = parseSynced("[00:01.00]<00:01.00>word<00:01.40> <00:02.00>next").single().words!!

        assertEquals(listOf("word", "next"), words.map { it.word })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
        assertEquals(1_400, words.first().endTime)
    }

    @Test
    fun bareClosingTagAfterSpace_doesNotJoinTheNextWord() {
        val words = parseSynced("[00:01.00]<00:01.00>word <00:01.40><00:02.00>next").single().words!!

        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun kugouDurations_setSyllableEndTimes() {
        val words = parseSynced("[1000,2000]<0,300,0>Hel<300,500,0>lo").single().words!!

        assertEquals(listOf(1_300, 1_800), words.map { it.endTime })
    }

    @Test
    fun decomposedVietnamese_isNormalizedToComposedForm() {
        val composed = "Người về em vẫn khóc thầm"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        assertFalse(composed == decomposed)

        val line = parseSynced("[00:06.00]$decomposed").single()

        assertEquals(composed, line.line)
    }

    @Test
    fun decomposedVietnameseWordTags_keepToneMarksOnTheirLetters() {
        val decomposed = Normalizer.normalize(
            "[00:11.00]<00:11.00>Tiếng <00:11.60>hát <00:12.20>vang",
            Normalizer.Form.NFD
        )

        val words = parseSynced(decomposed).single().words!!

        assertEquals(listOf("Tiếng", "hát", "vang"), words.map { it.word })
    }

    @Test
    fun kugouTokenStartingWithToneMark_isFoldedIntoThePreviousSyllable() {
        // A producer that splits decomposed text by code unit can cut the tone mark of "ế" off its "ê".
        val words = parseSynced("[2000,1000]<0,500,0>Tiê<500,500,0>́ng").single().words!!

        assertEquals(listOf("Tiếng"), words.map { it.word })
        assertEquals(3_000, words.single().endTime)
    }

    @Test
    fun enhancedLrcRoundTrip_keepsVoicesBackgroundSyllablesTranslationsAndEndTimes() {
        val source = """
            [00:02.00]v1:<00:02.00>There's <00:02.60>a <00:03.00>whis<00:03.40>per<00:03.80>
            [bg:<00:04.00>(whis<00:04.40>per)<00:05.00>]
            [00:02.00]Hay un susurro
            [00:07.00]v2:<00:07.00>Kee<00:07.40>ping <00:07.90>me<00:08.50> <00:09.00>rest<00:09.40>less
            [00:11.00]v1: Plain left line
            [bg: (oh)]
        """.trimIndent()
        val parsed = parseSynced(source)

        val reparsed = parseSynced(LyricsUtils.toEnhancedLrc(parsed))

        assertEquals(parsed, reparsed)
        assertEquals("Hay un susurro", reparsed.first().translation)
        assertEquals(listOf(1, 2, 1), reparsed.map { it.voice })
    }

    @Test
    fun syncedToLrcString_keepsVoicePrefixesAndBackgroundTextWithoutWordTags() {
        val lrc = LyricsUtils.syncedToLrcString(
            listOf(
                SyncedLine(
                    time = 10_000,
                    line = "Hello world",
                    words = listOf(SyncedWord(10_000, "Hello"), SyncedWord(10_500, "world")),
                    voice = 2,
                    background = BackgroundVocals(
                        text = "(hello)",
                        words = listOf(SyncedWord(11_000, "(hello)"))
                    )
                )
            )
        )

        assertEquals("[00:10.00]v2:Hello world\n[bg:(hello)]", lrc)
    }

    @Test
    fun syncedToLrcString_writesAsciiDigitsWhateverTheDefaultLocale() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
        try {
            val lrc = LyricsUtils.syncedToLrcString(listOf(SyncedLine(time = 61_230, line = "Hi")))

            assertEquals("[01:01.23]Hi", lrc)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
