package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class LocalAlbumGroupingPreferenceTest {

    @Test
    fun `title grouping defaults off and both changes require a local reindex`() = runTest {
        val tempDir = Files.createTempDirectory("local-album-grouping-preference-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertFalse(repository.groupLocalAlbumsByTitleFlow.first())
            assertFalse(repository.getLocalAlbumGroupingSettings().requiresReindex)

            repository.setGroupLocalAlbumsByTitle(true)
            val enabled = repository.getLocalAlbumGroupingSettings()
            assertTrue(repository.groupLocalAlbumsByTitleFlow.first())
            assertTrue(enabled.byTitle)
            assertTrue(enabled.requiresReindex)
            repository.markLocalAlbumGroupingApplied(enabled.indexVersion)
            assertFalse(repository.getLocalAlbumGroupingSettings().requiresReindex)

            repository.setGroupLocalAlbumsByTitle(false)
            val disabled = repository.getLocalAlbumGroupingSettings()
            assertFalse(repository.groupLocalAlbumsByTitleFlow.first())
            assertFalse(disabled.byTitle)
            assertTrue(disabled.requiresReindex)
            repository.markLocalAlbumGroupingApplied(disabled.indexVersion)
            assertFalse(repository.getLocalAlbumGroupingSettings().requiresReindex)

            repository.setGroupLocalAlbumsByTitle(true)
            val interrupted = repository.getLocalAlbumGroupingSettings()
            repository.setGroupLocalAlbumsByTitle(false)
            assertTrue(repository.getLocalAlbumGroupingSettings().requiresReindex)
            repository.markLocalAlbumGroupingApplied(interrupted.indexVersion)
            assertTrue(repository.getLocalAlbumGroupingSettings().requiresReindex)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
