package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** How word-synced lyrics light up, shared by the settings repository and the lyrics sheet. */
val LYRICS_WORD_HIGHLIGHT_STYLE_KEY = stringPreferencesKey("lyrics_word_highlight_style")

/** Whether lines marked for different singers (`v1:`, `v2:`) sit on opposite sides. */
val LYRICS_DUET_LAYOUT_KEY = booleanPreferencesKey("lyrics_duet_layout_enabled")

/** Whether `[bg: …]` backing vocals are shown under their line. */
val SHOW_LYRICS_BACKGROUND_VOCALS_KEY = booleanPreferencesKey("show_lyrics_background_vocals")

const val DEFAULT_LYRICS_DUET_LAYOUT_ENABLED = true
const val DEFAULT_SHOW_LYRICS_BACKGROUND_VOCALS = true

enum class LyricsWordHighlightStyle(val storageValue: String) {
    /** A soft-edged gradient sweeps across each syllable while it is sung. */
    WIPE("wipe"),

    /** Each syllable fades from the dim color to the highlight while it is sung. */
    FADE("fade"),

    /** Each syllable switches to the highlight as soon as it starts. */
    CLASSIC("classic");

    companion object {
        val DEFAULT = WIPE

        fun fromStorage(value: String?): LyricsWordHighlightStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
