package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.LyricsEntity
import com.lostf1sh.pixelplayeross.data.database.LyricsDao
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibResponse
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
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
        val repository = LyricsRepositoryImpl(
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
        val repository = LyricsRepositoryImpl(
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
        val repository = LyricsRepositoryImpl(
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
        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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

        val repository = LyricsRepositoryImpl(
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
    fun getLyrics_prefersACleanStoredCopyOverOneDamagedIntoReplacementCharacters() = runTest {
        // Builds before 0.3.0-alpha.19 could save Vietnamese lyrics back with U+FFFD in place
        // of letters; a clean copy elsewhere must win over the damaged one.
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(131L) } returns LyricsEntity(
            songId = 131L,
            content = "[00:01.00]Anh vẫn nhớ thân em",
            isSynced = true,
            source = "manual"
        )
        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = false)
        )
        val song = testSong(id = "131", title = "Mùa Thu", artist = "Sài Gòn Echo", duration = 180_000L)
            .copy(lyrics = "[00:01.00]Anh v\uFFFDn nh? th\uFFFDn em")

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.EMBEDDED_FIRST)

        assertThat(lyrics!!.synced!!.single().line).isEqualTo("Anh vẫn nhớ thân em")
    }

    @Test
    fun getLyrics_replacesDamagedStoredLyricsWithACleanLrcFileNextToTheSong() = runTest {
        val musicDir = Files.createTempDirectory("pixelplayer-music").toFile()
        File(musicDir, "Mua Thu.lrc").writeText("[00:01.00]Anh vẫn nhớ thân em")
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(132L) } returns null
        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
        )
        val song = testSong(
            id = "132",
            title = "Mùa Thu",
            artist = "Sài Gòn Echo",
            path = File(musicDir, "Mua Thu.flac").path,
            duration = 180_000L
        ).copy(lyrics = "[00:01.00]Anh v�n nh? th�n em")

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.API_FIRST)

        assertThat(lyrics!!.synced!!.single().line).isEqualTo("Anh vẫn nhớ thân em")
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
    }

    @Test
    fun getLyrics_keepsDamagedStoredLyricsWhenNoCleanCopyExists() = runTest {
        // Lyrics the user imported or picked stay visible, and no automatic search replaces them.
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(133L) } returns LyricsEntity(
            songId = 133L,
            content = "[00:01.00]Anh v�n nh� em",
            isSynced = true,
            source = "manual"
        )
        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = true)
        )
        val song = testSong(id = "133", title = "Mùa Thu", artist = "Sài Gòn Echo", duration = 180_000L)

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.API_FIRST)

        assertThat(lyrics!!.synced!!.single().line).isEqualTo("Anh v�n nh� em")
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { lyricsDao.insert(any()) }
    }

    @Test
    fun containsReplacementCharacter_findsDamageInBackingVocalsUnderACleanLine() {
        val timedBacking = LyricsUtils.parseLyrics(
            "[00:01.00]<00:01.00>Anh <00:01.40>vẫn <00:01.80>nhớ\n[bg:<00:02.20>(v�n <00:02.60>nh�)]"
        )
        val untimedBacking = LyricsUtils.parseLyrics("[00:01.00]Anh vẫn nhớ\n[bg:(v�n nh�)]")

        assertThat(timedBacking.plain!!.single()).doesNotContain("�")
        assertThat(timedBacking.containsReplacementCharacter()).isTrue()
        assertThat(untimedBacking.containsReplacementCharacter()).isTrue()
    }

    @Test
    fun containsReplacementCharacter_findsDamageInTimedWordsAndTranslations() {
        val damagedWord = Lyrics(
            synced = listOf(
                SyncedLine(
                    time = 1_000,
                    line = "Anh vẫn nhớ",
                    words = listOf(SyncedWord(1_000, "Anh "), SyncedWord(1_400, "v�n"))
                )
            )
        )
        val damagedTranslation = LyricsUtils.parseLyrics("[00:01.00]Anh vẫn nhớ\n[00:01.00]I still rem�mber")

        assertThat(damagedWord.containsReplacementCharacter()).isTrue()
        assertThat(damagedTranslation.containsReplacementCharacter()).isTrue()
    }

    @Test
    fun containsReplacementCharacter_acceptsCleanLyricsWithBackingVocals() {
        val lyrics = LyricsUtils.parseLyrics(
            "[00:01.00]v1:<00:01.00>Anh <00:01.40>vẫn <00:01.80>nhớ\n[bg:<00:02.20>(vẫn <00:02.60>nhớ)]"
        )

        assertThat(lyrics.containsReplacementCharacter()).isFalse()
    }

    @Test
    fun getLyrics_doesNotTakeALrcFileWithDamagedBackingVocalsAsTheCleanCopy() = runTest {
        val musicDir = Files.createTempDirectory("pixelplayer-music").toFile()
        File(musicDir, "Mua Thu.lrc").writeText("[00:01.00]Anh vẫn nhớ thân em\n[bg:(th�n em)]")
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(134L) } returns null
        val repository = LyricsRepositoryImpl(
            context = testContext(),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = false)
        )
        val song = testSong(
            id = "134",
            title = "Mùa Thu",
            artist = "Sài Gòn Echo",
            path = File(musicDir, "Mua Thu.flac").path,
            duration = 180_000L
        ).copy(lyrics = "[00:01.00]Anh v�n nh� th�n em")

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.LOCAL_FIRST)

        assertThat(lyrics!!.synced!!.single().line).isEqualTo("Anh v�n nh� th�n em")
    }

    @Test
    fun fetchFromRemote_diskCacheKeepsSingersAndBackgroundVocals() = runTest {
        val filesDir = Files.createTempDirectory("pixelplayer-lyrics-test").toFile()
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(107L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(
            lrcResponse(name = "Bones", artistName = "Neon Duo", duration = 180.0).copy(
                syncedLyrics = """
                    [00:02.00]v1:<00:02.00>There's <00:02.60>a <00:03.00>whis<00:03.40>per<00:04.00>
                    [bg:<00:04.20>(whis<00:04.60>per)]
                    [00:07.00]v2:<00:07.00>Kee<00:07.40>ping <00:07.90>me
                """.trimIndent()
            )
        )
        val song = testSong(id = "107", title = "Bones", artist = "Neon Duo", duration = 180_000L)
        LyricsRepositoryImpl(
            context = testContext(filesDir),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
        ).fetchFromRemote(song).getOrThrow()

        // A fresh repository has no memory cache, so this reads the JSON written above.
        val cached = LyricsRepositoryImpl(
            context = testContext(filesDir),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = false)
        ).getLyrics(song, LyricsSourcePreference.EMBEDDED_FIRST)

        val lines = cached!!.synced!!
        assertThat(lines.map { it.voice }).containsExactly(1, 2).inOrder()
        assertThat(lines.first().words!!.map { it.word }).containsExactly("There's", "a", "whis", "per").inOrder()
        assertThat(lines.first().words!!.last().endTime).isEqualTo(4_000)
        assertThat(lines.first().background!!.text).isEqualTo("(whisper)")
        assertThat(lines[1].words!!.map { it.startsNewWord }).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun fetchFromRemote_diskCacheKeepsABackingOnlyLinesTranslationApartFromALineSungWithIt() = runTest {
        val filesDir = Files.createTempDirectory("pixelplayer-lyrics-test").toFile()
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(108L) } returns null
        coEvery { apiService.searchLyrics(any(), any(), any(), any()) } returns arrayOf(
            lrcResponse(name = "Echoes", artistName = "Neon Duo", duration = 180.0).copy(
                syncedLyrics = """
                    [00:10.00]v2:
                    [bg:<00:10.00>(Ooh <00:10.50>ooh)]
                    [tr:(Uh uh)]
                    [00:10.00]v2:<00:10.00>Sung <00:10.50>line
                    [00:14.00]v2:
                    [bg:<00:14.00>(Aah)]
                    [00:14.00]v2:<00:14.00>Next <00:14.50>line
                """.trimIndent()
            )
        )
        val song = testSong(id = "108", title = "Echoes", artist = "Neon Duo", duration = 180_000L)
        LyricsRepositoryImpl(
            context = testContext(filesDir),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository()
        ).fetchFromRemote(song).getOrThrow()

        // A fresh repository has no memory cache, so this reads the JSON written above.
        val cached = LyricsRepositoryImpl(
            context = testContext(filesDir),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository(externalLyricsEnabled = false)
        ).getLyrics(song, LyricsSourcePreference.EMBEDDED_FIRST)

        val lines = cached!!.synced!!
        assertThat(lines.map { it.line }).containsExactly("", "Sung line", "", "Next line").inOrder()
        assertThat(lines.map { it.translation }).containsExactly("(Uh uh)", null, null, null).inOrder()
        assertThat(lines.map { it.background?.text }).containsExactly("(Ooh ooh)", null, "(Aah)", null).inOrder()
    }

    private fun testContext(filesDir: File = Files.createTempDirectory("pixelplayer-lyrics-test").toFile()): Context {
        return mockk<Context>(relaxed = true) {
            every { this@mockk.filesDir } returns filesDir
        }
    }

    private fun userPreferencesRepository(externalLyricsEnabled: Boolean = true): UserPreferencesRepository {
        return mockk {
            every { this@mockk.externalLyricsEnabledFlow } returns flowOf(externalLyricsEnabled)
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
