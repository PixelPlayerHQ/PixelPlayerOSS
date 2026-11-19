package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricSearchTextTest {
    @Test
    fun `matching ignores case punctuation and canonical Unicode spelling`() {
        val lyrics = "DON’T stop—cafe\u0301, oh! Here+I_go."

        assertTrue(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("dont stop CAFÉ oh")))
        assertTrue(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("here i go")))
        assertFalse(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("stop don't")))
        assertFalse(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery(",.-+_\"':*")))
    }

    @Test
    fun `LRC timings metadata and voice prefixes are not lyrics`() {
        val lyrics = """
            [ar:Not a lyric]
            [00:09.20]v2:<00:09.20>When we <00:09.50>meet,
            [00:11.04][00:19.00]remember me
        """.trimIndent()

        assertTrue(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("when we meet remember me")))
        assertFalse(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("not a lyric")))
        assertFalse(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("09 20")))
    }

    @Test
    fun `TTML matches its sung text rather than XML attributes`() {
        val lyrics = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
                <p begin="00:00:01.000"><span begin="00:00:01.000">Stay</span> here</p>
            </div></body></tt>
        """.trimIndent()

        assertTrue(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("stay here")))
        assertFalse(lyricsContainPhrase(lyrics, normalizeLyricSearchQuery("xmlns")))
    }
}
