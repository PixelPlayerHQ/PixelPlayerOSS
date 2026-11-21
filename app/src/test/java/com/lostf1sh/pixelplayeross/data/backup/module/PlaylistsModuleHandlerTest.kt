package com.lostf1sh.pixelplayeross.data.backup.module

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongSummary
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SMART_PLAYLIST_SOURCE_LEGACY
import com.lostf1sh.pixelplayeross.data.model.SmartPlaylistRule
import com.lostf1sh.pixelplayeross.data.model.toPlaylistSource
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.PreferenceBackupEntry
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.data.worker.SyncProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsModuleHandlerTest {
    @TempDir lateinit var tempDir: Path

    private val gson = GsonBuilder().serializeNulls().create()
    private val musicDao = mockk<MusicDao>(relaxed = true)
    private val playlists = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)

    @BeforeEach
    fun noMixPlaylistsByDefault() {
        every { playlists.mixEnabledPlaylistIdsFlow } returns flowOf(emptySet())
    }

    private fun summary(id: Long, title: String) = SongSummary(id, title, "Artist", "Album", 200_000)

    private fun handler(scope: CoroutineScope, syncProgress: Flow<SyncProgress> = emptyFlow()): PlaylistsModuleHandler {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir.toFile()
        val sync = mockk<SyncManager>()
        every { sync.syncProgress } returns syncProgress
        return PlaylistsModuleHandler(
            context, playlists, preferences, musicDao, BackupSongResolver(musicDao), gson,
            Provider { sync }, scope
        )
    }

    private val pendingFile: File
        get() = File(tempDir.toFile(), "pending_playlists_restore.json")

    /** The saved playlists, kept by the repository fake from [storePlaylistsInMemory]. */
    private var stored = emptyList<Playlist>()

    private fun storePlaylistsInMemory() {
        coEvery { playlists.getPlaylistsOnce() } answers { stored }
        coEvery { playlists.replaceAllPlaylists(any()) } answers { stored = firstArg() }
        // Like the real repository: updatePlaylist dates the playlist now, while
        // replacePlaylistSongs only writes the songs of a playlist that still exists.
        coEvery { playlists.updatePlaylist(any()) } answers {
            val playlist = firstArg<Playlist>().copy(lastModified = System.currentTimeMillis())
            stored = stored.map { if (it.id == playlist.id) playlist else it }
        }
        coEvery { playlists.replacePlaylistSongs(any(), any()) } answers {
            replaceStoredSongs(firstArg(), secondArg())
        }
    }

    private fun replaceStoredSongs(playlistId: String, songIds: List<String>): Boolean {
        val exists = stored.any { it.id == playlistId }
        stored = stored.map { if (it.id == playlistId) it.copy(songIds = songIds) else it }
        return exists
    }

    private fun metadata(title: String) = BackupSongMetadata(title, "Artist", "Album", 200_000)

    /**
     * A backup of one "road" playlist holding [songIds], where song "1" is titled the first of
     * [titles], "2" the second, and so on. By default each song is in it once, in that order.
     */
    private fun roadTripBackup(
        vararg titles: String,
        songIds: List<String> = titles.indices.map { (it + 1).toString() },
        source: String = "LOCAL",
    ): String {
        val playlist = mapOf(
            "id" to "road",
            "name" to "Road trip",
            "songIds" to songIds,
            "source" to source,
            "createdAt" to BACKUP_TIME,
            "lastModified" to BACKUP_TIME,
        )
        val songMetadata = titles.withIndex().associate { (index, title) ->
            (index + 1).toString() to metadata(title)
        }
        return gson.toJson(mapOf("playlists" to listOf(playlist), "songMetadata" to songMetadata))
    }

    private fun MutableStateFlow<SyncProgress>.finishSync() {
        value = SyncProgress(isRunning = true)
        value = SyncProgress(isRunning = false, isCompleted = true)
    }

    @Test
    fun `playlists keep their name and order across device ids`() = runTest {
        val handler = handler(backgroundScope)
        val local = Playlist("road", "Road trip", listOf("2", "1"))
        val smart = local.copy(id = "smart", source = SMART_PLAYLIST_SOURCE_LEGACY)
        coEvery { playlists.getPlaylistsOnce() } returns listOf(local, smart)
        every { playlists.playlistSongOrderModesFlow } returns flowOf(mapOf("road" to "custom"))
        every { playlists.playlistsSortOptionFlow } returns flowOf("name_az")
        // "road" is a mix; "elsewhere" is a mix whose playlist isn't part of this backup.
        every { playlists.mixEnabledPlaylistIdsFlow } returns flowOf(setOf("road", "elsewhere"))
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"))
        val payload = handler.export()

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        handler.restore(payload)

        coVerify {
            playlists.replaceAllPlaylists(
                listOf(
                    local.copy(songIds = listOf("42", "41")),
                    smart.copy(songIds = listOf("42", "41")),
                )
            )
            playlists.setPlaylistSongOrderModes(mapOf("road" to "custom"))
            // Mix status travels with the playlists that are backed up, and only those.
            playlists.setMixEnabledPlaylistIds(setOf("road"))
        }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `mix status is kept out of global settings`() {
        assertTrue(PlaylistsModuleHandler.MIX_ENABLED_PLAYLISTS_KEY in GlobalSettingsModuleHandler.EXCLUDED_KEYS)
    }

    @Test
    fun `songs restored before the library scan are filled in once a sync finishes`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()

        // Fresh install: nothing scanned yet. The handler starts watching sync progress.
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        runCurrent()
        handler.restore(roadTripBackup("One", "Two"))
        assertEquals(emptyList<String>(), stored.single().songIds)
        assertTrue(pendingFile.exists())

        // The first scan indexes the songs under this device's ids. The running state is
        // conflated away before the collector sees it, as a short sync can do in production.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        // runCurrent, not advanceUntilIdle: the latter stops once only backgroundScope work is left.
        runCurrent()
        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a song the backup repeats is filled in as often as it repeats`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", songIds = listOf("1", "2", "1")))
        assertEquals(emptyList<String>(), stored.single().songIds)

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41", "42", "41"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `songs found later go after the right copy of a repeated song`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        runCurrent()
        handler.restore(
            roadTripBackup("One", "Two", "Three", "Four", songIds = listOf("1", "3", "2", "1", "4"))
        )
        assertEquals(listOf("41", "42", "41"), stored.single().songIds)

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(
            summary(41, "One"), summary(42, "Two"), summary(43, "Three"), summary(44, "Four")
        )
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41", "43", "42", "41", "44"), stored.single().songIds)
    }

    @Test
    fun `a song the user added before it resolved isn't added again`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        // Two songs called Two match the backup's equally well, so neither is picked.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(
            summary(41, "One"), summary(42, "Two"), summary(52, "Two")
        )
        runCurrent()
        handler.restore(roadTripBackup("One", "Two"))
        assertEquals(listOf("41"), stored.single().songIds)

        // The user adds the right one, and the other is deleted, so the backup's Two now matches it.
        stored = listOf(stored.single().copy(songIds = listOf("41", "42")))
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41", "42"), stored.single().songIds)
        coVerify(exactly = 0) { playlists.replacePlaylistSongs(any(), any()) }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `filling in a restored playlist keeps its date and a rename made meanwhile`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        runCurrent()
        handler.restore(roadTripBackup("One", "Two"))

        // The user renames the playlist right after the retry reads it.
        coEvery { playlists.getPlaylistsOnce() } answers {
            stored.also { stored = stored.map { it.copy(name = "Summer road trip") } }
        }
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        val playlist = stored.single()
        assertEquals(listOf("41", "42"), playlist.songIds)
        assertEquals("Summer road trip", playlist.name)
        // Playlists sorted by date stay in their restored order.
        assertEquals(BACKUP_TIME, playlist.lastModified)
    }

    @Test
    fun `a song removed after a sync added it stays removed while another is still missing`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", "Three"))
        assertEquals(listOf("41"), stored.single().songIds)

        // A sync finds Two, but Three isn't on this device.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()
        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertTrue(pendingFile.exists())

        stored = listOf(stored.single().copy(songIds = listOf("41")))
        // The next sync with the same library; its running state is seen this time, so the idle
        // state it leaves behind counts as a new one.
        syncProgress.value = SyncProgress(isRunning = true)
        runCurrent()
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41"), stored.single().songIds)
        assertTrue(pendingFile.exists())
    }

    @Test
    fun `a restored playlist deleted before its songs resolve is skipped`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two"))
        assertTrue(pendingFile.exists())

        stored = emptyList()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        assertEquals(emptyList<Playlist>(), stored)
        coVerify(exactly = 0) {
            playlists.replacePlaylistSongs(any(), any())
            playlists.updatePlaylist(any())
        }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a smart playlist with a rule isn't filled in from the backup`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", source = SmartPlaylistRule.TOP_PLAYED.toPlaylistSource()))
        assertEquals(listOf("41"), stored.single().songIds)

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        // The rule rebuilds its songs; the backup's would be stale.
        assertEquals(listOf("41"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a legacy restore drops the pending restore it replaces`() = runTest {
        val handler = handler(backgroundScope)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        handler.restore(roadTripBackup("One", "Two"))
        assertTrue(pendingFile.exists())

        val legacyPlaylist = Playlist(
            "mix", "Old mix", listOf("7"), createdAt = BACKUP_TIME, lastModified = BACKUP_TIME
        )
        val legacyEntry = PreferenceBackupEntry(
            key = PlaylistsModuleHandler.LEGACY_USER_PLAYLISTS_KEY,
            type = "string",
            stringValue = gson.toJson(listOf(legacyPlaylist)),
        )
        handler.restore(gson.toJson(listOf(legacyEntry)))

        assertEquals(listOf(legacyPlaylist), stored)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a song removed from a partly restored playlist stays removed after the next sync`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        // Three isn't on this device, so the restore stays pending.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", "Three"))
        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertTrue(pendingFile.exists())

        stored = listOf(stored.single().copy(songIds = listOf("41")))
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41"), stored.single().songIds)
    }

    @Test
    fun `songs found by a later sync are added around the user's edits`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", "Three"))
        assertEquals(listOf("41"), stored.single().songIds)

        stored = listOf(stored.single().copy(songIds = listOf("41", "99")))
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(
            summary(41, "One"), summary(42, "Two"), summary(43, "Three"), summary(99, "Added later")
        )
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41", "42", "43", "99"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a sync finishing while restore writes the playlists still fills them in`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        val restoreSaving = CompletableDeferred<Unit>()
        val saveAllowed = CompletableDeferred<Unit>()
        var firstSave = true
        coEvery { playlists.replaceAllPlaylists(any()) } coAnswers {
            if (firstSave) {
                firstSave = false
                restoreSaving.complete(Unit)
                saveAllowed.await()
            }
            stored = firstArg()
        }
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        runCurrent()

        val restore = launch { handler.restore(roadTripBackup("One", "Two")) }
        restoreSaving.await()
        // The first scan finishes after the restore matched against the empty library, but
        // before its playlists are saved.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()
        saveAllowed.complete(Unit)
        restore.join()
        runCurrent()

        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a backup song without metadata doesn't leave the restore pending`() = runTest {
        val handler = handler(backgroundScope)
        storePlaylistsInMemory()
        // Song 9 was deleted before the backup was made, so the backup can't describe it.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        handler.restore(roadTripBackup("One", "Two", songIds = listOf("1", "9", "2")))

        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a restore stops waiting once only songs without metadata are missing`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", songIds = listOf("1", "9", "2")))
        assertEquals(listOf("41"), stored.single().songIds)
        assertTrue(pendingFile.exists())

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a pending restore waiting only on a song without metadata is dropped unfilled`() = runTest {
        storePlaylistsInMemory()
        stored = listOf(Playlist("road", "Road trip", listOf("41")))
        pendingFile.writeText(
            gson.toJson(
                mapOf(
                    "playlists" to listOf(Playlist("road", "Road trip", listOf("1", "9"))),
                    "songMetadata" to mapOf("1" to metadata("One")),
                    "unresolvedSongIds" to listOf("9"),
                )
            )
        )
        // This library's song 9 isn't the one the backup's playlist had under that id.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(9, "Nine"))
        handler(backgroundScope, MutableStateFlow(SyncProgress()))
        runCurrent()

        assertEquals(listOf("41"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a retry that fails keeps the pending restore for the next sync`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        val backup = mapOf(
            "playlists" to listOf(
                Playlist("road", "Road trip", listOf("1", "2")),
                Playlist("gym", "Gym", listOf("2", "1")),
            ),
            "songMetadata" to mapOf("1" to metadata("One"), "2" to metadata("Two")),
        )
        handler.restore(gson.toJson(backup))
        assertEquals(listOf(listOf("41"), listOf("41")), stored.map { it.songIds })

        // The retry fills in the first playlist, then the database fails on the second.
        var gymWritesFail = true
        coEvery { playlists.replacePlaylistSongs("gym", any()) } answers {
            check(!gymWritesFail) { "database is locked" }
            replaceStoredSongs(firstArg(), secondArg())
        }
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()
        assertEquals(listOf(listOf("41", "42"), listOf("41")), stored.map { it.songIds })
        assertTrue(pendingFile.exists())

        // The next sync still retries, and the song already added to the first isn't added twice.
        gymWritesFail = false
        syncProgress.value = SyncProgress(isRunning = true)
        runCurrent()
        syncProgress.finishSync()
        runCurrent()

        assertEquals(listOf(listOf("41", "42"), listOf("42", "41")), stored.map { it.songIds })
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a pending restore saved as the whole backup payload is dropped unfilled`() = runTest {
        storePlaylistsInMemory()
        stored = listOf(Playlist("road", "Road trip", listOf("41")))
        // The earlier format: the restored payload itself, with no record of which songs the
        // restore left out.
        val oldFormat = JsonParser.parseString(roadTripBackup("One", "Two")).asJsonObject.apply {
            add("playlistSongOrderModes", gson.toJsonTree(mapOf("road" to "custom")))
            addProperty("playlistsSortOption", "name_az")
            add("coverImages", gson.toJsonTree(mapOf("road" to "AAAA")))
        }
        pendingFile.writeText(oldFormat.toString())
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        handler(backgroundScope, MutableStateFlow(SyncProgress()))
        runCurrent()

        // Two may have been removed by the user, so it isn't brought back.
        assertEquals(listOf("41"), stored.single().songIds)
        coVerify(exactly = 0) {
            playlists.replacePlaylistSongs(any(), any())
            playlists.replaceAllPlaylists(any())
        }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a pending file cut off mid-write is dropped`() = runTest {
        storePlaylistsInMemory()
        stored = listOf(Playlist("road", "Road trip", listOf("41")))
        pendingFile.writeText("""{"playlists":[{"id":"road","songIds":["1",""")
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        handler(backgroundScope, MutableStateFlow(SyncProgress()))
        runCurrent()

        assertEquals(listOf("41"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a pending restore that can't be rewritten is still filled in by the next sync`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        runCurrent()
        handler.restore(roadTripBackup("One", "Two", "Three"))
        val savedPending = pendingFile.readText()

        // A sync finds Two, but saving what's still missing fails, as on a full disk.
        blockPendingWrites()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.finishSync()
        runCurrent()
        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertEquals(savedPending, pendingFile.readText())

        unblockPendingWrites()
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(
            summary(41, "One"), summary(42, "Two"), summary(43, "Three")
        )
        syncProgress.value = SyncProgress(isRunning = true)
        runCurrent()
        syncProgress.finishSync()
        runCurrent()

        // Two, which the earlier file still lists, isn't added a second time.
        assertEquals(listOf("41", "42", "43"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `a restore whose pending file can't be saved drops the earlier restore's`() = runTest {
        val handler = handler(backgroundScope)
        storePlaylistsInMemory()
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        handler.restore(roadTripBackup("One", "Two"))
        assertTrue(pendingFile.exists())

        blockPendingWrites()
        handler.restore(roadTripBackup("Three", "Four"))

        // Left in place, it would fill this backup's playlist in with the other backup's songs.
        assertFalse(pendingFile.exists())
    }

    /**
     * Makes saving the pending file fail, as on a full disk, until [unblockPendingWrites]: a
     * directory that isn't empty sits where it's written before being moved into place.
     */
    private fun blockPendingWrites() {
        File(tempDir.toFile(), PENDING_TEMP_FILE_NAME).apply {
            mkdir()
            resolve("in-the-way").writeText("")
        }
    }

    private fun unblockPendingWrites() {
        File(tempDir.toFile(), PENDING_TEMP_FILE_NAME).deleteRecursively()
    }

    private companion object {
        const val PENDING_TEMP_FILE_NAME = "pending_playlists_restore.json.tmp"
        const val BACKUP_TIME = 1_700_000_000_000L
    }
}
