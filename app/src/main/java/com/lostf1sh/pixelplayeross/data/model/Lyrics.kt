package com.lostf1sh.pixelplayeross.data.model

import kotlinx.serialization.Serializable

/**
 * Data model for song lyrics.
 *
 * @param plain List of plain-text lines (unsynced).
 * @param synced List of (milliseconds, line) pairs for synced lyrics.
 * @param areFromRemote Indicates whether the lyrics were fetched from a remote source.
 */
@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null,
    val translation: String? = null,
    val romanization: String? = null,
    /** Singer number from an enhanced-LRC `vN:` prefix or a TTML `ttm:agent`; null when unmarked. */
    val voice: Int? = null,
    /** Backing vocals sung under this line, from a `[bg: …]` line or TTML `x-bg` spans. */
    val background: BackgroundVocals? = null
)

@Serializable
data class BackgroundVocals(
    val text: String,
    val words: List<SyncedWord>? = null
)

@Serializable
data class SyncedWord(
    val time: Int,
    val word: String,
    val startsNewWord: Boolean = true,
    /** When the syllable stops, from a trailing empty LRC tag or a Kugou duration; null when unknown. */
    val endTime: Int? = null
)
