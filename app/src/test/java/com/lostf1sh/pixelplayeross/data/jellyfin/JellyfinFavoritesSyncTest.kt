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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    @Test
    fun `unlike during in-flight initial sync is not resurrected locally and propagates remotely`() = runTest {
        val syncEnabled = MutableStateFlow(true)
        val storedPrefs = mutableMapOf<String, Any?>(
            "server_url" to "https://jellyfin.example.com",
            "username" to "listener",
            "access_token" to "token",
            "user_id" to "user-1",
        )
        val prefs = mapBackedPrefs(storedPrefs)
        val fetchStarted = CompletableDeferred<Unit>()
        val allowFetchToFinish = CompletableDeferred<Unit>()
        val remoteFavorites = mutableSetOf("jf-1", "jf-2")
        val api = mockk<JellyfinApiService>(relaxed = true) {
            coEvery { getFavoriteAudioIds(any(), any()) } coAnswers {
                fetchStarted.complete(Unit)
                allowFetchToFinish.await()
                Result.success(remoteFavorites.size to remoteFavorites.toList())
            }
            coEvery { unmarkFavorite("jf-1") } answers {
                remoteFavorites.remove("jf-1")
                Result.success(Unit)
            }
        }
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)
        val localFavorites = mutableSetOf(-101L)
        coEvery { favoritesDao.getFavoriteSongIdsOnce() } answers { localFavorites.toList() }
        coEvery { favoritesDao.setFavorite(any()) } answers {
            localFavorites.add(firstArg<FavoritesEntity>().songId)
        }
        coEvery { favoritesDao.removeFavorite(any()) } answers {
            localFavorites.remove(firstArg())
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

        val syncJob = launch(Dispatchers.Default) { repo.syncFavorites() }
        fetchStarted.await()

        // User unlikes jf-1 locally while the initial remote fetch is still in flight.
        val token = repo.withLocalFavoriteLock {
            val recorded = repo.recordLocalFavoriteMutation("jf-1", isFavorite = false)
            localFavorites.remove(-101L)
            recorded
        }
        val pushJob = launch(Dispatchers.Default) {
            repo.pushFavoriteChange("jf-1", isFavorite = false, recordedToken = token)
        }

        allowFetchToFinish.complete(Unit)
        syncJob.join()
        pushJob.join()

        // jf-1 must remain unliked locally AND removed remotely; jf-2 pulled locally.
        assertThat(localFavorites).containsExactly(-102L)
        assertThat(remoteFavorites).containsExactly("jf-2")
        coVerify(exactly = 1) { api.unmarkFavorite("jf-1") }
    }

    @Test
    fun `logout during in-flight failed push discards retry and never leaks to another account`() = runTest {
        val syncEnabled = MutableStateFlow(true)
        val storedPrefs = mutableMapOf<String, Any?>(
            "server_url" to "https://jellyfin.example.com",
            "username" to "account-a",
            "access_token" to "token-a",
            "user_id" to "user-a",
        )
        val prefs = mapBackedPrefs(storedPrefs)
        val pushStarted = CompletableDeferred<Unit>()
        val allowPushFailure = CompletableDeferred<Unit>()
        val api = mockk<JellyfinApiService>(relaxed = true) {
            coEvery { markFavorite("old-track") } coAnswers {
                pushStarted.complete(Unit)
                allowPushFailure.await()
                Result.failure(Exception("logged out mid-request"))
            }
            coEvery { authenticateByName(any(), any(), any()) } returns Result.success("token-b" to "user-b")
            coEvery { getFavoriteAudioIds(any(), any()) } returns Result.success(0 to emptyList())
        }
        val favoritesDao = mockk<FavoritesDao>(relaxed = true) {
            coEvery { getFavoriteSongIdsOnce() } returns emptyList()
        }
        val musicDao = mockk<MusicDao>(relaxed = true) {
            coEvery { getAllJellyfinSongUriMappings() } returns emptyList()
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

        val pushJob = launch(Dispatchers.Default) {
            repo.pushFavoriteChange("old-track", isFavorite = true)
        }
        pushStarted.await()

        val logoutJob = launch(Dispatchers.Default) { repo.logout() }
        // Wait until logout has invalidated the active session while push is still in flight.
        while (repo.isLoggedIn) {
            kotlinx.coroutines.yield()
        }
        allowPushFailure.complete(Unit)
        pushJob.join()
        logoutJob.join()

        assertThat(storedPrefs["pending_favorite_adds"]).isNull()

        // Logging in as another account and syncing must never replay account A's track.
        assertThat(repo.login("https://jellyfin.example.com", "account-b", "pw").isSuccess).isTrue()
        assertThat(repo.syncFavorites().isSuccess).isTrue()
        coVerify(exactly = 1) { api.markFavorite("old-track") }
    }

    @Test
    fun `delayed push captured for account A is dropped after switching to account B`() = runTest {
        val syncEnabled = MutableStateFlow(true)
        val storedPrefs = mutableMapOf<String, Any?>(
            "server_url" to "https://jellyfin.example.com",
            "username" to "account-a",
            "access_token" to "token-a",
            "user_id" to "user-a",
        )
        val prefs = mapBackedPrefs(storedPrefs)
        val api = mockk<JellyfinApiService>(relaxed = true) {
            coEvery { authenticateByName(any(), any(), any()) } returns Result.success("token-b" to "user-b")
        }
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { jellyfinSelectedLibraryIdsFlow } returns flowOf(emptySet())
            every { jellyfinSyncFavoritesFlow } returns syncEnabled
            every { groupByAlbumArtistFlow } returns flowOf(true)
        }
        val repo = JellyfinRepository(
            api = api,
            dao = mockk<JellyfinDao>(relaxed = true),
            musicDao = mockk<MusicDao>(relaxed = true),
            favoritesDao = mockk<FavoritesDao>(relaxed = true),
            playlistPreferencesRepository = mockk(relaxed = true),
            userPreferencesRepository = userPrefs,
            context = mockk<Context>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns prefs
            }
        )

        // Toggle happens under account A, before the coroutine runs pushFavoriteChange.
        val tokenFromAccountA = repo.recordLocalFavoriteMutation("account-a-track", isFavorite = true)
        assertThat(tokenFromAccountA).isNotNull()

        // User logs out of account A and logs into account B before the delayed push executes.
        repo.logout()
        assertThat(repo.login("https://jellyfin.example.com", "account-b", "pw").isSuccess).isTrue()

        // Delayed push must be ignored completely and never hit account B's server.
        repo.pushFavoriteChange("account-a-track", isFavorite = true, recordedToken = tokenFromAccountA)
        coVerify(exactly = 0) { api.markFavorite("account-a-track") }
        assertThat(storedPrefs["pending_favorite_adds"]).isNull()
    }

    @Test
    fun `cleared local mutation after cancelled write allows subsequent sync to pull song`() = runTest {
        val syncEnabled = MutableStateFlow(true)
        val storedPrefs = mutableMapOf<String, Any?>(
            "server_url" to "https://jellyfin.example.com",
            "username" to "listener",
            "access_token" to "token",
            "user_id" to "user-1",
        )
        val prefs = mapBackedPrefs(storedPrefs)
        val api = mockk<JellyfinApiService>(relaxed = true) {
            coEvery { getFavoriteAudioIds(any(), any()) } returns Result.success(1 to listOf("jf-1"))
        }
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)
        val localFavorites = mutableSetOf<Long>()
        coEvery { favoritesDao.getFavoriteSongIdsOnce() } answers { localFavorites.toList() }
        coEvery { favoritesDao.setFavorite(any()) } answers {
            localFavorites.add(firstArg<FavoritesEntity>().songId)
        }
        val musicDao = mockk<MusicDao>(relaxed = true) {
            coEvery { getAllJellyfinSongUriMappings() } returns listOf(
                SongIdUriMapping(-101L, "jellyfin://jf-1")
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

        val token = repo.recordLocalFavoriteMutation("jf-1", isFavorite = false)!!
        repo.clearLocalFavoriteMutation("jf-1", isFavorite = false, token = token)

        assertThat(repo.syncFavorites().isSuccess).isTrue()
        assertThat(localFavorites).containsExactly(-101L)
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
