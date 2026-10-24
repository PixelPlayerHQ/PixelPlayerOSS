package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.lostf1sh.pixelplayeross.data.model.SortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UserPreferencesRepositoryTest {

    @Test
    fun `keep screen awake while playing is opt-in and persists`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(false, repository.keepScreenAwakeWhilePlayingFlow.first())

            repository.setKeepScreenAwakeWhilePlaying(true)

            assertTrue(repository.keepScreenAwakeWhilePlayingFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `clearPreferencesExceptKeys preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.clearPreferencesExceptKeys(emptySet())

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals(NavBarStyle.DEFAULT, repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup clearExisting preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_style",
                        type = "string",
                        stringValue = "restored"
                    )
                ),
                clearExisting = true
            )

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals("restored", repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navBarCornerRadiusFlow clamps values outside the supported UI range`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setNavBarCornerRadius(-1)
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.setNavBarCornerRadius(999)
            assertEquals(MAX_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_corner_radius",
                        type = "int",
                        intValue = -1
                    )
                ),
                clearExisting = false
            )
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navidrome selected music folders persist and clear`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertTrue(repository.navidromeSelectedMusicFolderIdsFlow.first().isEmpty())

            repository.setNavidromeSelectedMusicFolderIds(setOf("flac", "mp3"))
            assertEquals(
                setOf("flac", "mp3"),
                repository.navidromeSelectedMusicFolderIdsFlow.first()
            )

            repository.clearNavidromeSelectedMusicFolderIds()
            assertTrue(repository.navidromeSelectedMusicFolderIdsFlow.first().isEmpty())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `playback speed persists exact hundredths and clamps out of range values`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            // Exact float equality: 1.15 must be stored as the literal 1.15f.
            repository.setPlaybackSpeed(1.15f)
            assertEquals(1.15f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(1.1499999f)
            assertEquals(1.15f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(10f)
            assertEquals(3f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(0f)
            assertEquals(0.25f, repository.playbackSpeedFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy noisy or invalid stored playback speed is normalized on read`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)
            val speedKey = floatPreferencesKey("playback_speed")

            dataStore.edit { it[speedKey] = 0.74999994f }
            assertEquals(0.75f, repository.playbackSpeedFlow.first())

            dataStore.edit { it[speedKey] = Float.NaN }
            assertEquals(1f, repository.playbackSpeedFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `playbackSpeedFlow ignores unrelated preference writes`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.playbackSpeedFlow.test {
                assertEquals(1f, awaitItem())

                repository.setNavBarStyle("compact")
                testScheduler.advanceUntilIdle()
                expectNoEvents()

                repository.setPlaybackSpeed(1.15f)
                assertEquals(1.15f, awaitItem())
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `autoplay on skip is on by default and persists`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertTrue(repository.autoplayOnSkipFlow.first())

            repository.setAutoplayOnSkip(false)
            assertEquals(false, repository.autoplayOnSkipFlow.first())

            repository.setAutoplayOnSkip(true)
            assertTrue(repository.autoplayOnSkipFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `previous button restarts the track by default and persists previous track`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(PreviousButtonBehavior.RESTART_TRACK, repository.previousButtonBehaviorFlow.first())

            repository.setPreviousButtonBehavior(PreviousButtonBehavior.PREVIOUS_TRACK)
            assertEquals(PreviousButtonBehavior.PREVIOUS_TRACK, repository.previousButtonBehaviorFlow.first())

            repository.setPreviousButtonBehavior(PreviousButtonBehavior.RESTART_TRACK)
            assertEquals(PreviousButtonBehavior.RESTART_TRACK, repository.previousButtonBehaviorFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `artist albums collapsed by default is off by default and persists`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(false, repository.artistAlbumsCollapsedByDefaultFlow.first())

            repository.setArtistAlbumsCollapsedByDefault(true)
            assertTrue(repository.artistAlbumsCollapsedByDefaultFlow.first())

            repository.setArtistAlbumsCollapsedByDefault(false)
            assertEquals(false, repository.artistAlbumsCollapsedByDefaultFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `collapsing artist albums by default does not ask for a library rescan`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setArtistAlbumsCollapsedByDefault(true)

            assertEquals(false, repository.artistSettingsRescanRequiredFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateTabOrder backfills Folders right after Liked for a legacy order`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.saveLibraryTabsOrder("""["SONGS","LIKED","ALBUMS","ARTIST","PLAYLISTS"]""")
            repository.migrateTabOrder()

            assertEquals(
                listOf("SONGS", "LIKED", "FOLDERS", "ALBUMS", "ARTIST", "PLAYLISTS"),
                repository.storedLibraryTabsOrder()
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateTabOrder appends Folders when a legacy order has no Liked tab`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.saveLibraryTabsOrder("""["ALBUMS","SONGS"]""")
            repository.migrateTabOrder()

            assertEquals(listOf("ALBUMS", "SONGS", "FOLDERS"), repository.storedLibraryTabsOrder())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateTabOrder does not bring back a Folders tab the user hid`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.saveLibraryTabsOrder("""["SONGS","ALBUMS","FOLDERS","LIKED"]""")
            repository.migrateTabOrder()

            // The stored order is the visible-tab list, so dropping FOLDERS hides it.
            repository.saveLibraryTabsOrder("""["SONGS","ALBUMS","LIKED"]""")
            repository.migrateTabOrder()

            assertEquals(listOf("SONGS", "ALBUMS", "LIKED"), repository.storedLibraryTabsOrder())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateTabOrder leaves a malformed order untouched`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.saveLibraryTabsOrder("not a tab list")
            repository.migrateTabOrder()

            assertEquals("not a tab list", repository.libraryTabsOrderFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateTabOrder with no saved order only marks the migration done`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.migrateTabOrder()
            assertNull(repository.libraryTabsOrderFlow.first())

            repository.saveLibraryTabsOrder("""["SONGS","LIKED"]""")
            repository.migrateTabOrder()

            assertEquals(listOf("SONGS", "LIKED"), repository.storedLibraryTabsOrder())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `folder songs sort defaults to title ascending and persists a new choice`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(SortOption.FolderSongTitleAZ.storageKey, repository.folderSongsSortOptionFlow.first())

            repository.setFolderSongsSortOption(SortOption.FolderSongAlbumArtistDesc.storageKey)

            assertEquals(
                SortOption.FolderSongAlbumArtistDesc.storageKey,
                repository.folderSongsSortOptionFlow.first()
            )
            assertEquals(SortOption.FolderNameAZ.storageKey, repository.foldersSortOptionFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `folder songs sort keeps title descending for a legacy folder name Z-A choice`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setFoldersSortOption(SortOption.FolderNameZA.storageKey)
            assertEquals(SortOption.FolderSongTitleZA.storageKey, repository.folderSongsSortOptionFlow.first())

            repository.setFolderSongsSortOption(SortOption.FolderSongDuration.storageKey)
            assertEquals(SortOption.FolderSongDuration.storageKey, repository.folderSongsSortOptionFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `ensureLibrarySortDefaults stores the inherited folder songs sort so later folder sorts leave it alone`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)
            // An install from before the folder songs sort existed, still holding the old display name.
            dataStore.edit { it[stringPreferencesKey("folders_sort_option")] = SortOption.FolderNameZA.displayName }

            repository.ensureLibrarySortDefaults()
            repository.setFoldersSortOption(SortOption.FolderSongCountDesc.storageKey)

            assertEquals(
                SortOption.FolderSongTitleZA.storageKey,
                dataStore.data.first()[stringPreferencesKey("folder_songs_sort_option")]
            )
            assertEquals(SortOption.FolderSongTitleZA.storageKey, repository.folderSongsSortOptionFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an unknown folder songs sort falls back to title ascending and is repaired`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)
            val folderSongsKey = stringPreferencesKey("folder_songs_sort_option")
            dataStore.edit {
                it[stringPreferencesKey("folders_sort_option")] = SortOption.FolderNameZA.storageKey
                it[folderSongsKey] = "song_artist"
            }

            assertEquals(SortOption.FolderSongTitleAZ.storageKey, repository.folderSongsSortOptionFlow.first())

            repository.ensureLibrarySortDefaults()

            assertEquals(SortOption.FolderSongTitleAZ.storageKey, dataStore.data.first()[folderSongsKey])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrateGenresTab appends Genres after the folders backfill of a legacy order`() = runTest {
        withRepository { repository ->
            repository.saveLibraryTabsOrder("""["LIKED","SONGS","ALBUMS"]""")
            repository.migrateTabOrder()
            repository.migrateGenresTab()

            assertEquals(
                listOf("LIKED", "FOLDERS", "SONGS", "ALBUMS", "GENRES"),
                repository.storedLibraryTabsOrder()
            )
        }
    }

    @Test
    fun `migrateGenresTab does not bring back a Genres tab the user hid`() = runTest {
        withRepository { repository ->
            repository.saveLibraryTabsOrder("""["SONGS","ALBUMS"]""")
            repository.migrateGenresTab()
            assertEquals(listOf("SONGS", "ALBUMS", "GENRES"), repository.storedLibraryTabsOrder())

            // The stored order is the visible-tab list, so dropping GENRES hides it.
            repository.saveLibraryTabsOrder("""["SONGS","ALBUMS"]""")
            repository.migrateGenresTab()

            assertEquals(listOf("SONGS", "ALBUMS"), repository.storedLibraryTabsOrder())
        }
    }

    @Test
    fun `migrateGenresTab keeps an order that already shows Genres`() = runTest {
        withRepository { repository ->
            repository.saveLibraryTabsOrder("""["GENRES","SONGS"]""")
            repository.migrateGenresTab()

            assertEquals(listOf("GENRES", "SONGS"), repository.storedLibraryTabsOrder())
        }
    }

    @Test
    fun `migrateGenresTab leaves malformed, empty and missing orders alone`() = runTest {
        withRepository { repository ->
            repository.migrateGenresTab()
            assertNull(repository.libraryTabsOrderFlow.first())
        }
        withRepository { repository ->
            repository.saveLibraryTabsOrder("not a tab list")
            repository.migrateGenresTab()
            assertEquals("not a tab list", repository.libraryTabsOrderFlow.first())
        }
        withRepository { repository ->
            // An empty order already falls back to the default tabs, Genres included.
            repository.saveLibraryTabsOrder("[]")
            repository.migrateGenresTab()
            assertEquals("[]", repository.libraryTabsOrderFlow.first())
        }
    }

    @Test
    fun `genres sort option defaults to name and persists a valid choice`() = runTest {
        withRepository { repository ->
            assertEquals(SortOption.GenreNameAZ.storageKey, repository.genresSortOptionFlow.first())

            repository.setGenresSortOption(SortOption.GenreSongCountDesc.storageKey)
            assertEquals(SortOption.GenreSongCountDesc.storageKey, repository.genresSortOptionFlow.first())

            repository.setGenresSortOption(SortOption.FolderNameZA.storageKey)
            assertEquals(SortOption.GenreNameAZ.storageKey, repository.genresSortOptionFlow.first())
        }
    }

    private suspend fun TestScope.withRepository(block: suspend (UserPreferencesRepository) -> Unit) {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            block(
                UserPreferencesRepository(
                    dataStore = PreferenceDataStoreFactory.create(
                        scope = backgroundScope,
                        produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                    ),
                    json = Json
                )
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private suspend fun UserPreferencesRepository.storedLibraryTabsOrder(): List<String>? =
        libraryTabsOrderFlow.first()?.let { Json.decodeFromString<List<String>>(it) }
}
