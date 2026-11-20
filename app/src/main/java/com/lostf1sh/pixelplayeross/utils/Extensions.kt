package com.lostf1sh.pixelplayeross.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

fun Color.toHexString(): String {
    return String.format(Locale.US, "#%08X", this.toArgb())
}

/**
 * Attempts to fix incorrectly encoded metadata strings that frequently appear when
 * tags are saved using Windows-1252/ISO-8859-1 but are later read as UTF-8. This results
 * in characters such as "Ã", "â" or replacement symbols appearing instead of expected
 * punctuation. The function re-encodes the text when those patterns are detected and
 * removes stray control characters while keeping the original text when no adjustment
 * is necessary.
 */
fun String?.normalizeMetadataText(): String? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    val candidate = repairUtf8Mojibake(trimmed) ?: trimmed

    val cleaned = candidate.replace("\u0000", "")

    return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
}

// Lead characters of UTF-8 sequences once mis-decoded as Windows-1252 ("Ã©" = é, "â€™" = ’).
private val MOJIBAKE_LEAD_CHARS = charArrayOf('Ã', 'Â', 'â', 'Å', 'Æ', 'Ð', 'ð')

/**
 * Returns the repaired text only when [text] is genuinely UTF-8 that was decoded as
 * Windows-1252, or null to keep it as-is.
 *
 * "â" and "Ã" are ordinary letters in French and Portuguese ("Château", "CORAÇÃO"), so the
 * round trip has to succeed *strictly*: every character must exist in Windows-1252 and the
 * resulting bytes must be valid UTF-8, which real text with accents virtually never
 * satisfies ("â" followed by "t" is not a UTF-8 continuation byte).
 */
internal fun repairUtf8Mojibake(text: String): String? {
    if (text.none { it in MOJIBAKE_LEAD_CHARS }) return null
    return runCatching {
        val bytes = WINDOWS_1252.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(bytes)
            .toString()
            .trim()
        decoded.takeIf { it.isNotEmpty() && it != text }
    }.getOrNull()
}

fun String?.normalizeMetadataTextOrEmpty(): String {
    return normalizeMetadataText() ?: ""
}

/**
 * Escape sequence for delimiters in artist names.
 * Use double backslash (\\) before a delimiter to prevent splitting at that position.
 * Example: "AC\\\\DC" with delimiter "/" won't split, but "Artist1/Artist2" will.
 */
private const val ESCAPE_SEQUENCE = "\\\\"

/**
 * Placeholder used internally during parsing to preserve escaped delimiters.
 */
private const val ESCAPE_PLACEHOLDER = "\u0000ESCAPED\u0000"

/**
 * Default word-based delimiters for splitting multi-artist strings.
 * These are matched case-insensitively with word boundaries.
 */
val DEFAULT_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")

/**
 * Splits an artist string by the given character delimiters and word delimiters,
 * respecting escaped delimiters.
 *
 * @param delimiters List of character delimiter strings to split by (e.g., ["/", ";", ","]).
 *        A plus sign only splits when surrounded by whitespace.
 * @param wordDelimiters List of word-based delimiters to split by (e.g., ["feat.", "ft.", "vs."])
 *        These are matched case-insensitively with surrounding whitespace.
 *        The single-letter "x" is handled specially — only matched when surrounded by spaces.
 * @return List of individual artist names, trimmed and with escaped delimiters restored.
 *         Returns a single-element list with the original string if no splitting occurs.
 *
 * Examples:
 * - "Artist1/Artist2".splitArtistsByDelimiters(listOf("/")) -> ["Artist1", "Artist2"]
 * - "AC\\DC".splitArtistsByDelimiters(listOf("/")) -> ["AC/DC"] (escaped)
 * - "Drake feat. Rihanna".splitArtistsByDelimiters(listOf(), listOf("feat.")) -> ["Drake", "Rihanna"]
 * - "Marshmello x Bastille".splitArtistsByDelimiters(listOf(), listOf("x")) -> ["Marshmello", "Bastille"]
 */
fun String.splitArtistsByDelimiters(
    delimiters: List<String>,
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
): List<String> {
    if ((delimiters.isEmpty() && wordDelimiters.isEmpty()) || this.isBlank()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val sortedDelimiters = delimiters.sortedByDescending { it.length }

    var working = this

    val escapedMappings = mutableMapOf<String, String>()
    sortedDelimiters.forEachIndexed { index, delimiter ->
        val escapedDelimiter = ESCAPE_SEQUENCE + delimiter
        val placeholder = "${ESCAPE_PLACEHOLDER}${index}${ESCAPE_PLACEHOLDER}"
        escapedMappings[placeholder] = delimiter
        working = working.replace(escapedDelimiter, placeholder)
    }

    val patternParts = mutableListOf<String>()

    val sortedWordDelimiters = wordDelimiters.sortedByDescending { it.length }
    for (wd in sortedWordDelimiters) {
        val escaped = Regex.escape(wd)
        if (wd.length == 1) {
            patternParts.add("\\s+$escaped\\s+")
        } else {
            patternParts.add("\\s+$escaped\\s+|\\s+$escaped$|^$escaped\\s+")
        }
    }

    if (sortedDelimiters.isNotEmpty()) {
        val charPattern = sortedDelimiters.joinToString("|") {
            if (it == "+") """(?<=\s)\+(?=\s)""" else Regex.escape(it)
        }
        patternParts.add(charPattern)
    }

    if (patternParts.isEmpty()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val combinedPattern = patternParts.joinToString("|")
    val regex = Regex(combinedPattern, RegexOption.IGNORE_CASE)

    val parts = working.split(regex)

    return parts
        .map { part ->
            var restored = part
            escapedMappings.forEach { (placeholder, delimiter) ->
                restored = restored.replace(placeholder, delimiter)
            }
            restored.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .ifEmpty { if (this.trim().isNotEmpty()) listOf(this.trim()) else emptyList() }
}

/**
 * Extracts featured artists from a song title and returns the cleaned title + extracted artists.
 *
 * Detects patterns like:
 * - "Song (feat. Artist)" / "Song [feat. Artist]"
 * - "Song (ft. Artist1 & Artist2)" / "Song [with Artist]"
 *
 * @param delimiters Character delimiters to further split extracted artist strings
 * @param wordDelimiters Word delimiters to further split extracted artist strings
 * @return Pair of (cleaned title, list of extracted artist names). Empty list if no artists found.
 */
fun String.extractArtistsFromTitle(
    delimiters: List<String> = emptyList(),
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
): Pair<String, List<String>> {
    if (this.isBlank()) return this to emptyList()

    val featureKeywords = listOf("featuring", "feat\\.", "feat", "ft\\.", "ft", "with", "prod\\.", "prod")
    val keywordPattern = featureKeywords.joinToString("|")
    val bracketPattern = Regex(
        """[\(\[]\\?\s*(?:$keywordPattern)\s+(.+?)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    val extractedArtists = mutableListOf<String>()
    var cleanedTitle = this

    bracketPattern.findAll(this).forEach { match ->
        val artistString = match.groupValues[1]
        val artists = artistString.splitArtistsByDelimiters(delimiters, wordDelimiters)
        extractedArtists.addAll(artists)
        cleanedTitle = cleanedTitle.replace(match.value, "")
    }

    return cleanedTitle.trim() to extractedArtists.distinct()
}
