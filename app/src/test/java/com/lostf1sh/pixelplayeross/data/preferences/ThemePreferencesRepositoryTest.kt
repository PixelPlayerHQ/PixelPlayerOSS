package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ThemePreferencesRepositoryTest {

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
