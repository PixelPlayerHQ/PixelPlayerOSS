package com.lostf1sh.pixelplayeross.data.network.lyrics

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import org.junit.jupiter.api.Test

class LyricsfileParserTest {

    @Test
    fun `word synced lyricsfile becomes enhanced lrc without losing text`() {
        val lyricsfile = """
            version: '1.0'
            metadata:
              title: 'Test song'
            lines:
              - text: 'Hello: world #1'
                start_ms: 1000
                end_ms: 2400
                words:
                  - text: 'Hel'
                    start_ms: 1000
                    end_ms: 1200
                  - text: 'lo '
                    start_ms: 1200
                    end_ms: 1500
                  - text: 'world #1'
                    start_ms: 1500
                    end_ms: 2400
        """.trimIndent()

        val enhancedLrc = LyricsfileParser.toEnhancedLrc(lyricsfile)
        val parsed = LyricsUtils.parseLyrics(enhancedLrc)

        assertThat(enhancedLrc).isNotNull()
        assertThat(parsed.synced).hasSize(1)
        assertThat(parsed.synced!!.single().line).isEqualTo("Hello: world #1")
        assertThat(parsed.synced!!.single().words!!.map { it.time })
            .containsExactly(1000, 1200, 1500).inOrder()
    }

    @Test
    fun `line synced lyricsfile remains usable when words are absent`() {
        val lyricsfile = """
            version: '1.0'
            lines:
              - text: "It's only a line"
                start_ms: 2500
                end_ms: 4000
        """.trimIndent()

        val enhancedLrc = LyricsfileParser.toEnhancedLrc(lyricsfile)
        val parsed = LyricsUtils.parseLyrics(enhancedLrc)

        assertThat(parsed.synced!!.single().time).isEqualTo(2500)
        assertThat(parsed.synced!!.single().line).isEqualTo("It's only a line")
        assertThat(parsed.synced!!.single().words).isNull()
    }

    @Test
    fun `malformed or empty lyricsfile is ignored`() {
        assertThat(LyricsfileParser.toEnhancedLrc("not: [valid")).isNull()
        assertThat(LyricsfileParser.toEnhancedLrc("version: '1.0'\nlines: []")).isNull()
        assertThat(
            LyricsfileParser.toEnhancedLrc(
                "version: '2.0'\nlines:\n  - text: future\n    start_ms: 0",
            ),
        ).isNull()
    }
}
