package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import com.lostf1sh.pixelplayeross.data.database.GenreSongCountRow
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SearchHistoryDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.ArtistEntity
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SearchFilterType
import com.lostf1sh.pixelplayeross.data.model.SearchResultItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.database.FavoritesDao
import com.lostf1sh.pixelplayeross.utils.GenreParser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import app.cash.turbine.test
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@ExperimentalCoroutinesApi
class MusicRepositoryImplTest {

    private lateinit var musicRepository: MusicRepositoryImpl
    private val mockMusicDao: MusicDao = mockk()
    private val mockSearchHistoryDao: SearchHistoryDao = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk()
    private val mockPlaylistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val mockLyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val mockSongRepository: SongRepository = mockk(relaxed = true)
    private val mockFavoritesDao: FavoritesDao = mockk(relaxed = true)
    private val mockArtistImageRepository: ArtistImageRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockArtistImageRepository.folderArtworkRevision } returns MutableStateFlow(0L)
        coEvery { mockArtistImageRepository.getFolderArtistImageUrl(any(), any()) } returns null
        coEvery { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.blockedDirectoriesFlow } returns flowOf(setOf("/dummy"))
        coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(false)
        val dummyArtists = listOf(
            ArtistEntity(101L, "ArtistName1", 5, null),
            ArtistEntity(102L, "ArtistName2", 3, null)
        )
        every { mockMusicDao.getAllArtistsRaw() } returns flowOf(dummyArtists)
        coEvery { mockMusicDao.getDistinctParentDirectories() } returns listOf("/music/folder1", "/music/folder2")
        every { mockMusicDao.getDistinctParentDirectoriesFlow() } returns
            flowOf(listOf("/music/folder1", "/music/folder2"))
        every { mockMusicDao.getAllSongArtistCrossRefs() } returns flowOf(emptyList())
        every { mockMusicDao.getAllSongs(any(), any()) } answers {
            println("getAllSongs called with: ${args[0]}, ${args[1]}")
            flowOf(emptyList())
        }
        every { mockMusicDao.getArtistsWithSongCountsFiltered(any(), any(), any()) } returns flowOf(emptyList())

        every { mockMusicDao.getSongs(any(), eq(true)) } answers {
            val allowedParams = firstArg<List<String>>()
            if (allowedParams.isEmpty()) flowOf(emptyList()) else flowOf(emptyList())
        }
        every { mockMusicDao.getSongs(any(), eq(false)) } returns flowOf(emptyList())
        every { mockMusicDao.getAlbums(any(), eq(true), any(), any()) } answers {
            val allowedParams = firstArg<List<String>>()
            if (allowedParams.isEmpty()) flowOf(emptyList()) else flowOf(emptyList())
        }
        every { mockMusicDao.getAlbums(any(), eq(false), any(), any()) } returns flowOf(emptyList())
        
        every { mockMusicDao.getArtists(any(), eq(true)) } answers {
             val allowedParams = firstArg<List<String>>()
             if (allowedParams.isNotEmpty()) {
                 flowOf(dummyArtists.filter { it.id == 101L })
             } else {
                 flowOf(emptyList())
             }
        }
        every { mockMusicDao.getArtists(any(), eq(false)) } returns flowOf(dummyArtists)



        musicRepository = MusicRepositoryImpl(
            context = mockContext,
            userPreferencesRepository = mockUserPreferencesRepository,
            playlistPreferencesRepository = mockPlaylistPreferencesRepository,
            searchHistoryDao = mockSearchHistoryDao,
            musicDao = mockMusicDao,
            lyricsRepository = mockLyricsRepository,
            songRepository = mockSongRepository,

            favoritesDao = mockFavoritesDao,
            artistImageRepository = mockArtistImageRepository,
            folderTreeBuilder = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `artist lists prefer folder art and restore remote art after invalidation`() = runTest(testDispatcher) {
        val revision = MutableStateFlow(0L)
        every { mockArtistImageRepository.folderArtworkRevision } returns revision
        every { mockMusicDao.getArtistsWithSongCountsFiltered(any(), any(), any()) } returns flowOf(
            listOf(ArtistEntity(101L, "Artist", 1, imageUrl = "https://example.com/remote.jpg"))
        )
        coEvery { mockArtistImageRepository.getFolderArtistImageUrl(101L, "Artist") } returns
            "/Music/Artist/artist.jpg"

        musicRepository.getArtists().test {
            assertEquals("/Music/Artist/artist.jpg", awaitItem().single().effectiveImageUrl)
            coEvery { mockArtistImageRepository.getFolderArtistImageUrl(101L, "Artist") } returns null
            revision.value++
            assertEquals("https://example.com/remote.jpg", awaitItem().single().effectiveImageUrl)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { mockMusicDao.updateArtistImageUrl(any(), any()) }
    }

    @Test
    fun `artist search keeps manually chosen photos ahead of folder art`() = runTest(testDispatcher) {
        every { mockMusicDao.searchArtists("Artist", any(), any()) } returns flowOf(
            listOf(ArtistEntity(101L, "Artist", 1, imageUrl = "https://example.com/remote.jpg", customImageUri = "/custom.jpg"))
        )
        assertEquals("/custom.jpg", musicRepository.searchArtists("Artist").first().single().effectiveImageUrl)
        coVerify(exactly = 0) { mockArtistImageRepository.getFolderArtistImageUrl(any(), any()) }
    }

    @Test
    fun `artist detail and song artists resolve folder images`() = runTest(testDispatcher) {
        val entity = ArtistEntity(101L, "Artist", 1, imageUrl = "https://example.com/remote.jpg")
        every { mockMusicDao.getArtistById(101L) } returns flowOf(entity)
        every { mockMusicDao.getArtistsForSong(5L) } returns flowOf(listOf(entity))
        coEvery { mockArtistImageRepository.getFolderArtistImageUrl(101L, "Artist") } returns "/Music/Artist/band.png"

        assertEquals("/Music/Artist/band.png", musicRepository.getArtistById(101L).first()?.effectiveImageUrl)
        assertEquals("/Music/Artist/band.png", musicRepository.getArtistsForSong(5L).first().single().effectiveImageUrl)
    }

    @Test
    fun `getAudioFiles returns songs from DAO, filtered by allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Pop", "/allowed/path/songA.mp3", "/allowed/path"),
            createSongEntity(2L, "Song B", "Artist 1", "Pop", "/forbidden/path/songB.mp3", "/forbidden/path"),
            createSongEntity(3L, "Song C", "Artist 2", "Rock", "/allowed/path/songC.mp3", "/allowed/path")
        )
        val allowedDirs = setOf("/allowed/path")

        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed/path") }
        every { mockMusicDao.getAllSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        every { mockMusicDao.getAllSongs(any(), eq(false)) } returns flowOf(songEntities)
        
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.blockedDirectoriesFlow } returns flowOf(setOf("/dummy"))
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        every { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(true)
        coEvery { mockMusicDao.getDistinctParentDirectories() } returns listOf("/allowed/path", "/forbidden/path")

        val result: List<Song> = musicRepository.getAudioFiles().first()

        assertEquals(2, result.size)
        assertEquals(listOf("1", "3"), result.map { it.id })
        verify { mockMusicDao.getAllSongs(any<List<String>>(), any<Boolean>()) }
    }

    @Test
    fun `getAudioFiles returns empty list if no allowed directories even before initial setup`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Pop", "/any/path/songA.mp3", "/any/path"),
            createSongEntity(2L, "Song B", "Artist 1", "Pop", "/other/path/songB.mp3", "/other/path")
        )



        val result = musicRepository.getAudioFiles().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAudioFiles returns empty list if initial setup done and no allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
             createSongEntity(1L, "Song A", "Artist 1", "Pop", "/allowed/path/songA.mp3", "/allowed/path")
        )
        val result = musicRepository.getAudioFiles().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAlbums returns albums from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "S1", "A1", "G", "/allowed/s1.mp3", "/allowed").copy(albumId = 201L),
            createSongEntity(2L, "S2", "A1", "G", "/allowed/s2.mp3", "/allowed").copy(albumId = 201L),
            createSongEntity(3L, "S3", "A2", "G", "/forbidden/s3.mp3", "/forbidden").copy(albumId = 202L),
            createSongEntity(4L, "S4", "A3", "G", "/allowed/s4.mp3", "/allowed").copy(albumId = 203L)
        )
        val allAlbumEntities = listOf(
            AlbumEntity(201L, "Album1", "ArtistName1", 101L, "art_uri1", 10, 0L, 2023),
            AlbumEntity(202L, "Album2", "ArtistName2", 102L, "art_uri2", 5, 0L, 2022),
            AlbumEntity(203L, "Album3", "ArtistName3", 103L, "art_uri3", 3, 0L, 2021)
        )
        val allowedDirs = setOf("/allowed")

        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed") }
        every { mockMusicDao.getSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        val expectedAlbums = allAlbumEntities.map { album ->
            when (album.id) {
                201L -> album.copy(songCount = 2)
                203L -> album.copy(songCount = 1)
                else -> album
            }
        }.filter { it.id == 201L || it.id == 203L }
        every { mockMusicDao.getAlbums(any(), eq(true), any(), any()) } returns flowOf(expectedAlbums)

        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)

        val result = musicRepository.getAlbums().first()

        assertEquals(2, result.size)
        assertEquals(listOf(201L, 203L), result.map { it.id })
        assertEquals(2, result.find { it.id == 201L }?.songCount)
        assertEquals(1, result.find { it.id == 203L }?.songCount)
    }

    @Test
    fun `getSongsForAlbum keeps every disc together in DAO order`() = runTest(testDispatcher) {
        fun albumTrack(id: Long, disc: Int, track: Int) =
            createSongEntity(id, "Disc $disc Track $track", "A1", "G", "/cloud/$id", "/cloud")
                .copy(discNumber = disc, trackNumber = track)
        every { mockMusicDao.getSongsByAlbumId(201L) } returns flowOf(
            listOf(
                albumTrack(1L, disc = 1, track = 1),
                albumTrack(2L, disc = 1, track = 2),
                albumTrack(3L, disc = 2, track = 1),
                albumTrack(4L, disc = 2, track = 2)
            )
        )

        val result = musicRepository.getSongsForAlbum(201L).first()

        assertEquals(
            listOf("Disc 1 Track 1", "Disc 1 Track 2", "Disc 2 Track 1", "Disc 2 Track 2"),
            result.map { it.title }
        )
    }

    @Test
    fun `getArtists returns artists from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "S1", "Artist1Name", "G", "/allowed/s1.mp3", "/allowed"),
            createSongEntity(2L, "S2", "Artist2Name", "G", "/forbidden/s2.mp3", "/forbidden"),
            createSongEntity(3L, "S3", "Artist1Name", "G", "/allowed/s3.mp3", "/allowed")
        )
        val allArtistEntities = listOf(
            ArtistEntity(101L, "Artist1Name", 20),
            ArtistEntity(102L, "Artist2Name", 10)
        )
        val allowedDirs = setOf("/allowed")

        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed") }
        every { mockMusicDao.getSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        val expectedArtists = allArtistEntities.map { 
            if (it.id == 101L) it.copy(trackCount = 2) else it 
        }.filter { it.id == 101L }
        every { mockMusicDao.getArtistsWithSongCountsFiltered(any(), eq(true), any()) } returns flowOf(expectedArtists)
        
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        every { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(true)


        val result = musicRepository.getArtists().first()

        assertEquals(1, result.size)
        assertEquals(101L, result.first().id)
        assertEquals(2, result.first().songCount)
    }

    @Test
    fun `getGenres deduplicates normalized genre ids`() = runTest(testDispatcher) {
        stubGenreCounts("Rock" to 2, " Rock " to 1, "rock" to 1)

        val result = musicRepository.getGenres().first()

        assertEquals(1, result.size)
        assertEquals("rock", result.first().id)
        assertEquals("Rock", result.first().name)
        assertEquals(4, result.first().songCount)
    }

    @Test
    fun `getGenres splits multi-genre values into single genres with summed counts`() = runTest(testDispatcher) {
        stubGenreCounts(
            "Alternative; Indie Rock" to 2,
            "Alternative" to 3,
            "House|Electronic" to 1,
            "Rap/Hip-Hop" to 1
        )

        val result = musicRepository.getGenres().first()

        assertEquals(
            listOf("Alternative" to 5, "Electronic" to 1, "House" to 1, "Indie Rock" to 2, "Rap/Hip-Hop" to 1),
            result.map { it.name to it.songCount }
        )
        assertEquals("indie_rock", result.single { it.name == "Indie Rock" }.id)
    }

    @Test
    fun `getGenres names a genre after its most used spelling`() = runTest(testDispatcher) {
        stubGenreCounts("hip hop" to 1, "Hip Hop" to 3)

        val result = musicRepository.getGenres().first()

        assertEquals("Hip Hop", result.single().name)
        assertEquals("hip_hop", result.single().id)
    }

    @Test
    fun `getGenres folds untagged and unknown values into one Unknown genre`() = runTest(testDispatcher) {
        stubGenreCounts(null to 2, "" to 1, "Unknown" to 1, " unknown " to 1, "Rock" to 1)

        val result = musicRepository.getGenres().first()

        assertEquals(listOf("rock", "unknown"), result.map { it.id })
        assertEquals("Unknown", result.last().name)
        assertEquals(5, result.last().songCount)
    }

    @Test
    fun `getGenres appends unknown only when needed`() = runTest(testDispatcher) {
        stubGenreCounts("Rock" to 1)

        val result = musicRepository.getGenres().first()

        assertEquals(1, result.size)
        assertTrue(result.none { it.id == "unknown" })
    }

    @Test
    fun `getMusicByGenre returns songs that list the genre as a whole genre`() = runTest(testDispatcher) {
        every { mockUserPreferencesRepository.mockGenresEnabledFlow } returns flowOf(false)
        val patternSlot = slot<String>()
        every { mockMusicDao.getSongsWithGenreLike(capture(patternSlot), any(), any()) } returns flowOf(
            listOf(
                createSongEntity(1L, "Both", "Artist", "Alternative; Indie Rock", "/m/1.mp3", "/m"),
                createSongEntity(2L, "Only", "Artist", "alternative", "/m/2.mp3", "/m"),
                createSongEntity(3L, "Other", "Artist", "Alternative Rock", "/m/3.mp3", "/m")
            )
        )

        val result = musicRepository.getMusicByGenre("Alternative").first()

        assertEquals(listOf("1", "2"), result.map { it.id })
        assertEquals("%Alternative%", patternSlot.captured)
    }

    @Test
    fun `getMusicByGenre keeps NUL separated lists only when they name the genre`() = runTest(testDispatcher) {
        every { mockUserPreferencesRepository.mockGenresEnabledFlow } returns flowOf(false)
        // The DAO returns every value holding a NUL, since SQLite LIKE cannot read past one.
        every { mockMusicDao.getSongsWithGenreLike(any(), any(), any()) } returns flowOf(
            listOf(
                createSongEntity(1L, "Listed second", "Artist", "Rock\u0000Pop", "/m/1.mp3", "/m"),
                createSongEntity(2L, "Other list", "Artist", "Rock\u0000Jazz", "/m/2.mp3", "/m"),
                createSongEntity(3L, "Plain", "Artist", "Pop", "/m/3.mp3", "/m")
            )
        )

        val result = musicRepository.getMusicByGenre("Pop").first()

        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test
    fun `getMusicByGenre for Unknown keeps only songs without any genre`() = runTest(testDispatcher) {
        every { mockUserPreferencesRepository.mockGenresEnabledFlow } returns flowOf(false)
        val noGenreCharactersSlot = slot<String>()
        every {
            mockMusicDao.getUnknownGenreSongCandidates(capture(noGenreCharactersSlot), any(), any())
        } returns flowOf(
            listOf(
                createSongEntity(1L, "Untagged", "Artist", null, "/m/1.mp3", "/m"),
                createSongEntity(2L, "Placeholder", "Artist", "<unknown>", "/m/2.mp3", "/m"),
                createSongEntity(3L, "Tagged", "Artist", "Unknown Pleasures; Post-Punk", "/m/3.mp3", "/m"),
                createSongEntity(4L, "Tab and separators", "Artist", ";\t|", "/m/4.mp3", "/m"),
                createSongEntity(5L, "Ideographic space", "Artist", "\u3000", "/m/5.mp3", "/m"),
                createSongEntity(6L, "NUL placeholder", "Artist", "\u0000Unknown", "/m/6.mp3", "/m"),
                createSongEntity(7L, "NUL list", "Artist", "Rock\u0000Pop", "/m/7.mp3", "/m")
            )
        )

        val result = musicRepository.getMusicByGenre("unknown").first()

        assertEquals(listOf("1", "2", "4", "5", "6"), result.map { it.id })
        assertEquals(GenreParser.noGenreCharacters, noGenreCharactersSlot.captured)
    }

    private fun stubGenreCounts(vararg rows: Pair<String?, Int>) {
        every {
            mockMusicDao.getGenreSongCounts(any<List<String>>(), any<Boolean>())
        } returns flowOf(rows.map { (genre, count) -> GenreSongCountRow(genre, count) })
    }

    @Nested
    @DisplayName("Search filters")
    inner class SearchFilters {
        private val auroraSongs = listOf(
            createSongEntity(1L, "Afterglow", "Aurora Fields", "Indie Rock", "/music/a/afterglow.mp3", "/music/a"),
            createSongEntity(2L, "Aurora Borealis", "Neon Duo", "Electronic", "/music/n/aurora.mp3", "/music/n")
        )

        @BeforeEach
        fun stubSearchSources() {
            every { mockUserPreferencesRepository.minTracksPerAlbumFlow } returns flowOf(1)
            every { mockMusicDao.searchSongsLimited("aurora", any(), any(), any()) } returns flowOf(auroraSongs)
            every { mockMusicDao.searchAlbums("aurora", any<List<String>>(), any<Boolean>(), any<Int>()) } returns
                flowOf(listOf(AlbumEntity(201L, "Low Tide", "Aurora Fields", 101L, null, 3, 0L, 2021)))
            every { mockMusicDao.searchArtists("aurora", any(), any()) } returns
                flowOf(listOf(ArtistEntity(101L, "Aurora Fields", 8, null)))
            every { mockPlaylistPreferencesRepository.userPlaylistsFlow } returns flowOf(
                listOf(
                    Playlist(id = "p1", name = "Aurora mornings", songIds = listOf("1")),
                    Playlist(id = "p2", name = "Gym", songIds = listOf("2"))
                )
            )
        }

        @Test
        fun `songs filter keeps the songs All lists for an artist match`() = runTest(testDispatcher) {
            val all = musicRepository.searchAll("aurora", SearchFilterType.ALL).first()

            val songs = musicRepository.searchAll("aurora", SearchFilterType.SONGS).first()

            assertEquals(all.filterIsInstance<SearchResultItem.SongItem>(), songs)
            assertEquals(listOf("1", "2"), songs.map { (it as SearchResultItem.SongItem).song.id })
        }

        @Test
        fun `each type filter returns exactly its section of the All results`() = runTest(testDispatcher) {
            val all = musicRepository.searchAll("aurora", SearchFilterType.ALL).first()

            val albums = musicRepository.searchAll("aurora", SearchFilterType.ALBUMS).first()
            val artists = musicRepository.searchAll("aurora", SearchFilterType.ARTISTS).first()
            val playlists = musicRepository.searchAll("aurora", SearchFilterType.PLAYLISTS).first()

            assertEquals(listOf(201L), albums.map { (it as SearchResultItem.AlbumItem).album.id })
            assertEquals(all.filterIsInstance<SearchResultItem.AlbumItem>(), albums)
            assertEquals(listOf(101L), artists.map { (it as SearchResultItem.ArtistItem).artist.id })
            assertEquals(all.filterIsInstance<SearchResultItem.ArtistItem>(), artists)
            assertEquals(listOf("p1"), playlists.map { (it as SearchResultItem.PlaylistItem).playlist.id })
            assertEquals(all.filterIsInstance<SearchResultItem.PlaylistItem>(), playlists)
        }
    }

    @Nested
    @DisplayName("Search History Functions")
    inner class SearchHistoryFunctions {
        @Test
        fun `addSearchHistoryItem calls dao methods`() = runTest {
            val query = "test query"
            coEvery { mockSearchHistoryDao.deleteByQuery(query) } just runs
            coEvery { mockSearchHistoryDao.insert(any()) } just runs

            musicRepository.addSearchHistoryItem(query)

            coVerifyOrder {
                mockSearchHistoryDao.deleteByQuery(query)
                mockSearchHistoryDao.insert(any())
            }
        }
    }

    private fun createSongEntity(
        id: Long,
        title: String,
        artistName: String,
        genre: String?,
        filePath: String,
        parentDirectoryPath: String
    ): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artistName = artistName,
            artistId = 101L,
            albumName = "Album1",
            albumId = 201L,
            contentUriString = "uri_$id",
            albumArtUriString = "art_$id",
            duration = 180,
            genre = genre,
            filePath = filePath,
            parentDirectoryPath = parentDirectoryPath,
            year = 2023,
            trackNumber = 1
        )
    }
}
