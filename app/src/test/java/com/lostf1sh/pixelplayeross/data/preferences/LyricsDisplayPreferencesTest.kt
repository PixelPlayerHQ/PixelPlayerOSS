package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class LyricsDisplayPreferencesTest {

    @Test
    fun `unknown or missing highlight style falls back to the gradient sweep`() {
        assertEquals(LyricsWordHighlightStyle.WIPE, LyricsWordHighlightStyle.fromStorage(null))
        assertEquals(LyricsWordHighlightStyle.WIPE, LyricsWordHighlightStyle.fromStorage("sparkle"))
    }

    @Test
    fun `highlight styles round trip through their stored value`() {
        LyricsWordHighlightStyle.entries.forEach { style ->
            assertEquals(style, LyricsWordHighlightStyle.fromStorage(style.storageValue))
        }
    }

    @Test
    fun `lyrics display settings default on and persist`() = runTest {
        val tempDir = Files.createTempDirectory("lyrics-display-preferences-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)

            assertEquals(LyricsWordHighlightStyle.WIPE, repository.lyricsWordHighlightStyleFlow.first())
            assertTrue(repository.lyricsDuetLayoutEnabledFlow.first())
            assertTrue(repository.showLyricsBackgroundVocalsFlow.first())

            repository.setLyricsWordHighlightStyle(LyricsWordHighlightStyle.FADE)
            repository.setLyricsDuetLayoutEnabled(false)
            repository.setShowLyricsBackgroundVocals(false)

            assertEquals(LyricsWordHighlightStyle.FADE, repository.lyricsWordHighlightStyleFlow.first())
            assertFalse(repository.lyricsDuetLayoutEnabledFlow.first())
            assertFalse(repository.showLyricsBackgroundVocalsFlow.first())
            // The lyrics sheet reads the same keys straight from the DataStore.
            val stored = dataStore.data.first()
            assertEquals("fade", stored[LYRICS_WORD_HIGHLIGHT_STYLE_KEY])
            assertEquals(false, stored[LYRICS_DUET_LAYOUT_KEY])
            assertEquals(false, stored[SHOW_LYRICS_BACKGROUND_VOCALS_KEY])

            dataStore.edit { it[LYRICS_WORD_HIGHLIGHT_STYLE_KEY] = "classic" }
            assertEquals(LyricsWordHighlightStyle.CLASSIC, repository.lyricsWordHighlightStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
