package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.LyricsEntity
import com.lostf1sh.pixelplayeross.data.database.LyricsDao
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test

class LyricsRepositoryImplTest {

    @Test
    fun getLyrics_returnsSongLyricsBeforeNeedingStorageRead() = runTest {
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "12",
            title = "Track",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = "[00:01.00]Hello again",
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.EMBEDDED_FIRST)

        assertThat(lyrics).isNotNull()
        assertThat(lyrics!!.areFromRemote).isFalse()
        assertThat(lyrics.synced).isNotEmpty()
        assertThat(lyrics.synced!!.first().line).isEqualTo("Hello again")
    }

    @Test
    fun getLyrics_apiFirst_usesStoredLyricsBeforeCallingLrcLib() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "45",
            title = "Already Here",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = "These lyrics are already saved",
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.API_FIRST)

        assertThat(lyrics).isNotNull()
        assertThat(lyrics!!.plain).containsExactly("These lyrics are already saved")
        assertThat(lyrics.areFromRemote).isFalse()
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getLyrics(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFromRemote_returnsStoredLyricsWithoutCallingApi() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(77L) } returns LyricsEntity(
            songId = 77L,
            content = "[00:01.00]Stored line",
            isSynced = true,
            source = "manual"
        )
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "77",
            title = "Stored Track",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = null,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        val (lyrics, rawLyrics) = result.getOrThrow()
        assertThat(rawLyrics).isEqualTo("[00:01.00]Stored line")
        assertThat(lyrics.synced).isNotEmpty()
        assertThat(lyrics.areFromRemote).isFalse()
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getLyrics(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFromRemote_rejectsDurationOnlySearchMatch() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(101L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(
            lrcResponse(
                name = "Completely Different Song",
                artistName = "Different Artist",
                duration = 180.0
            )
        )
        coEvery { apiService.getLyrics(any(), any(), any(), any()) } returns null

        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = testSong(
            id = "101",
            title = "Actual Song",
            artist = "Actual Artist",
            duration = 180_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { lyricsDao.insert(any()) }
    }

    @Test
    fun fetchFromRemote_rejectsOriginalLyricsForRemix() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        val originalSongLyrics = lrcResponse(
            name = "Midnight City",
            artistName = "M83",
            duration = 242.0
        )
        coEvery { lyricsDao.getLyrics(102L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(originalSongLyrics)
        coEvery { apiService.getLyrics(any(), any(), any(), any()) } returns originalSongLyrics

        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = testSong(
            id = "102",
            title = "Midnight City (Remix)",
            artist = "M83",
            path = "/music/Midnight City (Remix).mp3",
            duration = 242_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { lyricsDao.insert(any()) }
    }

    @Test
    fun fetchFromRemote_acceptsMatchingRemixVariant() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        val remixLyrics = lrcResponse(
            name = "Midnight City (Eric Prydz Remix)",
            artistName = "M83",
            duration = 242.0
        )
        coEvery { lyricsDao.getLyrics(103L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(remixLyrics)

        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = testSong(
            id = "103",
            title = "Midnight City (Eric Prydz Remix)",
            artist = "M83",
            path = "/music/Midnight City (Eric Prydz Remix).mp3",
            duration = 242_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().first.areFromRemote).isTrue()
        coVerify(exactly = 1) { lyricsDao.insert(any()) }
    }

    @Test
    fun fetchFromRemote_doesNotTreatArtistNameInFilePathAsVariant() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        val lyrics = lrcResponse(
            name = "Black Magic",
            artistName = "Little Mix",
            duration = 211.0
        )
        coEvery { lyricsDao.getLyrics(104L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(lyrics)

        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = testSong(
            id = "104",
            title = "Black Magic",
            artist = "Little Mix",
            path = "/music/Little Mix - Black Magic.mp3",
            duration = 211_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { lyricsDao.insert(any()) }
    }

    private fun testContext(filesDir: File = Files.createTempDirectory("pixelplay-lyrics-test").toFile()): Context {
        return mockk<Context>(relaxed = true) {
            every { this@mockk.filesDir } returns filesDir
        }
    }

    private fun testSong(
        id: String,
        title: String,
        artist: String,
        path: String = "",
        duration: Long
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = path,
            contentUriString = "",
            albumArtUriString = null,
            duration = duration,
            lyrics = null,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
    }

    private fun lrcResponse(
        name: String,
        artistName: String,
        duration: Double
    ): LrcLibResponse {
        return LrcLibResponse(
            id = name.hashCode(),
            name = name,
            artistName = artistName,
            albumName = "Album",
            duration = duration,
            plainLyrics = null,
            syncedLyrics = "[00:01.00]First line\n[00:05.00]Second line"
        )
    }
}
