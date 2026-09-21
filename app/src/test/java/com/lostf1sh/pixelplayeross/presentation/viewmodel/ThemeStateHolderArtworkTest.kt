package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.net.Uri
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtColorAccuracy
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtPaletteStyle
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreference
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreferencesRepository
import com.lostf1sh.pixelplayeross.utils.AlbumArtUtils
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStateHolderArtworkTest {
    private val artworkVersion = MutableStateFlow(0L)
    private val processor = mockk<ColorSchemeProcessor>()
    private val oldScheme = scheme(Color.Red)
    private val newScheme = scheme(Color.Blue)

    @BeforeEach
    fun setUpArtworkVersion() {
        mockkObject(AlbumArtUtils)
        every { AlbumArtUtils.artworkCacheVersion } returns artworkVersion
        coEvery { processor.getOrGenerateColorScheme(any(), any(), any(), any()) } answers {
            if (artworkVersion.value == 0L) oldScheme else newScheme
        }
    }

    @AfterEach
    fun restoreArtworkUtils() {
        unmockkObject(AlbumArtUtils)
    }

    @Test
    fun `artwork refresh regenerates subscribed local palettes and leaves remote palettes intact`() = runTest {
        val holder = holder()
        holder.initialize(backgroundScope)
        val localUri = LocalArtworkUri.buildSongUri(1L)
        val inactiveUri = LocalArtworkUri.buildSongUri(2L)
        val remoteUri = "https://example.com/cover.jpg"
        val local = holder.getAlbumColorSchemeFlow(localUri, eager = false)
        val inactive = holder.getAlbumColorSchemeFlow(inactiveUri, eager = false)
        val remote = holder.getAlbumColorSchemeFlow(remoteUri, eager = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { remote.collect {} }
        listOf(localUri, inactiveUri, remoteUri).forEach(holder::ensureAlbumColorScheme)
        local.first { it == oldScheme }
        inactive.first { it == oldScheme }
        remote.first { it == oldScheme }
        runCurrent()

        artworkVersion.value = 1L
        local.first { it == newScheme }

        assertNull(inactive.value)
        assertEquals(oldScheme, remote.value)
        coVerify(exactly = 2) { processor.getOrGenerateColorScheme(localUri, any(), any(), any()) }
        coVerify(exactly = 1) { processor.getOrGenerateColorScheme(inactiveUri, any(), any(), any()) }
        coVerify(exactly = 1) { processor.getOrGenerateColorScheme(remoteUri, any(), any(), any()) }
    }

    @Test
    fun `artwork refresh updates current playing local palette without an album subscriber`() = runTest {
        val holder = holder()
        holder.initialize(backgroundScope)
        val localUri = LocalArtworkUri.buildSongUri(1L)
        val uri = mockk<Uri>()
        every { uri.toString() } returns localUri
        holder.extractAndGenerateColorScheme(uri, localUri)
        runCurrent()
        assertEquals(oldScheme, holder.currentAlbumArtColorSchemePair.value)

        artworkVersion.value = 1L
        holder.currentAlbumArtColorSchemePair.first { it == newScheme }

        assertEquals(localUri, holder.currentAlbumArtUri.value)
        coVerify(exactly = 2) { processor.getOrGenerateColorScheme(localUri, any(), any(), any()) }
    }

    @Test
    fun `cancelled palette generation propagates cancellation`() = runTest {
        val holder = holder()
        val localUri = LocalArtworkUri.buildSongUri(1L)
        val uri = mockk<Uri>()
        every { uri.toString() } returns localUri
        coEvery { processor.getOrGenerateColorScheme(any(), any(), any(), any()) } throws CancellationException()

        val failure = runCatching { holder.extractAndGenerateColorScheme(uri, localUri) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    private fun holder(): ThemeStateHolder {
        val preferences = mockk<ThemePreferencesRepository> {
            every { playerThemePreferenceFlow } returns flowOf(ThemePreference.ALBUM_ART)
            every { globalNowPlayingThemeEnabledFlow } returns flowOf(false)
            every { albumArtPaletteStyleFlow } returns flowOf(AlbumArtPaletteStyle.default)
            every { albumArtColorAccuracyFlow } returns flowOf(AlbumArtColorAccuracy.DEFAULT)
        }
        return ThemeStateHolder(processor, preferences)
    }

    private fun scheme(color: Color): ColorSchemePair = ColorSchemePair(
        light = lightColorScheme(primary = color),
        dark = darkColorScheme(primary = color)
    )
}
