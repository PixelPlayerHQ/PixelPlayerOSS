package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RecapHomeCardPreferenceTest {

    @Test
    fun `dismissed recap home card year defaults to none and persists`() = runTest {
        val tempDir = Files.createTempDirectory("recap-home-card-preference-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(0, repository.recapHomeCardDismissedYearFlow.first())

            repository.setRecapHomeCardDismissedYear(2026)

            assertEquals(2026, repository.recapHomeCardDismissedYearFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
