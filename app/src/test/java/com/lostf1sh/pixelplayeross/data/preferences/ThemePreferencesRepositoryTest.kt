package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ThemePreferencesRepositoryTest {

    @Test
    fun `interface preferences default to the bundled font at system scale and change independently`() = runTest {
        val tempDir = Files.createTempDirectory("interface-appearance-test")
        try {
            val repository = ThemePreferencesRepository(
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                )
            )
            assertEquals(InterfaceAppearance(), repository.interfaceAppearanceFlow.first())
            repository.setUseSystemFont(true)
            repository.setInterfaceScale(110)
            assertEquals(InterfaceAppearance(true, 110), repository.interfaceAppearanceFlow.first())
            repository.setInterfaceScale(0)
            assertEquals(InterfaceAppearance(useSystemFont = true), repository.interfaceAppearanceFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `app wide now playing colors are opt-in and persist`() = runTest {
        val tempDir = Files.createTempDirectory("theme-preferences-repository-test")
        try {
            val repository = ThemePreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                )
            )

            assertFalse(repository.globalNowPlayingThemeEnabledFlow.first())

            repository.setGlobalNowPlayingThemeEnabled(true)

            assertTrue(repository.globalNowPlayingThemeEnabledFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
