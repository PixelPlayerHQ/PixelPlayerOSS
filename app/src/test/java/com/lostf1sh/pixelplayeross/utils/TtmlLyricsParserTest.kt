package com.lostf1sh.pixelplayeross.utils

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtmlLyricsParserTest {

    private fun ttml(head: String = "", body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <head><metadata>$head</metadata></head>
          <body><div>$body</div></body>
        </tt>
    """.trimIndent()

    @Test
    fun numberedAgents_becomeVoicePrefixes() {
        val lrc = TtmlLyricsParser.parseToEnhancedLrc(
            ttml(
                body = """
                    <p begin="1.000" ttm:agent="v1">First singer</p>
                    <p begin="3.000" ttm:agent="v2">Second singer</p>
                """.trimIndent()
            )
        )

        assertEquals("[00:01.00]v1:First singer\n[00:03.00]v2:Second singer", lrc)
    }

    @Test
    fun namedAgents_areNumberedByFirstAppearanceAndGroupsSingInTheMiddle() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    head = """
                        <ttm:agent type="person" xml:id="lead"/>
                        <ttm:agent type="person" xml:id="guest"/>
                        <ttm:agent type="group" xml:id="everyone"/>
                    """.trimIndent(),
                    body = """
                        <p begin="1.000" ttm:agent="guest">Guest</p>
                        <p begin="2.000" ttm:agent="lead">Lead</p>
                        <p begin="3.000" ttm:agent="guest">Guest again</p>
                        <p begin="4.000" ttm:agent="everyone">All</p>
                    """.trimIndent()
                )
            ).synced
        )

        assertEquals(listOf(1, 2, 1, 1_000), synced.map { it.voice })
    }

    @Test
    fun namedAgents_skipTheNumbersOfNumberedAgentsEvenWhenTheyAppearFirst() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    head = """
                        <ttm:agent type="person" xml:id="v1"/>
                        <ttm:agent type="person" xml:id="guest"/>
                        <ttm:agent type="person" xml:id="v3"/>
                        <ttm:agent type="person" xml:id="choir"/>
                    """.trimIndent(),
                    body = """
                        <p begin="1.000" ttm:agent="guest">Guest first</p>
                        <p begin="2.000" ttm:agent="choir">Choir</p>
                        <p begin="3.000" ttm:agent="v1">Lead</p>
                        <p begin="4.000" ttm:agent="v3">Third</p>
                    """.trimIndent()
                )
            ).synced
        )

        assertEquals(listOf(2, 4, 1, 3), synced.map { it.voice })
    }

    @Test
    fun namedAndNumberedAgentsSingingTogether_stayTwoSungLines() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    head = """
                        <ttm:agent type="person" xml:id="v1"/>
                        <ttm:agent type="person" xml:id="guest"/>
                    """.trimIndent(),
                    body = """
                        <p begin="1.000" ttm:agent="v1">Lead part</p>
                        <p begin="1.000" ttm:agent="guest">Guest part</p>
                    """.trimIndent()
                )
            ).synced
        )

        assertEquals(listOf("Lead part", "Guest part"), synced.map { it.line })
        assertEquals(listOf(1, 2), synced.map { it.voice })
        assertTrue(synced.all { it.translation == null })
    }

    @Test
    fun backgroundSpans_becomeBackgroundVocalsInsteadOfJoiningTheLine() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """
                        <p begin="2.000" end="6.000" ttm:agent="v2"><span begin="2.000" end="2.400">Whis</span><span begin="2.400" end="3.000">per</span> <span begin="3.000" end="3.500">now</span><span ttm:role="x-bg"><span begin="4.000" end="4.500">(whis</span><span begin="4.500" end="5.000">per)</span></span></p>
                    """.trimIndent()
                )
            ).synced
        )

        val line = synced.single()
        assertEquals(2, line.voice)
        assertEquals("Whisper now", line.line)
        assertEquals(listOf(true, false, true), line.words!!.map { it.startsNewWord })
        val background = requireNotNull(line.background)
        assertEquals("(whisper)", background.text)
        assertEquals(listOf("(whis", "per)"), background.words!!.map { it.word })
        assertEquals(listOf(true, false), background.words!!.map { it.startsNewWord })
    }

    @Test
    fun paragraphSungOnlyInTheBackground_isKeptAsAnEmptyLineWithBackgroundVocals() {
        val lyrics = LyricsUtils.parseLyrics(
            ttml(
                body = """
                    <p begin="1.000" ttm:agent="v1">Lead line</p>
                    <p begin="10.000" ttm:agent="v2"><span ttm:role="x-bg"><span begin="10.000" end="10.500">(Ooh</span> <span begin="10.500" end="11.200">ooh)</span></span></p>
                """.trimIndent()
            )
        )

        val synced = requireNotNull(lyrics.synced)
        assertEquals(listOf("Lead line", ""), synced.map { it.line })
        val backingLine = synced[1]
        assertEquals(10_000, backingLine.time)
        assertEquals(2, backingLine.voice)
        val background = requireNotNull(backingLine.background)
        assertEquals("(Ooh ooh)", background.text)
        assertEquals(listOf("(Ooh", "ooh)"), background.words!!.map { it.word })
        assertEquals(11_200, background.words!!.last().endTime)
        assertEquals("(Ooh ooh)", lyrics.plain!![1])
    }

    @Test
    fun paragraphSungOnlyInTheBackground_keepsItsTranslationOnTheBackingLine() {
        val ttmlText = ttml(
            body = """
                <p begin="1.000" ttm:agent="v1">Lead line</p>
                <p begin="10.000" ttm:agent="v2"><span ttm:role="x-bg"><span begin="10.000" end="10.500">(Ooh</span> <span begin="10.500" end="11.200">ooh)</span></span><span ttm:role="x-translation" xml:lang="es">(Uh uh)</span></p>
            """.trimIndent()
        )
        val lyrics = LyricsUtils.parseLyrics(ttmlText)

        val synced = requireNotNull(lyrics.synced)
        assertEquals(listOf("Lead line", ""), synced.map { it.line })
        val backingLine = synced[1]
        assertEquals(10_000, backingLine.time)
        assertEquals(2, backingLine.voice)
        assertEquals("(Uh uh)", backingLine.translation)
        assertEquals("(Ooh ooh)", backingLine.background!!.text)
        assertEquals("(Ooh ooh)\n(Uh uh)", lyrics.plain!![1])
        assertTrue(requireNotNull(TtmlLyricsParser.parseToEnhancedLrc(ttmlText)).lines().contains("[tr:(Uh uh)]"))
        assertEquals(synced, LyricsUtils.parseLyrics(LyricsUtils.toEnhancedLrc(synced)).synced)
        val untimed = requireNotNull(LyricsUtils.parseLyrics(LyricsUtils.syncedToLrcString(synced)).synced)
        assertEquals(listOf("Lead line", ""), untimed.map { it.line })
        assertEquals(listOf(null, "(Uh uh)"), untimed.map { it.translation })
        assertEquals("(Ooh ooh)", untimed[1].background!!.text)
    }

    @Test
    fun paragraphSungAtTheTimeOfABackingOnlyParagraph_staysASungLineThroughEverySavePath() {
        val lyrics = LyricsUtils.parseLyrics(
            ttml(
                body = """
                    <p begin="10.000" ttm:agent="v1"><span ttm:role="x-bg"><span begin="10.000" end="10.500">(Ooh)</span></span><span ttm:role="x-translation" xml:lang="es">(Uh)</span></p>
                    <p begin="10.000" ttm:agent="v1"><span begin="10.000" end="10.400">Sung</span> <span begin="10.400" end="11.000">line</span><span ttm:role="x-translation" xml:lang="es">Línea cantada</span></p>
                    <p begin="12.000" ttm:agent="v1">Line timed only</p>
                    <p begin="12.000" ttm:agent="v1"><span ttm:role="x-bg">(Aah)</span></p>
                """.trimIndent()
            )
        )

        val synced = requireNotNull(lyrics.synced)
        assertEquals(listOf("", "Sung line", "Line timed only", ""), synced.map { it.line })
        assertEquals(listOf("(Uh)", "Línea cantada", null, null), synced.map { it.translation })
        assertEquals(listOf("(Ooh)", null, null, "(Aah)"), synced.map { it.background?.text })
        val savedForms = listOf(
            LyricsUtils.toEnhancedLrc(synced),
            LyricsUtils.toEnhancedLrc(synced, includeWordTimings = false),
            LyricsUtils.toLrcString(lyrics)
        )
        assertEquals(synced, LyricsUtils.parseLyrics(savedForms.first()).synced)
        savedForms.forEach { saved ->
            val readBack = requireNotNull(LyricsUtils.parseLyrics(saved).synced)
            assertEquals(synced.map { it.line }, readBack.map { it.line }, saved)
            assertEquals(synced.map { it.translation }, readBack.map { it.translation }, saved)
            assertEquals(synced.map { it.background?.text }, readBack.map { it.background?.text }, saved)
        }
    }

    @Test
    fun spanEndTimes_closeEachSyllable() {
        val words = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """<p begin="1.000"><span begin="1.000" end="1.400">Hold</span> <span begin="2.000" end="3.500">on</span></p>"""
                )
            ).synced
        ).single().words!!

        assertEquals(listOf(1_400, 3_500), words.map { it.endTime })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun inlineTranslationAndRomanizationSpans_doNotJoinTheSungLine() {
        val line = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """
                        <p begin="1.000" ttm:agent="v1"><span begin="1.000">Hello</span><span ttm:role="x-translation" xml:lang="es">Hola</span><span ttm:role="x-roman">heh-loh</span></p>
                    """.trimIndent()
                )
            ).synced
        ).single()

        assertEquals("Hello", line.line)
        assertEquals("Hola", line.translation)
        assertEquals(1, line.voice)
    }

    @Test
    fun paragraphsWithoutAgents_haveNoVoicePrefix() {
        val lrc = requireNotNull(TtmlLyricsParser.parseToEnhancedLrc(ttml(body = """<p begin="1.000">Solo</p>""")))

        assertFalse(lrc.contains("v1:"))
        assertNull(LyricsUtils.parseLyrics(lrc).synced!!.single().voice)
        assertTrue(lrc.startsWith("[00:01.00]Solo"))
    }

    @Test
    fun parsesWithAnXmlParserThatSupportsNoHardeningFeatures() {
        // Android's DocumentBuilderFactory throws for XInclude, attributes and these features.
        val previous = System.setProperty(FACTORY_PROPERTY, FeaturelessDocumentBuilderFactory::class.java.name)
        val lrc = try {
            assertTrue(DocumentBuilderFactory.newInstance() is FeaturelessDocumentBuilderFactory)
            TtmlLyricsParser.parseToEnhancedLrc(
                ttml(body = """<p begin="1.000" ttm:agent="v2"><span begin="1.000" end="1.500">Still</span> <span begin="1.500">here</span></p>""")
            )
        } finally {
            if (previous == null) System.clearProperty(FACTORY_PROPERTY) else System.setProperty(FACTORY_PROPERTY, previous)
        }

        assertEquals("[00:01.00]v2:<00:01.00>Still <00:01.50>here", lrc)
    }

    @Test
    fun documentTypeDeclarations_areRefusedBeforeParsing() {
        val withInternalEntity = """
            <!DOCTYPE tt [<!ENTITY chorus "la la la">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1.000">&chorus;</p></div></body></tt>
        """.trimIndent()

        assertNull(TtmlLyricsParser.parseToEnhancedLrc(withInternalEntity))
    }

    /** Behaves like Android's factory: only namespace awareness, no hardening settings. */
    class FeaturelessDocumentBuilderFactory : DocumentBuilderFactory() {
        override fun newDocumentBuilder(): DocumentBuilder =
            newInstance(JDK_FACTORY, null)
                .also { it.isNamespaceAware = isNamespaceAware }
                .newDocumentBuilder()

        override fun setAttribute(name: String, value: Any?) = throw IllegalArgumentException(name)

        override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)

        override fun setFeature(name: String, value: Boolean) = throw ParserConfigurationException(name)

        override fun getFeature(name: String): Boolean = throw ParserConfigurationException(name)

        override fun setXIncludeAware(state: Boolean) = throw UnsupportedOperationException()
    }

    private companion object {
        const val FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory"
        const val JDK_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"
    }
}
