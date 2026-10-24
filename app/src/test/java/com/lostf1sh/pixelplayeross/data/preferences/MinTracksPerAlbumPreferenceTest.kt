package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MinTracksPerAlbumPreferenceTest {

    @Test
    fun `unrelated preference writes do not re-emit the minimum tracks per album`() = runTest {
        val tempDir = Files.createTempDirectory("min-tracks-per-album-preference-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            // The albums pager is rebuilt on every emission, so a write such as the playback
            // queue snapshot saved when an album starts playing must not emit again.
            repository.minTracksPerAlbumFlow.test {
                assertEquals(1, awaitItem())

                repository.setRecapHomeCardDismissedYear(2026)
                repository.setMinTracksPerAlbum(3)

                assertEquals(3, awaitItem())
                expectNoEvents()
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
