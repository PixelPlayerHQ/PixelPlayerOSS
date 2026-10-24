package com.lostf1sh.pixelplayeross.data.navidrome

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.NavidromeDao
import com.lostf1sh.pixelplayeross.data.database.NavidromeSongEntity
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

class NavidromeRepositoryUnifiedSyncTest {

    private val navidromeDao: NavidromeDao = mockk()
    private val musicDao: MusicDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val syncedSongs = slot<List<SongEntity>>()

    private fun repository(): NavidromeRepository {
        every { userPreferencesRepository.groupByAlbumArtistFlow } returns flowOf(true)
        coEvery { musicDao.getAllNavidromeSongIds() } returns emptyList()
        coEvery {
            musicDao.incrementalSyncMusicData(capture(syncedSongs), any(), any(), any(), any())
        } just Runs
        return NavidromeRepository(
            api = mockk(relaxed = true),
            dao = navidromeDao,
            musicDao = musicDao,
            playlistPreferencesRepository = mockk(relaxed = true),
            userPreferencesRepository = userPreferencesRepository,
            context = mockk(relaxed = true)
        )
    }

    private fun cachedSong(navidromeId: String, title: String, track: Int, disc: Int) =
        NavidromeSongEntity(
            id = "${NavidromeRepository.LIBRARY_PLAYLIST_ID}_$navidromeId",
            navidromeId = navidromeId,
            playlistId = NavidromeRepository.LIBRARY_PLAYLIST_ID,
            title = title,
            artist = "Artist",
            artistId = "artist-1",
            albumArtist = "Artist",
            album = "Double Album",
            albumId = "album-1",
            coverArtId = "cover-1",
            duration = 180_000L,
            trackNumber = track,
            discNumber = disc,
            year = 2016,
            genre = "House",
            bitRate = 320,
            mimeType = "audio/mpeg",
            suffix = "mp3",
            path = "Artist/Double Album/$navidromeId.mp3",
            dateAdded = 1_700_000_000_000L
        )

    @Test
    fun `unified library sync keeps disc numbers of multi disc albums`() = runTest {
        coEvery { navidromeDao.getAllNavidromeSongsList() } returns listOf(
            cachedSong("d1t1", "Disc 1 Track 1", track = 1, disc = 1),
            cachedSong("d2t1", "Disc 2 Track 1", track = 1, disc = 2),
            cachedSong("d1t2", "Disc 1 Track 2", track = 2, disc = 1),
            cachedSong("d2t2", "Disc 2 Track 2", track = 2, disc = 2)
        )

        repository().syncUnifiedLibrarySongsFromNavidrome()

        val discAndTrackByTitle = syncedSongs.captured.associate {
            it.title to (it.discNumber to it.trackNumber)
        }
        assertThat(discAndTrackByTitle).containsExactly(
            "Disc 1 Track 1", 1 to 1,
            "Disc 2 Track 1", 2 to 1,
            "Disc 1 Track 2", 1 to 2,
            "Disc 2 Track 2", 2 to 2
        )
        assertThat(syncedSongs.captured.map { it.sourceType }.distinct())
            .containsExactly(SourceType.NAVIDROME)
    }

    @Test
    fun `unified library sync stores no disc when the server reports none`() = runTest {
        coEvery { navidromeDao.getAllNavidromeSongsList() } returns listOf(
            cachedSong("single", "Single", track = 1, disc = 0)
        )

        repository().syncUnifiedLibrarySongsFromNavidrome()

        assertThat(syncedSongs.captured.single().discNumber).isNull()
    }
}
