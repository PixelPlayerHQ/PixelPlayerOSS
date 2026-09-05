package com.lostf1sh.pixelplayeross.data.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MusicDaoTest {

    private lateinit var musicDao: MusicDao
    private lateinit var db: PixelPlayerDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        musicDao = db.musicDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private fun createSongEntity(
        id: Long,
        title: String,
        artist: String,
        album: String,
        path: String,
        genre: String = "Pop",
        artistId: Long = 101L,
        albumId: Long = 201L,
        trackNumber: Int = 1,
        discNumber: Int? = null
    ): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artistName = artist,
            artistId = artistId,
            albumName = album,
            albumId = albumId,
            contentUriString = "uri_$id",
            albumArtUriString = "art_uri_$id",
            duration = 180000,
            genre = genre,
            filePath = path,
            parentDirectoryPath = path.substringBeforeLast("/"),
            year = 2023,
            trackNumber = trackNumber,
            discNumber = discNumber
        )
    }

    private suspend fun insertSongsWithParents(songs: List<SongEntity>) {
        val artists = songs
            .distinctBy(SongEntity::artistId)
            .map { song -> createArtistEntity(song.artistId, song.artistName) }
        val albums = songs
            .distinctBy(SongEntity::albumId)
            .map { song ->
                createAlbumEntity(
                    id = song.albumId,
                    title = song.albumName,
                    artistId = song.artistId,
                    artistName = song.artistName
                )
            }

        musicDao.insertMusicData(songs, albums, artists)
    }

    private suspend fun insertAlbumSortFixture(): Pair<List<Long>, List<Long>> {
        musicDao.insertArtists(listOf(createArtistEntity(101L, "Artist")))
        musicDao.insertAlbums(
            listOf(
                createAlbumEntity(201L, "Alpha"),
                createAlbumEntity(202L, "Beta")
            )
        )

        musicDao.insertSongs(
            listOf(
                createSongEntity(11L, "Zeta Track Two", "Artist", "Alpha", "/alpha/11.mp3", albumId = 201L, trackNumber = 2, discNumber = 1),
                createSongEntity(12L, "Track One", "Artist", "Alpha", "/alpha/12.mp3", albumId = 201L, trackNumber = 1, discNumber = 1),
                createSongEntity(13L, "Zulu Unknown", "Artist", "Alpha", "/alpha/13.mp3", albumId = 201L, trackNumber = 0, discNumber = 1),
                createSongEntity(14L, "Disc Two", "Artist", "Alpha", "/alpha/14.mp3", albumId = 201L, trackNumber = 1, discNumber = 2),
                createSongEntity(15L, "Track Three", "Artist", "Alpha", "/alpha/15.mp3", albumId = 201L, trackNumber = 3, discNumber = 0),
                createSongEntity(16L, "Alpha Track Two", "Artist", "Alpha", "/alpha/16.mp3", albumId = 201L, trackNumber = 2, discNumber = null),
                createSongEntity(17L, "Alpha Track Two", "Artist", "Alpha", "/alpha/17.mp3", albumId = 201L, trackNumber = 2, discNumber = null),
                createSongEntity(18L, "Alpha Unknown", "Artist", "Alpha", "/alpha/18.mp3", albumId = 201L, trackNumber = -1, discNumber = 1),
                createSongEntity(21L, "Beta Disc Two", "Artist", "Beta", "/beta/21.mp3", albumId = 202L, trackNumber = 1, discNumber = 2),
                createSongEntity(22L, "Beta Track Two", "Artist", "Beta", "/beta/22.mp3", albumId = 202L, trackNumber = 2, discNumber = 1),
                createSongEntity(23L, "Beta Track One", "Artist", "Beta", "/beta/23.mp3", albumId = 202L, trackNumber = 1, discNumber = 1)
            )
        )

        val alphaOrder = listOf(12L, 16L, 17L, 11L, 15L, 18L, 13L, 14L)
        val betaOrder = listOf(23L, 22L, 21L)
        return (alphaOrder + betaOrder) to (betaOrder + alphaOrder)
    }

    private suspend fun PagingSource<Int, SongEntity>.loadIds(): List<Long> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false
            )
        )
        return (result as PagingSource.LoadResult.Page<Int, SongEntity>).data.map(SongEntity::id)
    }

    private fun createAlbumEntity(
        id: Long,
        title: String,
        artistId: Long = 101L,
        artistName: String = "Artist"
    ): AlbumEntity {
        return AlbumEntity(
            id = id,
            title = title,
            artistName = artistName,
            artistId = artistId,
            albumArtUriString = "art_uri_$id",
            songCount = 5,
            dateAdded = 0L,
            year = 2023
        )
    }

    private fun createArtistEntity(id: Long, name: String): ArtistEntity {
        return ArtistEntity(id = id, name = name, trackCount = 10, imageUrl = null)
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetSongs() = runTest {
        val songList = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Album X", "/path/a/songA.mp3"),
            createSongEntity(
                2L,
                "Song B",
                "Artist 2",
                "Album Y",
                "/path/b/songB.mp3",
                genre = "Rock",
                artistId = 102L,
                albumId = 202L
            )
        )
        insertSongsWithParents(songList)

        val retrievedSongs = musicDao.getSongs(emptyList(), false).first()
        assertEquals(2, retrievedSongs.size)
        assertEquals("Song A", retrievedSongs[0].title)
        assertEquals("Song B", retrievedSongs[1].title)
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetAlbums() = runTest {
        val artists = listOf(createArtistEntity(101L, "Artist 1"))
        val albums = listOf(
            createAlbumEntity(201L, "Album X", artistName = "Artist 1"),
            createAlbumEntity(202L, "Album Y", artistName = "Artist 1")
        )
        val songs = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Album X", "/path/a/songA.mp3"),
            createSongEntity(2L, "Song B", "Artist 1", "Album X", "/path/a/songB.mp3")
        )
        musicDao.insertMusicData(songs, albums, artists)
        
        val retrievedAlbums = musicDao.getAlbums(emptyList(), false, 0, 1).first()
        
        assertEquals(1, retrievedAlbums.size)
        assertEquals("Album X", retrievedAlbums[0].title)
        assertEquals(2, retrievedAlbums[0].songCount)
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetArtists() = runTest {
        val artists = listOf(
            createArtistEntity(101L, "Artist 1"),
            createArtistEntity(102L, "Artist 2")
        )
        val albums = listOf(createAlbumEntity(201L, "Album X", artistName = "Artist 1"))
        val songs = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Album X", "/path/a/songA.mp3")
        )
        musicDao.insertMusicData(songs, albums, artists)

        val retrievedArtists = musicDao.getArtists(emptyList(), false).first()
        assertEquals(1, retrievedArtists.size)
        assertEquals("Artist 1", retrievedArtists[0].name)
    }

    @Test
    @Throws(Exception::class)
    fun insertMusicData_insertsNewWithoutClearingExisting() = runTest {
        val oldSong = createSongEntity(
            1L,
            "Old Song",
            "Old Artist",
            "Old Album",
            "/old/path/old.mp3",
            artistId = 1001L,
            albumId = 2001L
        )
        insertSongsWithParents(listOf(oldSong))

        val songs = listOf(
            createSongEntity(10L, "Song A", "Artist 1", "Album X", "/path/a/songA.mp3")
        )
        val albums = listOf(
            createAlbumEntity(201L, "Album X")
        )
        val artists = listOf(
            createArtistEntity(101L, "Artist 1")
        )

        musicDao.insertMusicData(songs, albums, artists)

        val oldSongRetrieved = musicDao.getSongById(1L).first()
        assertNotNull(oldSongRetrieved)
        
        val newSongRetrieved = musicDao.getSongById(10L).first()
        assertNotNull(newSongRetrieved)
    }

    @Test
    @Throws(Exception::class)
    fun searchSongs_returnsMatchingSongs() = runTest {
        val songs = listOf(
            createSongEntity(1L, "Cool Song", "Artist A", "Album X", "/p1/s1.mp3"),
            createSongEntity(
                2L,
                "Another Song",
                "Artist B",
                "Album Y",
                "/p2/s2.mp3",
                genre = "Rock",
                artistId = 102L,
                albumId = 202L
            ),
            createSongEntity(
                3L,
                "Coolest Song Ever",
                "Artist C",
                "Album Z",
                "/p3/s3.mp3",
                artistId = 103L,
                albumId = 203L
            )
        )
        insertSongsWithParents(songs)

        val results = musicDao.searchSongs("Cool", emptyList(), false).first()
        assertEquals(2, results.size)
        val titles = results.map { it.title }.sorted()
        assertEquals(listOf("Cool Song", "Coolest Song Ever"), titles)
    }

    @Test
    fun albumSort_ordersEveryLibraryQueryByDiscAndTrackWithinAlbum() = runTest {
        val (ascending, descending) = insertAlbumSortFixture()

        listOf(
            "song_album" to ascending,
            "song_album_desc" to descending
        ).forEach { (sortOrder, expectedIds) ->
            assertEquals(
                expectedIds,
                musicDao.getSongIdsSorted(emptyList(), false, sortOrder, 0)
            )
            assertEquals(
                expectedIds,
                musicDao.getSongsPage(emptyList(), false, sortOrder, 0, 100, 0).map(SongEntity::id)
            )
            assertEquals(
                expectedIds,
                musicDao.getSongsPaginated(emptyList(), false, sortOrder, 0).loadIds()
            )
        }
    }

    @Test
    fun albumSort_ordersEveryFavoriteQueryByDiscAndTrackWithinAlbum() = runTest {
        val (ascending, descending) = insertAlbumSortFixture()
        db.favoritesDao().insertAll(
            (ascending + descending)
                .distinct()
                .map { songId -> FavoritesEntity(songId = songId, timestamp = songId) }
        )

        listOf(
            "liked_album" to ascending,
            "liked_album_desc" to descending
        ).forEach { (sortOrder, expectedIds) ->
            assertEquals(
                expectedIds,
                musicDao.getFavoriteSongIdsSorted(emptyList(), false, sortOrder, 0)
            )
            assertEquals(
                expectedIds,
                musicDao.getFavoriteSongsPage(emptyList(), false, sortOrder, 0, 100, 0).map(SongEntity::id)
            )
            assertEquals(
                expectedIds,
                musicDao.getFavoriteSongsPaginated(emptyList(), false, sortOrder, 0).loadIds()
            )
        }
    }

    @Test
    fun albumDetail_ordersUnknownDiscAsDiscOneAndUnknownTracksLast() = runTest {
        val (ascending, _) = insertAlbumSortFixture()

        assertEquals(
            ascending.take(8),
            musicDao.getSongsByAlbumId(201L).first().map(SongEntity::id)
        )
    }
}
