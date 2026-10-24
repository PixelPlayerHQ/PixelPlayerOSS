package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.data.model.BackgroundVocals
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer

class LyricsClipboardTextTest {

    @Test
    fun fullLyrics_syncedLyrics_copiesTextWithoutTimestamps() {
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]Hello there\n[00:04.50]General Kenobi")

        assertEquals("Hello there\nGeneral Kenobi", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_blankSyncedLines_becomeSingleStanzaBreaks() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:00.50]
            [00:01.00]First line of verse one
            [00:04.00]Second line of verse one
            [00:07.00]
            [00:08.00]
            [00:10.00]Chorus line one
            [00:13.00]Chorus line two
            [00:16.00]
            """.trimIndent()
        )

        assertEquals(
            "First line of verse one\nSecond line of verse one\n\nChorus line one\nChorus line two",
            LyricsClipboardText.fullLyrics(lyrics)
        )
    }

    @Test
    fun fullLyrics_syncedLine_leavesOutTranslationAndRomanization() {
        val lyrics = Lyrics(
            synced = listOf(
                SyncedLine(
                    time = 1_000,
                    line = "こんにちは",
                    translation = "Hello",
                    romanization = "konnichiwa"
                )
            ),
            plain = listOf("こんにちは\nkonnichiwa\nHello")
        )

        assertEquals("こんにちは", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_sameTimestampTranslationLines_areNotCopied() {
        val lyrics = LyricsUtils.parseLyrics(
            "[00:01.00]Je t'aime\n[00:01.00]I love you\n[00:03.00]Moi non plus\n[00:03.00]Me neither"
        )

        assertEquals("Je t'aime\nMoi non plus", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_enhancedLrc_putsBackgroundVocalsOnTheirOwnRowAfterTheirLine() {
        val lyrics = LyricsUtils.parseLyrics(BONES_LRC)

        assertEquals(
            """
            There's a whisper in my bones
            (bones, bones)
            Keeping me restless and whole
            Crawling through the vastness of space
            Tangle me, tangle me in your web
            (in your web)
            If there's a will, there's a way
            Take me away
            Oh, oh, woah-oh-oh-oh
            """.trimIndent(),
            LyricsClipboardText.fullLyrics(lyrics, includeBackground = true)
        )
    }

    @Test
    fun fullLyrics_hiddenBackgroundVocals_copiesOnlyTheSungLines() {
        val lyrics = LyricsUtils.parseLyrics(BONES_LRC)

        assertEquals(
            """
            There's a whisper in my bones
            Keeping me restless and whole
            Crawling through the vastness of space
            Tangle me, tangle me in your web
            If there's a will, there's a way
            Take me away
            Oh, oh, woah-oh-oh-oh
            """.trimIndent(),
            LyricsClipboardText.fullLyrics(lyrics, includeBackground = false)
        )
    }

    @Test
    fun syncedLine_lineWithBackgroundVocals_copiesBothRows() {
        val line = requireNotNull(LyricsUtils.parseLyrics(BONES_LRC).synced).first()

        assertEquals(
            "There's a whisper in my bones\n(bones, bones)",
            LyricsClipboardText.syncedLine(line, includeBackground = true)
        )
        assertEquals(
            "There's a whisper in my bones",
            LyricsClipboardText.syncedLine(line, includeBackground = false)
        )
    }

    @Test
    fun syncedLine_backgroundSyllables_joinIntoWholeWords() {
        val line = requireNotNull(
            LyricsUtils.parseLyrics(
                """
                [00:10.00]v2: <00:10.00>Whis<00:10.40>per <00:10.90>now<00:11.50>
                [bg: <00:11.50>(whis<00:11.90>per <00:12.30>back)<00:13.00>]
                """.trimIndent()
            ).synced
        ).single()

        assertEquals("Whisper now\n(whisper back)", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun syncedLine_untimedBackgroundVocals_dropSingerPrefixAndTimestamps() {
        val line = SyncedLine(
            time = 0,
            line = "v1: Main line",
            voice = 1,
            background = BackgroundVocals(text = "v2: [00:01.00](oh yeah)")
        )

        assertEquals("Main line\n(oh yeah)", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun backingOnlyLine_isCopiedWhenShownAndIsAStanzaBreakWhenHidden() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:01.00]Lead line
            [00:05.00]
            [bg: <00:05.00>(Ooh <00:05.50>ooh)<00:06.20>]
            [00:07.00]Next line
            """.trimIndent()
        )
        val backingLine = requireNotNull(lyrics.synced)[1]

        assertEquals("(Ooh ooh)", LyricsClipboardText.syncedLine(backingLine, includeBackground = true))
        assertEquals("", LyricsClipboardText.syncedLine(backingLine, includeBackground = false))
        assertEquals(
            "Lead line\n(Ooh ooh)\nNext line",
            LyricsClipboardText.fullLyrics(lyrics, includeBackground = true)
        )
        assertEquals(
            "Lead line\n\nNext line",
            LyricsClipboardText.fullLyrics(lyrics, includeBackground = false)
        )
    }

    @Test
    fun fullLyrics_openingBackingVocals_areLeftOutWhenHidden() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [bg:<00:01.00>(Ooh <00:01.50>ooh)<00:02.20>]
            [00:03.00]Lead line
            """.trimIndent()
        )

        assertEquals("(Ooh ooh)\nLead line", LyricsClipboardText.fullLyrics(lyrics, includeBackground = true))
        assertEquals("Lead line", LyricsClipboardText.fullLyrics(lyrics, includeBackground = false))
    }

    @Test
    fun fullLyrics_ttmlBackgroundSpans_followTheirParagraph() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="2.000" end="6.000" ttm:agent="v2"><span begin="2.000" end="2.400">Whis</span><span begin="2.400" end="3.000">per</span> <span begin="3.000" end="3.500">now</span><span ttm:role="x-bg"><span begin="4.000" end="4.500">(whis</span><span begin="4.500" end="5.000">per)</span></span></p>
                <p begin="7.000" ttm:agent="v1">Lead line</p>
              </div></body>
            </tt>
            """.trimIndent()
        )

        assertEquals("Whisper now\n(whisper)\nLead line", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_ttmlBackingOnlyParagraph_leavesItsTranslationOut() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="1.000" ttm:agent="v1">Lead line</p>
                <p begin="4.000" ttm:agent="v2"><span ttm:role="x-bg"><span begin="4.000" end="4.500">(Ooh</span> <span begin="4.500" end="5.000">ooh)</span></span><span ttm:role="x-translation" xml:lang="es">(Uh uh)</span></p>
              </div></body>
            </tt>
            """.trimIndent()
        )

        assertEquals("Lead line\n(Ooh ooh)", LyricsClipboardText.fullLyrics(lyrics, includeBackground = true))
        assertEquals("Lead line", LyricsClipboardText.fullLyrics(lyrics, includeBackground = false))
    }

    @Test
    fun fullLyrics_lineSungAtTheTimeOfABackingOnlyLine_isStillCopiedAfterSavingAsLrc() {
        val parsed = LyricsUtils.parseLyrics(
            """
            [00:10.00]
            [bg:<00:10.00>(ooh)]
            [00:10.00]<00:10.00>Sung <00:10.50>line
            """.trimIndent()
        )
        val saved = LyricsUtils.parseLyrics(LyricsUtils.toLrcString(parsed))

        assertEquals("(ooh)\nSung line", LyricsClipboardText.fullLyrics(saved, includeBackground = true))
        assertEquals("Sung line", LyricsClipboardText.fullLyrics(saved, includeBackground = false))
    }

    @Test
    fun syncedLine_wordTimedLine_matchesTheWordsTheScreenDraws() {
        // Kugou producers can omit spaces between timed Latin tokens. The parser keeps the raw
        // text in `line` but marks the word boundary, and the lyrics screen draws "Hello world".
        val lyrics = LyricsUtils.parseLyrics("[1000,2000]<0,500,0>Hello<500,500,0>world")
        val line = requireNotNull(lyrics.synced).single()

        assertEquals("Hello world", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun syncedLine_splitSyllables_joinWithoutSpaces() {
        val line = SyncedLine(
            time = 0,
            line = "v1: whisper",
            words = listOf(
                SyncedWord(time = 0, word = "v1:"),
                SyncedWord(time = 0, word = "whis"),
                SyncedWord(time = 400, word = "per", startsNewWord = false)
            )
        )

        assertEquals("whisper", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun syncedLine_mergedContinuationRows_keepsEveryRow() {
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]First row\nSecond row\n[00:05.00]Third row")
        val synced = requireNotNull(lyrics.synced)

        assertEquals("First row\nSecond row", LyricsClipboardText.syncedLine(synced.first()))
        assertEquals("First row\nSecond row\nThird row", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_prefersSyncedLyricsOverPlainOnes() {
        val lyrics = Lyrics(
            synced = listOf(SyncedLine(time = 0, line = "Synced text")),
            plain = listOf("Plain text")
        )

        assertEquals("Synced text", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_plainLyrics_copiesFirstRowOfEachEntry() {
        val lyrics = Lyrics(plain = listOf("東京\ntoukyou\nTokyo", "Second line"))

        assertEquals("東京\nSecond line", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_plainLyricsFromText_copiesEveryLineInOrder() {
        val lyrics = LyricsUtils.parseLyrics("Verse one\nStill verse one\nVerse two")

        assertEquals("Verse one\nStill verse one\nVerse two", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_blankPlainEntries_becomeSingleStanzaBreaks() {
        val lyrics = Lyrics(plain = listOf("", "Verse one", "", "", "Verse two", ""))

        assertEquals("Verse one\n\nVerse two", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun plainLine_stripsStrayTimestampsVoiceTagsAndPadding() {
        assertEquals("Hello", LyricsClipboardText.plainLine("v1: Hello [00:12.00]  "))
        assertEquals("東京", LyricsClipboardText.plainLine("東京\ntoukyou"))
    }

    @Test
    fun fullLyrics_keepsDecomposedVietnameseMarks() {
        val decomposed = Normalizer.normalize("Người ơi người ở đừng về", Normalizer.Form.NFD)
        val lyrics = Lyrics(synced = listOf(SyncedLine(time = 1000, line = decomposed)))

        assertEquals(decomposed, LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_parsedDecomposedVietnamese_copiesComposedMarks() {
        val composed = "Người ơi người ở đừng về"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]$decomposed")

        assertEquals(composed, LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_missingOrEmptyLyrics_returnsEmptyText() {
        assertEquals("", LyricsClipboardText.fullLyrics(null))
        assertEquals("", LyricsClipboardText.fullLyrics(Lyrics()))
        assertEquals("", LyricsClipboardText.fullLyrics(LyricsUtils.parseLyrics("")))
    }

    @Test
    fun hasCopyableText_isFalseWithoutAnyLyricText() {
        assertFalse(LyricsClipboardText.hasCopyableText(null))
        assertFalse(LyricsClipboardText.hasCopyableText(Lyrics(plain = emptyList(), synced = emptyList())))
        assertFalse(
            LyricsClipboardText.hasCopyableText(
                Lyrics(synced = listOf(SyncedLine(time = 0, line = ""), SyncedLine(time = 1_000, line = " ")))
            )
        )
    }

    @Test
    fun hasCopyableText_isTrueForPlainOrSyncedText() {
        assertTrue(LyricsClipboardText.hasCopyableText(Lyrics(plain = listOf("Only plain"))))
        assertTrue(LyricsClipboardText.hasCopyableText(LyricsUtils.parseLyrics("[00:01.00]Synced")))
    }

    @Test
    fun hasCopyableText_backingVocalsOnly_dependsOnWhetherTheyAreShown() {
        val lyrics = Lyrics(
            synced = listOf(SyncedLine(time = 0, line = "", background = BackgroundVocals(text = "(ooh)")))
        )

        assertTrue(LyricsClipboardText.hasCopyableText(lyrics, includeBackground = true))
        assertFalse(LyricsClipboardText.hasCopyableText(lyrics, includeBackground = false))
    }

    private companion object {
        /** The duet from the QA test library: two singers, word timings and two `[bg:]` lines. */
        val BONES_LRC = """
            [ti:Bones]
            [ar:Neon Duo]
            [00:02.00]v1: <00:02.00>There's <00:02.60>a <00:03.00>whis<00:03.40>per <00:03.90>in <00:04.20>my <00:04.60>bones<00:05.40>
            [bg: <00:05.40>(bones, <00:06.00>bones)<00:06.80>]
            [00:07.00]v2: <00:07.00>Kee<00:07.40>ping <00:07.90>me <00:08.30>rest<00:08.80>less <00:09.30>and <00:09.70>whole<00:10.80>
            [00:11.00]v1: <00:11.00>Craw<00:11.40>ling <00:11.90>through <00:12.40>the <00:12.70>vast<00:13.20>ness <00:13.70>of <00:14.00>space<00:15.00>
            [00:15.50]v2: <00:15.50>Tan<00:15.90>gle <00:16.30>me, <00:16.80>tan<00:17.20>gle <00:17.60>me <00:18.00>in <00:18.30>your <00:18.70>web<00:19.80>
            [bg: <00:19.80>(in <00:20.30>your <00:20.80>web)<00:21.60>]
            [00:22.00]v1: <00:22.00>If <00:22.40>there's <00:22.90>a <00:23.20>will, <00:23.70>there's <00:24.20>a <00:24.50>way<00:25.50>
            [00:26.00]v2: <00:26.00>Take <00:26.50>me <00:26.90>a<00:27.20>way<00:28.00>
            [00:29.00]<00:29.00>Oh, <00:29.50>oh, <00:30.00>woah<00:30.60>-oh<00:31.10>-oh<00:31.60>-oh<00:32.40>
        """.trimIndent()
    }
}
