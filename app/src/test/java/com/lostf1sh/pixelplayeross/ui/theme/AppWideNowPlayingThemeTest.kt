package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import org.junit.Test

class AppWideNowPlayingThemeTest {

    private val currentScheme = ColorSchemePair(lightColorScheme(), darkColorScheme())
    private val lastScheme = ColorSchemePair(lightColorScheme(), darkColorScheme())

    @Test
    fun `feature is opt-in`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = false,
            currentSongId = "song-1",
            isPlaying = true,
            currentSongScheme = currentScheme,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `ready current song palette is used`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-1",
            isPlaying = true,
            currentSongScheme = currentScheme,
            lastValidSongId = null,
            lastValidScheme = null
        )

        assertThat(resolved).isSameInstanceAs(currentScheme)
    }

    @Test
    fun `paused song keeps its last valid palette`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-1",
            isPlaying = false,
            currentSongScheme = null,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isSameInstanceAs(lastScheme)
    }

    @Test
    fun `stale palette from another song is not reused`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-2",
            isPlaying = false,
            currentSongScheme = null,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isNull()
    }
}
