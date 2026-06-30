package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lostf1sh.pixelplayeross.data.database.LocalPlaylistDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSongCountTest {

    private lateinit var db: PixelPlayerDatabase
    private lateinit var dao: LocalPlaylistDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: PlaylistPreferencesRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        dao = db.localPlaylistDao()
        dataStore = PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("test_settings_${System.nanoTime()}")
        }
        val userPrefs = UserPreferencesRepository(dataStore, Json { ignoreUnknownKeys = true })
        repo = PlaylistPreferencesRepository(dao, userPrefs)
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun countFor(playlistId: String): Int =
        repo.userPlaylistsFlow.first().first { it.id == playlistId }.songIds.size

    @Test
    fun menuSongCount_reflectsAddAndRemove() = runTest {
        val playlist = repo.createPlaylist(name = "J-Pop", songIds = listOf("10", "20", "30"))
        assertEquals("initial count", 3, countFor(playlist.id))

        repo.removeSongFromPlaylist(playlist.id, "20")
        assertEquals("after removing one song", 2, countFor(playlist.id))

        repo.removeSongFromPlaylist(playlist.id, "30")
        assertEquals("after removing a second song", 1, countFor(playlist.id))

        repo.addSongsToPlaylist(playlist.id, listOf("40"))
        assertEquals("after adding one song", 2, countFor(playlist.id))
    }

    @Test
    fun concurrentRemovals_doNotLoseUpdates() = runBlocking {
        val playlist = repo.createPlaylist(
            name = "Race",
            songIds = listOf("1", "2", "3", "4", "5")
        )
        assertEquals(5, countFor(playlist.id))

        coroutineScope {
            listOf("1", "2", "3", "4").forEach { id ->
                launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, id) }
            }
        }

        assertEquals("All concurrent removals must persist", 1, countFor(playlist.id))
    }

    @Test
    fun quickRemoveThenAdd_keepsCountAccurate() = runBlocking {
        val playlist = repo.createPlaylist(
            name = "J-Pop",
            songIds = listOf("1", "2", "3", "4", "5", "6")
        )
        assertEquals(6, countFor(playlist.id))

        coroutineScope {
            launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, "2") }
            launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, "4") }
        }
        assertEquals("count after removing two songs", 4, countFor(playlist.id))

        repo.addSongsToPlaylist(playlist.id, listOf("7", "8"))
        assertEquals("count after adding two songs", 6, countFor(playlist.id))
    }
}
