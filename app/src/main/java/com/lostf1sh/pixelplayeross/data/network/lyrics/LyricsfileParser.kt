package com.lostf1sh.pixelplayeross.data.network.lyrics

import java.util.Locale
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Converts LRCLIB's open Lyricsfile YAML payload into the enhanced-LRC format we persist. */
internal object LyricsfileParser {
    private const val MAX_LYRICSFILE_CODE_POINTS = 1_000_000
    private const val MAX_LINES = 10_000
    private const val MAX_WORDS_PER_LINE = 2_000

    private fun newYaml(): Yaml {
        val options = LoaderOptions().apply {
            codePointLimit = MAX_LYRICSFILE_CODE_POINTS
            maxAliasesForCollections = 0
            nestingDepthLimit = 20
            isAllowDuplicateKeys = false
        }
        return Yaml(SafeConstructor(options))
    }

    fun toEnhancedLrc(rawLyricsfile: String?): String? {
        val source = rawLyricsfile?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { newYaml().load<Any?>(source) }.getOrNull() as? Map<*, *>
            ?: return null
        // Lyricsfile is still a draft. Refuse unknown versions instead of silently
        // interpreting a future, potentially incompatible schema as version 1.0.
        if (root["version"]?.toString() != "1.0") return null
        val lines = (root["lines"] as? List<*>)
            ?.take(MAX_LINES)
            .orEmpty()

        return lines.mapNotNull(::parseLine)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
    }

    private fun parseLine(rawLine: Any?): String? {
        val line = rawLine as? Map<*, *> ?: return null
        val text = line["text"]?.toString()?.sanitizeLineText().orEmpty()
        val rawWords = (line["words"] as? List<*>)
            ?.take(MAX_WORDS_PER_LINE)
            .orEmpty()
        val words = rawWords.mapNotNull(::parseWord)
        val startMs = line["start_ms"].asMilliseconds()
            ?: words.firstOrNull()?.startMs
            ?: return null

        val content = if (words.isNotEmpty()) {
            buildWordSyncedContent(text, words)
        } else {
            text
        }
        if (content.isBlank()) return null
        return "[${formatTimestamp(startMs)}]$content"
    }

    /**
     * Lyricsfile's line text is canonical; word entries from some providers omit punctuation or
     * surrounding spaces. Place timing tags at the matching word positions in that canonical text
     * so enhanced-LRC playback keeps both timing and the exact visible lyric.
     */
    private fun buildWordSyncedContent(text: String, words: List<TimedWord>): String {
        if (text.isNotBlank()) {
            val aligned = StringBuilder()
            var cursor = 0
            var allWordsAligned = true
            for (word in words) {
                val token = word.text.sanitizeLineText().trim()
                if (token.isEmpty()) continue
                val position = text.indexOf(token, startIndex = cursor)
                if (position < cursor) {
                    allWordsAligned = false
                    break
                }
                aligned.append(text, cursor, position)
                aligned.append('<').append(formatTimestamp(word.startMs)).append('>')
                aligned.append(token)
                cursor = position + token.length
            }
            if (allWordsAligned && aligned.isNotEmpty()) {
                aligned.append(text, cursor, text.length)
                return aligned.toString()
            }
        }

        return words.joinToString(separator = "") { word ->
            "<${formatTimestamp(word.startMs)}>${word.text.sanitizeLineText()}"
        }
    }

    private fun parseWord(rawWord: Any?): TimedWord? {
        val word = rawWord as? Map<*, *> ?: return null
        val text = word["text"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        val startMs = word["start_ms"].asMilliseconds() ?: return null
        return TimedWord(startMs = startMs, text = text)
    }

    private fun Any?.asMilliseconds(): Int? {
        val value = when (this) {
            is Number -> toLong()
            is String -> toLongOrNull()
            else -> null
        } ?: return null
        return value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }

    private fun String.sanitizeLineText(): String =
        replace('\r', ' ').replace('\n', ' ')

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1_000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private data class TimedWord(
        val startMs: Int,
        val text: String,
    )
}
