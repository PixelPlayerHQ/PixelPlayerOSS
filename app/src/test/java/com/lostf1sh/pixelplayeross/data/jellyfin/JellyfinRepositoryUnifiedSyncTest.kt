package com.lostf1sh.pixelplayeross.data.jellyfin

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.JellyfinDao
import com.lostf1sh.pixelplayeross.data.database.JellyfinSongEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SourceType
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinApiService
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class JellyfinRepositoryUnifiedSyncTest {

    private val jellyfinDao: JellyfinDao = mockk()
    private val musicDao: MusicDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val syncedSongs = slot<List<SongEntity>>()
    private val groupByAlbumArtist = MutableStateFlow(true)
    private val api: JellyfinApiService = mockk(relaxed = true)

    @Volatile
    private var unifiedSongCount = 0

    // The encrypted preferences are unavailable on the JVM, so the repository falls back to
    // plain ones; an in-memory map stands in for them.
    private val prefs = InMemorySharedPreferences()

    private fun repository(): JellyfinRepository {
        every { userPreferencesRepository.groupByAlbumArtistFlow } returns groupByAlbumArtist
        coEvery { musicDao.getAllJellyfinSongIds() } returns emptyList()
        coEvery { musicDao.getJellyfinSongCount() } answers { unifiedSongCount }
        coEvery {
            musicDao.incrementalSyncMusicData(capture(syncedSongs), any(), any(), any(), any())
        } answers { unifiedSongCount = syncedSongs.captured.size }
        return JellyfinRepository(
            api = api,
            dao = jellyfinDao,
            musicDao = musicDao,
            playlistPreferencesRepository = mockk(relaxed = true) {
                every { userPlaylistsFlow } returns flowOf(emptyList())
            },
            userPreferencesRepository = userPreferencesRepository,
            context = mockk<Context>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns prefs
            }
        )
    }

    private fun cachedSong(jellyfinId: String, title: String, track: Int, disc: Int) =
        JellyfinSongEntity(
            id = "__library___$jellyfinId",
            jellyfinId = jellyfinId,
            playlistId = "__library__",
            title = title,
            artist = "Original Broadway Cast",
            artistId = null,
            albumArtist = "Lin-Manuel Miranda",
            album = "Hamilton",
            albumId = "album-hamilton",
            duration = 180_000L,
            trackNumber = track,
            discNumber = disc,
            year = 2015,
            genre = "Musical",
            bitRate = 320,
            mimeType = "audio/flac",
            path = "/music/Hamilton/$jellyfinId.flac",
            dateAdded = 1_700_000_000_000L
        )

    @Test
    fun `unified library sync keeps disc numbers of multi disc albums`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("d1t1", "Alexander Hamilton", track = 1, disc = 1),
            cachedSong("d2t1", "What'd I Miss", track = 1, disc = 2),
            cachedSong("d1t2", "Aaron Burr, Sir", track = 2, disc = 1),
            cachedSong("d2t2", "Cabinet Battle #1", track = 2, disc = 2)
        )

        repository().syncUnifiedLibrarySongsFromJellyfin()

        val discAndTrackByTitle = syncedSongs.captured.associate {
            it.title to (it.discNumber to it.trackNumber)
        }
        assertThat(discAndTrackByTitle).containsExactly(
            "Alexander Hamilton", 1 to 1,
            "What'd I Miss", 2 to 1,
            "Aaron Burr, Sir", 1 to 2,
            "Cabinet Battle #1", 2 to 2
        )
        assertThat(syncedSongs.captured.map { it.sourceType }.distinct())
            .containsExactly(SourceType.JELLYFIN)
    }

    @Test
    fun `unified library sync stores no disc when the server reports none`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("single", "Single", track = 1, disc = 0)
        )

        repository().syncUnifiedLibrarySongsFromJellyfin()

        assertThat(syncedSongs.captured.single().discNumber).isNull()
    }

    @Test
    fun `startup rebuild is skipped while nothing it reads has changed`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("d1t1", "Alexander Hamilton", track = 1, disc = 1)
        )
        val repository = repository()

        repository.syncUnifiedLibrarySongsFromJellyfinIfChanged()
        repository.syncUnifiedLibrarySongsFromJellyfinIfChanged()

        coVerify(exactly = 1) { jellyfinDao.getDistinctJellyfinSongs(any()) }
        coVerify(exactly = 1) { musicDao.incrementalSyncMusicData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `startup rebuild runs after a playlist sync changed the cached songs`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("d1t1", "Alexander Hamilton", track = 1, disc = 1)
        )
        coEvery { jellyfinDao.deleteSongsByPlaylist(any()) } just Runs
        coEvery { jellyfinDao.getPlaylistById(any()) } returns null
        coEvery { api.getPlaylistItems("playlist") } returns Result.success(emptyList())
        val repository = repository()
        repository.syncUnifiedLibrarySongsFromJellyfin()

        // A playlist sync writes the cache without rebuilding the unified library itself.
        repository.syncPlaylistSongs("playlist")
        repository.syncUnifiedLibrarySongsFromJellyfinIfChanged()

        coVerify(exactly = 2) { musicDao.incrementalSyncMusicData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `startup rebuild runs when the album artist grouping changed`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("d1t1", "Alexander Hamilton", track = 1, disc = 1)
        )
        val repository = repository()
        repository.syncUnifiedLibrarySongsFromJellyfin()

        groupByAlbumArtist.value = false
        repository.syncUnifiedLibrarySongsFromJellyfinIfChanged()

        coVerify(exactly = 2) { musicDao.incrementalSyncMusicData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `startup rebuild runs when Jellyfin songs left the unified library`() = runTest {
        coEvery { jellyfinDao.getDistinctJellyfinSongs(any()) } returns listOf(
            cachedSong("d1t1", "Alexander Hamilton", track = 1, disc = 1)
        )
        val repository = repository()
        repository.syncUnifiedLibrarySongsFromJellyfin()

        unifiedSongCount = 0
        repository.syncUnifiedLibrarySongsFromJellyfinIfChanged()

        coVerify(exactly = 2) { musicDao.incrementalSyncMusicData(any(), any(), any(), any(), any()) }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?): String? =
            values[key] as String? ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            values[key] as Set<String>? ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as Boolean? ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values }
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun remove(key: String) = apply { changes[key] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) values.clear()
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
        }
    }
}
