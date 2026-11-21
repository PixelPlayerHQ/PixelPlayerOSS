package com.lostf1sh.pixelplayeross.data.jellyfin

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.FavoritesDao
import com.lostf1sh.pixelplayeross.data.database.FavoritesEntity
import com.lostf1sh.pixelplayeross.data.database.JellyfinDao
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongIdUriMapping
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinApiService
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class JellyfinFavoritesSyncTest {

    @Test
    fun `initial sync merges local and remote favorites without deleting either side`() {
        val diff = computeJellyfinFavoriteSyncDiff(
            knownJellyfinIds = setOf("a", "b", "c", "d"),
            localFavorites = setOf("a", "b"),
            remoteFavorites = setOf("b", "c", "outside-library"),
            baseline = null
        )

        assertThat(diff.addLocally).containsExactly("c")
        assertThat(diff.removeLocally).isEmpty()
        assertThat(diff.addRemotely).containsExactly("a")
        assertThat(diff.removeRemotely).isEmpty()
        assertThat(diff.resultingFavorites).containsExactly("a", "b", "c")
    }

    @Test
    fun `subsequent 3-way sync propagates remote and local additions and removals`() {
        val diff = computeJellyfinFavoriteSyncDiff(
            knownJellyfinIds = setOf("keep", "rem-add", "rem-del", "loc-add", "loc-del"),
            localFavorites = setOf("keep", "rem-del", "loc-add"),
            remoteFavorites = setOf("keep", "rem-add", "loc-del"),
            baseline = setOf("keep", "rem-del", "loc-del")
        )

        assertThat(diff.addLocally).containsExactly("rem-add")
        assertThat(diff.removeLocally).containsExactly("rem-del")
        assertThat(diff.addRemotely).containsExactly("loc-add")
        assertThat(diff.removeRemotely).containsExactly("loc-del")
        assertThat(diff.resultingFavorites).containsExactly("keep", "rem-add", "loc-add")
    }

    @Test
    fun `protected pending ids are not overwritten by remote state`() {
        val diff = computeJellyfinFavoriteSyncDiff(
            knownJellyfinIds = setOf("offline-add", "normal-del"),
            localFavorites = setOf("offline-add", "normal-del"),
            remoteFavorites = emptySet(),
            baseline = setOf("offline-add", "normal-del"),
            protectedPendingIds = setOf("offline-add")
        )

        assertThat(diff.removeLocally).containsExactly("normal-del")
        assertThat(diff.resultingFavorites).containsExactly("offline-add")
    }

    @Test
    fun `pushFavoriteChange queues offline retry and syncFavorites flushes it`() = runTest {
        val syncEnabled = MutableStateFlow(true)
        val storedPrefs = mutableMapOf<String, Any?>(
            "server_url" to "https://jellyfin.example.com",
            "username" to "listener",
            "access_token" to "token",
            "user_id" to "user-1",
        )
        val prefs = mapBackedPrefs(storedPrefs)
        val remoteFavorites = mutableSetOf("jf-2")
        var markCalls = 0
        val api = mockk<JellyfinApiService>(relaxed = true) {
            coEvery { markFavorite("jf-1") } answers {
                markCalls++
                if (markCalls == 1) {
                    Result.failure(Exception("offline"))
                } else {
                    remoteFavorites.add("jf-1")
                    Result.success(Unit)
                }
            }
            coEvery { getFavoriteAudioIds(any(), any()) } answers {
                Result.success(remoteFavorites.size to remoteFavorites.toList())
            }
        }
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)
        val localFavorites = mutableSetOf(-101L)
        coEvery { favoritesDao.getFavoriteSongIdsOnce() } answers { localFavorites.toList() }
        coEvery { favoritesDao.setFavorite(any()) } answers {
            localFavorites.add(firstArg<FavoritesEntity>().songId)
        }
        val musicDao = mockk<MusicDao>(relaxed = true) {
            coEvery { getAllJellyfinSongUriMappings() } returns listOf(
                SongIdUriMapping(-101L, "jellyfin://jf-1"),
                SongIdUriMapping(-102L, "jellyfin://jf-2")
            )
        }
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { jellyfinSelectedLibraryIdsFlow } returns flowOf(emptySet())
            every { jellyfinSyncFavoritesFlow } returns syncEnabled
            every { groupByAlbumArtistFlow } returns flowOf(true)
        }

        val repo = JellyfinRepository(
            api = api,
            dao = mockk<JellyfinDao>(relaxed = true),
            musicDao = musicDao,
            favoritesDao = favoritesDao,
            playlistPreferencesRepository = mockk(relaxed = true),
            userPreferencesRepository = userPrefs,
            context = mockk<Context>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns prefs
            }
        )

        // First push fails offline -> queued for retry.
        repo.pushFavoriteChange("jf-1", isFavorite = true)
        @Suppress("UNCHECKED_CAST")
        assertThat(storedPrefs["pending_favorite_adds"] as Set<String>).containsExactly("jf-1")

        // Next sync flushes pending add and pulls remote favorite jf-2 locally.
        val result = repo.syncFavorites()
        assertThat(result.isSuccess).isTrue()
        assertThat(localFavorites).containsExactly(-101L, -102L)
        coVerify(exactly = 2) { api.markFavorite("jf-1") }
    }

    private fun mapBackedPrefs(map: MutableMap<String, Any?>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers {
            map[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            map[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putStringSet(any(), any()) } answers {
            map[firstArg()] = secondArg<Set<String>?>()?.toSet()
            editor
        }
        every { editor.remove(any()) } answers {
            map.remove(firstArg())
            editor
        }
        every { editor.clear() } answers {
            map.clear()
            editor
        }
        return mockk(relaxed = true) {
            every { getString(any(), any()) } answers { map[firstArg()] as? String ?: secondArg() }
            every { getBoolean(any(), any()) } answers { map[firstArg()] as? Boolean ?: secondArg() }
            @Suppress("UNCHECKED_CAST")
            every { getStringSet(any(), any()) } answers {
                (map[firstArg()] as? Set<String>) ?: secondArg()
            }
            every { edit() } returns editor
        }
    }
}
