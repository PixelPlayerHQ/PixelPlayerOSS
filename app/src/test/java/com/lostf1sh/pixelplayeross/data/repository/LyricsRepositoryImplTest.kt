package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.LyricsEntity
import com.lostf1sh.pixelplayeross.data.database.LyricsDao
import com.lostf1sh.pixelplayeross.data.database.GeminiLyricsDao
import com.lostf1sh.pixelplayeross.data.database.GeminiLyricsCacheEntity
import com.lostf1sh.pixelplayeross.data.model.GeminiLyricItem
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.network.gemini.GeminiLyricsClient
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibResponse
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test

class LyricsRepositoryImplTest {

    @Test
    fun getLyrics_returnsSongLyricsBeforeNeedingStorageRead() = runTest {
        val repository = createRepository(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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
        val repository = createRepository(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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
        val repository = createRepository(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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
    fun fetchFromRemote_whenExternalLyricsDisabled_doesNotCallLrcLib() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(105L) } returns null
        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = false)
        )
        val song = testSong(
            id = "105",
            title = "Remote Track",
            artist = "Remote Artist",
            duration = 180_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { lyricsDao.insert(any()) }
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

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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
    fun fetchFromRemote_acceptsRomanizedTitleMatchForNonLatinSong() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        val romanizedLyrics = lrcResponse(
            name = "Nabi",
            artistName = "IU",
            duration = 200.0
        )
        coEvery { lyricsDao.getLyrics(107L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(romanizedLyrics)

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
        )
        val song = testSong(
            id = "107",
            title = "나비",
            artist = "IU",
            duration = 200_000L
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { lyricsDao.insert(any()) }
    }

    @Test
    fun fetchFromRemote_prefersLrclibWordSyncedLyricsfile() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        val wordSynced = lrcResponse(
            name = "Word Song",
            artistName = "Word Artist",
            duration = 180.0,
            lyricsfile = """
                version: '1.0'
                lines:
                  - text: 'Hello world'
                    start_ms: 1000
                    end_ms: 3000
                    words:
                      - text: 'Hello '
                        start_ms: 1000
                        end_ms: 1800
                      - text: 'world'
                        start_ms: 1800
                        end_ms: 3000
            """.trimIndent(),
        )
        coEvery { lyricsDao.getLyrics(106L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(wordSynced)

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(),
        )
        val song = testSong(
            id = "106",
            title = "Word Song",
            artist = "Word Artist",
            duration = 180_000L,
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        val (lyrics, rawLyrics) = result.getOrThrow()
        assertThat(lyrics.synced!!.single().words).hasSize(2)
        assertThat(lyrics.synced!!.single().words!!.map { it.time })
            .containsExactly(1000, 1800).inOrder()
        assertThat(rawLyrics).contains("<00:01.00>Hello ")
        coVerify(exactly = 1) {
            lyricsDao.insert(match { it.isSynced && it.content == rawLyrics })
        }
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

        val repository = createRepository(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
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

    @Test
    fun enrichLyricsWithGemini_whenDisabled_returnsOriginalWithoutNetworkCall() = runTest {
        val geminiClient = mockk<GeminiLyricsClient>(relaxed = true)
        val geminiDao = mockk<GeminiLyricsDao>(relaxed = true)
        val userPrefs = userPreferencesRepository(
            geminiLyricsEnabled = false,
            geminiApiKey = "test-key"
        )
        val repository = createRepository(
            userPreferencesRepository = userPrefs,
            geminiLyricsClient = geminiClient,
            geminiLyricsDao = geminiDao
        )
        val song = testSong("1", "Song", "Artist", duration = 180_000L)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Line 1")))

        val enriched = repository.enrichLyricsWithGemini(song, lyrics)

        assertThat(enriched).isEqualTo(lyrics)
        coVerify(exactly = 0) { geminiClient.translateAndRomanize(any(), any(), any()) }
        coVerify(exactly = 0) { geminiDao.getCache(any()) }
    }

    @Test
    fun enrichLyricsWithGemini_whenEnabled_translatesAndCachesInDao() = runTest {
        val geminiClient = mockk<GeminiLyricsClient>(relaxed = true)
        val geminiDao = mockk<GeminiLyricsDao>(relaxed = true)
        coEvery { geminiDao.getCache(any()) } returns null
        coEvery { geminiClient.translateAndRomanize(any(), "test-key", any(), any()) } returns Result.success(
            listOf(
                GeminiLyricItem(
                    original = "日本語",
                    romanized = "Nihongo",
                    translated = "Japanese language"
                )
            )
        )
        val userPrefs = userPreferencesRepository(
            geminiLyricsEnabled = true,
            geminiApiKey = "test-key"
        )
        val repository = createRepository(
            userPreferencesRepository = userPrefs,
            geminiLyricsClient = geminiClient,
            geminiLyricsDao = geminiDao
        )
        val song = testSong("2", "Song", "Artist", duration = 180_000L)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "日本語")))

        val enriched = repository.enrichLyricsWithGemini(song, lyrics)

        assertThat(enriched.synced?.first()?.romanization).isEqualTo("Nihongo")
        assertThat(enriched.synced?.first()?.translation).isEqualTo("Japanese language")
        coVerify(exactly = 1) { geminiClient.translateAndRomanize(listOf("日本語"), "test-key", any(), any()) }
        coVerify(exactly = 1) { geminiDao.insertCache(any()) }
    }

    @Test
    fun enrichLyricsWithGemini_whenCached_returnsFromDaoWithoutNetworkCall() = runTest {
        val geminiClient = mockk<GeminiLyricsClient>(relaxed = true)
        val geminiDao = mockk<GeminiLyricsDao>(relaxed = true)
        val cachedJson = """[{"original":"Hello","romanized":"Hello","translated":"Bonjour"}]"""
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val textHash = md.digest("Hello".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        coEvery { geminiDao.getCache(any()) } returns GeminiLyricsCacheEntity(
            trackKey = "gemini_mock",
            originalTextHash = textHash,
            structuredJson = cachedJson
        )

        val userPrefs = userPreferencesRepository(
            geminiLyricsEnabled = true,
            geminiApiKey = "test-key"
        )
        val repository = createRepository(
            userPreferencesRepository = userPrefs,
            geminiLyricsClient = geminiClient,
            geminiLyricsDao = geminiDao
        )
        val song = testSong("3", "Song", "Artist", duration = 180_000L)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Hello")))

        val enriched = repository.enrichLyricsWithGemini(song, lyrics)

        assertThat(enriched.synced?.first()?.translation).isEqualTo("Bonjour")
        coVerify(exactly = 0) { geminiClient.translateAndRomanize(any(), any(), any()) }
    }

    @Test
    fun enrichLyricsWithGemini_onNetworkError_returnsOriginalGracefully() = runTest {
        val geminiClient = mockk<GeminiLyricsClient>(relaxed = true)
        val geminiDao = mockk<GeminiLyricsDao>(relaxed = true)
        coEvery { geminiDao.getCache(any()) } returns null
        coEvery { geminiClient.translateAndRomanize(any(), any()) } returns Result.failure(
            java.io.IOException("Network dropped")
        )

        val userPrefs = userPreferencesRepository(
            geminiLyricsEnabled = true,
            geminiApiKey = "test-key"
        )
        val repository = createRepository(
            userPreferencesRepository = userPrefs,
            geminiLyricsClient = geminiClient,
            geminiLyricsDao = geminiDao
        )
        val song = testSong("4", "Song", "Artist", duration = 180_000L)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Original text")))

        val enriched = repository.enrichLyricsWithGemini(song, lyrics)

        assertThat(enriched).isEqualTo(lyrics)
        coVerify(exactly = 0) { geminiDao.insertCache(any()) }
    }

    private fun testContext(filesDir: File = Files.createTempDirectory("pixelplayer-lyrics-test").toFile()): Context {
        return mockk<Context>(relaxed = true) {
            every { this@mockk.filesDir } returns filesDir
        }
    }

    private fun userPreferencesRepository(
        externalLyricsEnabled: Boolean = true,
        geminiLyricsEnabled: Boolean = false,
        geminiApiKey: String? = null,
        geminiModel: String = "gemini-3.5-flash-lite"
    ): UserPreferencesRepository {
        return mockk {
            every { this@mockk.externalLyricsEnabledFlow } returns flowOf(externalLyricsEnabled)
            every { this@mockk.geminiLyricsEnabledFlow } returns flowOf(geminiLyricsEnabled)
            every { this@mockk.geminiApiKeyFlow } returns flowOf(geminiApiKey)
            every { this@mockk.geminiModelFlow } returns flowOf(geminiModel)
        }
    }

    private fun createRepository(
        context: Context = mockk<Context>(relaxed = true),
        lrcLibApiService: LrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
        lyricsDao: LyricsDao = mockk<LyricsDao>(relaxed = true),
        okHttpClient: OkHttpClient = mockk<OkHttpClient>(relaxed = true),
        userPreferencesRepository: UserPreferencesRepository = userPreferencesRepository(),
        geminiLyricsClient: GeminiLyricsClient = mockk<GeminiLyricsClient>(relaxed = true),
        geminiLyricsDao: GeminiLyricsDao = mockk<GeminiLyricsDao>(relaxed = true)
    ): LyricsRepositoryImpl {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient,
            userPreferencesRepository = userPreferencesRepository,
            geminiLyricsClient = geminiLyricsClient,
            geminiLyricsDao = geminiLyricsDao
        )
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
        duration: Double,
        lyricsfile: String? = null,
    ): LrcLibResponse {
        return LrcLibResponse(
            id = name.hashCode(),
            name = name,
            artistName = artistName,
            albumName = "Album",
            duration = duration,
            plainLyrics = null,
            syncedLyrics = "[00:01.00]First line\n[00:05.00]Second line",
            lyricsfile = lyricsfile,
        )
    }
}
