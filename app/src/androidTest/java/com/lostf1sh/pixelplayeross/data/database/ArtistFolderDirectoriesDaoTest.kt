package com.lostf1sh.pixelplayeross.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistFolderDirectoriesDaoTest {
    private lateinit var database: PixelPlayerDatabase
    private lateinit var musicDao: MusicDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PixelPlayerDatabase::class.java
        )
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        musicDao = database.musicDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun returnsDistinctLocalDirectoriesForPrimaryAlbumAndGuestArtists() = runTest {
        insertArtistRolesFixture()

        assertEquals(
            listOf("/music/Album A", "/music/Album B", "/music/Album C"),
            musicDao.getLocalArtistDirectories(ARTIST_ID, limit = 64)
        )
    }

    @Test
    fun appliesLimitAfterDeduplicationAndExcludingRemoteAndEmptyPaths() = runTest {
        insertArtistRolesFixture()

        assertEquals(
            listOf("/music/Album A", "/music/Album B"),
            musicDao.getLocalArtistDirectories(ARTIST_ID, limit = 2)
        )
    }

    private suspend fun insertArtistRolesFixture() {
        val songs = listOf(
            // The same folder is reachable through every UNION branch and multiple tracks.
            song(1L, "/music/Album A", artistId = ARTIST_ID, albumArtistId = ARTIST_ID),
            song(2L, "/music/Album A", artistId = ARTIST_ID),
            song(3L, "/music/Album B", albumArtistId = ARTIST_ID),
            song(4L, "/music/Album C"),
            song(5L, "/aaa-unrelated"),
            song(6L, "", artistId = ARTIST_ID, albumArtistId = ARTIST_ID),
            // Plausible cached paths on cloud tracks must be excluded in each UNION branch.
            song(-1L, "/aaa-cloud-primary", artistId = ARTIST_ID, sourceType = SourceType.NAVIDROME),
            song(-2L, "/aaa-cloud-album", albumArtistId = ARTIST_ID, sourceType = SourceType.JELLYFIN),
            song(-3L, "/aaa-cloud-guest", sourceType = SourceType.NAVIDROME)
        )
        musicDao.insertMusicData(
            songs = songs,
            albums = listOf(
                AlbumEntity(
                    id = ALBUM_ID,
                    title = "Fixture Album",
                    artistName = "Other Artist",
                    artistId = OTHER_ARTIST_ID,
                    albumArtUriString = null,
                    songCount = songs.size,
                    dateAdded = 0L,
                    year = 2026
                )
            ),
            artists = listOf(
                ArtistEntity(id = ARTIST_ID, name = "Artist", trackCount = 0),
                ArtistEntity(id = OTHER_ARTIST_ID, name = "Other Artist", trackCount = 0)
            )
        )
        musicDao.insertSongArtistCrossRefs(
            listOf(
                SongArtistCrossRef(songId = 1L, artistId = ARTIST_ID, isPrimary = true),
                SongArtistCrossRef(songId = 4L, artistId = ARTIST_ID, isPrimary = false),
                SongArtistCrossRef(songId = 5L, artistId = OTHER_ARTIST_ID, isPrimary = true),
                SongArtistCrossRef(songId = 6L, artistId = ARTIST_ID, isPrimary = true),
                SongArtistCrossRef(songId = -3L, artistId = ARTIST_ID, isPrimary = false)
            )
        )
    }

    private fun song(
        id: Long,
        directory: String,
        artistId: Long = OTHER_ARTIST_ID,
        albumArtistId: Long = OTHER_ARTIST_ID,
        sourceType: Int = SourceType.LOCAL
    ): SongEntity = SongEntity(
        id = id,
        title = "Track $id",
        artistName = if (artistId == ARTIST_ID) "Artist" else "Other Artist",
        artistId = artistId,
        albumArtistId = albumArtistId,
        albumName = "Fixture Album",
        albumId = ALBUM_ID,
        contentUriString = when (sourceType) {
            SourceType.NAVIDROME -> "navidrome://track-$id"
            SourceType.JELLYFIN -> "jellyfin://track-$id"
            else -> "content://media/external/audio/media/$id"
        },
        albumArtUriString = null,
        duration = 180_000L,
        genre = null,
        filePath = "$directory/track-$id.flac",
        parentDirectoryPath = directory,
        sourceType = sourceType
    )

    private companion object {
        const val ARTIST_ID = 42L
        const val OTHER_ARTIST_ID = 99L
        const val ALBUM_ID = 201L
    }
}
