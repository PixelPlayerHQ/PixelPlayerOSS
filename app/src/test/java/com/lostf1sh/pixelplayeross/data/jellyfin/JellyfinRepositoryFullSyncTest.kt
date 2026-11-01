package com.lostf1sh.pixelplayeross.data.jellyfin

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.JellyfinDao
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinApiService
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The dashboard skips its automatic sync for a day after a complete one, so only a sync whose
 * songs reached the shared library may count as complete.
 */
class JellyfinRepositoryFullSyncTest {

    // Saved credentials, so the repository starts out logged in.
    private val storedPrefs = mutableMapOf<String, Any>(
        "server_url" to "https://music.example.com",
        "username" to "listener",
        "access_token" to "token",
        "user_id" to "user-1",
    )

    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true) {
        every { putLong(any(), any()) } answers {
            storedPrefs[firstArg()] = secondArg<Long>()
            self as SharedPreferences.Editor
        }
    }

    private val prefs: SharedPreferences = mockk(relaxed = true) {
        every { getString(any(), any()) } answers { storedPrefs[firstArg()] as? String ?: secondArg() }
        every { getLong(any(), any()) } answers { storedPrefs[firstArg()] as? Long ?: secondArg() }
        every { edit() } returns prefsEditor
    }

    private val api: JellyfinApiService = mockk(relaxed = true) {
        coEvery { getUserViews() } returns Result.success(emptyList())
        coEvery { getMusicItems(any(), any(), any()) } returns Result.success(0 to emptyList())
        coEvery { getPlaylists() } returns Result.success(emptyList())
    }

    private val jellyfinDao: JellyfinDao = mockk(relaxed = true) {
        coEvery { getPlaylistCount() } returns 0
        coEvery { getAllPlaylistsList() } returns emptyList()
    }

    private val musicDao: MusicDao = mockk(relaxed = true) {
        coEvery { getAllJellyfinSongIds() } returns emptyList()
    }

    private fun repository() = JellyfinRepository(
        api = api,
        dao = jellyfinDao,
        musicDao = musicDao,
        playlistPreferencesRepository = mockk(relaxed = true),
        userPreferencesRepository = mockk(relaxed = true) {
            every { jellyfinSelectedLibraryIdsFlow } returns flowOf(emptySet())
            every { groupByAlbumArtistFlow } returns flowOf(true)
        },
        // Encrypted prefs are unavailable on the JVM, so the repository falls back to these.
        context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
    )

    @Test
    fun `a complete sync holds off the next automatic one`() = runTest {
        coEvery { jellyfinDao.getAllJellyfinSongsList() } returns emptyList()
        val repository = repository()
        assertThat(repository.isLoggedIn).isTrue()
        assertThat(repository.isFullSyncDue).isTrue()

        repository.syncAllPlaylistsAndSongs()

        assertThat(repository.isFullSyncDue).isFalse()
    }

    @Test
    fun `a sync whose shared library update fails stays due`() = runTest {
        coEvery { jellyfinDao.getAllJellyfinSongsList() } throws IllegalStateException("db closed")
        val repository = repository()
        assertThat(repository.isLoggedIn).isTrue()

        repository.syncAllPlaylistsAndSongs()

        assertThat(repository.isFullSyncDue).isTrue()
    }
}
