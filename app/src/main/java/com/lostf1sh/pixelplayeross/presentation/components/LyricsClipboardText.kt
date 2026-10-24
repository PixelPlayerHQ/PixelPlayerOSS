package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord

/**
 * Text the lyrics screen copies to the clipboard: the original lyrics as they are shown, without
 * LRC timestamps, voice tags, romanization or translations. Backing vocals are copied when the
 * screen shows them: `includeBackground` follows the "show background vocals" setting.
 */
internal object LyricsClipboardText {

    /**
     * One synced line as the lyrics screen draws it: its sung text, then its backing vocals on a
     * row of their own. Word-timed parts are rebuilt from their words, so syllables join into
     * whole words; other lines keep merged continuation rows on rows of their own.
     */
    fun syncedLine(line: SyncedLine, includeBackground: Boolean = true): String {
        val background = line.background
            ?.takeIf { includeBackground }
            ?.let { shownRows(it.words, it.text) }
            .orEmpty()
        // Like the screen, a line without text is an instrumental break unless it holds backing
        // vocals that are shown.
        if (line.line.isBlank() && background.isEmpty()) return ""
        return (shownRows(line.words, line.line) + background).joinToString("\n")
    }

    /** One plain lyrics entry. Rows after the first hold its romanization or translation. */
    fun plainLine(entry: String): String =
        sanitizeLyricLineText(entry.substringBefore('\n')).trim()

    /**
     * The whole song. Synced lyrics are preferred over plain ones so the result doesn't depend on
     * the active view. Their blank (instrumental) lines become one empty line between stanzas.
     */
    fun fullLyrics(lyrics: Lyrics?, includeBackground: Boolean = true): String = buildString {
        var pendingStanzaBreak = false
        for (row in rows(lyrics, includeBackground)) {
            if (row.isEmpty()) {
                pendingStanzaBreak = isNotEmpty()
                continue
            }
            if (isNotEmpty()) append(if (pendingStanzaBreak) "\n\n" else "\n")
            append(row)
            pendingStanzaBreak = false
        }
    }

    fun hasCopyableText(lyrics: Lyrics?, includeBackground: Boolean = true): Boolean =
        rows(lyrics, includeBackground).any { it.isNotEmpty() }

    private fun rows(lyrics: Lyrics?, includeBackground: Boolean): Sequence<String> {
        val synced = lyrics?.synced
        return if (!synced.isNullOrEmpty()) {
            synced.asSequence().map { syncedLine(it, includeBackground) }
        } else {
            lyrics?.plain.orEmpty().asSequence().map(::plainLine)
        }
    }

    /**
     * The rows the screen shows for a sung part: its timed words clustered into visual words,
     * or, without timings, each row of its text without timestamps or a singer prefix.
     */
    private fun shownRows(words: List<SyncedWord>?, text: String): List<String> {
        val clusters = buildLyricClusters(words)
        if (!clusters.isNullOrEmpty()) return listOf(clusters.joinToString(" ") { it.text })
        return text.lines()
            .map { sanitizeLyricLineText(it).trim() }
            .filter { it.isNotEmpty() }
    }
}
