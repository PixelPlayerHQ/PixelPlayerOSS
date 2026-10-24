package com.lostf1sh.pixelplayeross.data.jellyfin

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.JellyfinDao
import com.lostf1sh.pixelplayeross.data.database.JellyfinSongEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SourceType
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class JellyfinRepositoryUnifiedSyncTest {

    private val jellyfinDao: JellyfinDao = mockk()
    private val musicDao: MusicDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val syncedSongs = slot<List<SongEntity>>()

    private fun repository(): JellyfinRepository {
        every { userPreferencesRepository.groupByAlbumArtistFlow } returns flowOf(true)
        coEvery { musicDao.getAllJellyfinSongIds() } returns emptyList()
        coEvery {
            musicDao.incrementalSyncMusicData(capture(syncedSongs), any(), any(), any(), any())
        } just Runs
        return JellyfinRepository(
            api = mockk(relaxed = true),
            dao = jellyfinDao,
            musicDao = musicDao,
            playlistPreferencesRepository = mockk(relaxed = true),
            userPreferencesRepository = userPreferencesRepository,
            context = mockk(relaxed = true)
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
        coEvery { jellyfinDao.getAllJellyfinSongsList() } returns listOf(
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
        coEvery { jellyfinDao.getAllJellyfinSongsList() } returns listOf(
            cachedSong("single", "Single", track = 1, disc = 0)
        )

        repository().syncUnifiedLibrarySongsFromJellyfin()

        assertThat(syncedSongs.captured.single().discNumber).isNull()
    }
}
