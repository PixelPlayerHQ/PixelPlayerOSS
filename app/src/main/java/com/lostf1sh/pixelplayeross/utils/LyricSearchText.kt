package com.lostf1sh.pixelplayeross.utils

import java.text.Normalizer

private val APOSTROPHE = Regex("['’ʼ]")
private val NON_LYRIC_WORD_CHARACTERS = Regex("""[^\p{L}\p{M}\p{N}]+""")
private val LYRIC_TIMINGS = Regex(
    """\[\d+:\d{2}(?:[.:]\d{1,3})?]|<\d+:\d{2}(?:[.:]\d{1,3})?>|\[\d+,\d+]|<\d+,\d+,\d+>"""
)
private val LYRIC_METADATA_LINE = Regex("""^\[(?!bg:|tr:)[a-z]+:.*]$""", RegexOption.IGNORE_CASE)
private val LYRIC_VOICE_PREFIX = Regex("""^(?:v\d{1,4}\s*:|\[(?:bg|tr):)\s*""", RegexOption.IGNORE_CASE)

internal fun normalizeLyricSearchQuery(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace(APOSTROPHE, "")
        .replace(NON_LYRIC_WORD_CHARACTERS, " ")
        .trim()

internal fun lyricsContainPhrase(raw: String, normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return false
    val clean = stripLeadingLyricsDocumentNoise(raw)
    val lyrics = if (looksLikeTtmlDocument(clean)) {
        TtmlLyricsParser.parseToEnhancedLrc(clean) ?: return false
    } else {
        clean
    }
    val text = lyrics.lineSequence()
        .map { it.trim() }
        .filterNot(LYRIC_METADATA_LINE::matches)
        .joinToString(" ") { line ->
            line.replace(LYRIC_TIMINGS, "").trimStart().replaceFirst(LYRIC_VOICE_PREFIX, "")
        }
    return normalizeLyricSearchQuery(text).contains(normalizedQuery, ignoreCase = true)
}
