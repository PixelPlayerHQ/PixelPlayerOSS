package com.lostf1sh.pixelplayeross.data.worker

import android.Manifest
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.concurrent.futures.ResolvableFuture
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    @get:Rule
    val mediaReadPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    private lateinit var context: Context
    private lateinit var database: PixelPlayerDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var mockContentResolver: android.content.ContentResolver


    class TestSyncWorkerFactory(
        private val dao: MusicDao,
        private val preferences: UserPreferencesRepository = createTestPreferencesRepository()
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return if (workerClassName == SyncWorker::class.java.name) {
                SyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    musicDao = dao,
                    userPreferencesRepository = preferences,
                    lyricsRepository = mockk(relaxed = true),
                    cloudSyncCoordinator = mockk(relaxed = true),
                    artistImageRepository = mockk(relaxed = true)
                )
            } else {
                null
            }
        }
    }


    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        musicDao = database.musicDao()
        mockContentResolver = mockk()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    private fun createMockSongCursor(): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED
        ))
        cursor.addRow(arrayOf<Any?>(1L, "Test Song 1", "Test Artist 1", 101L, "Test Album 1", 201L, null, 180000L, "/sdcard/Music/song1.mp3", "audio/mpeg", 1, 2024, 100L, 101L))
        cursor.addRow(arrayOf<Any?>(2L, "Test Song 2", "Test Artist 2", 102L, "Test Album 2", 202L, null, 240000L, "/sdcard/Music/song2.mp3", "audio/mpeg", 2, 2024, 100L, 101L))
        return cursor
    }

    private fun createMockAlbumCursor(): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST
        ))
        cursor.addRow(arrayOf<Any>(201L, "Test Album 1", "Test Artist 1"))
        cursor.addRow(arrayOf<Any>(202L, "Test Album 2", "Test Artist 2"))
        return cursor
    }

    private fun createMockArtistCursor(): MatrixCursor {
         val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST
        ))
        cursor.addRow(arrayOf<Any>(101L, "Test Artist 1"))
        cursor.addRow(arrayOf<Any>(102L, "Test Artist 2"))
        return cursor
    }

    private fun createMockGenreCursor(): MatrixCursor {
        return MatrixCursor(arrayOf(MediaStore.Audio.GenresColumns.NAME))
    }


    @Test
    fun testSyncWorker_success_whenMediaStoreHasData() = runBlocking {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            when (firstArg<Uri>().toString()) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString() -> createMockSongCursor()
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString() -> createMockAlbumCursor()
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString() -> createMockArtistCursor()
                else -> createMockGenreCursor()
            }
        }


        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver(): android.content.ContentResolver {
                return mockContentResolver
            }
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(testContext)
            .setWorkerFactory(TestSyncWorkerFactory(musicDao))
            .build()

        val result = worker.doWork()
        assertSuccessfulSongCount(result, expectedCount = 2)

        val songsInDb = musicDao.getSongs(emptyList(), false).first()
        assertThat(songsInDb).hasSize(2)
        assertThat(songsInDb.find { it.id == 1L }?.title).isEqualTo("Test Song 1")

        val albumsInDb = musicDao.getAlbums(emptyList(), false, 0, 1).first()
        assertThat(albumsInDb).hasSize(2)
        assertThat(albumsInDb.find { it.id == 201L }?.title).isEqualTo("Test Album 1")

        val artistsInDb = musicDao.getArtists(emptyList(), false).first()
        assertThat(artistsInDb).hasSize(2)
        assertThat(artistsInDb.map { it.name })
            .containsExactly("Test Artist 1", "Test Artist 2")
        Unit
    }

    @Test
    fun testSyncWorker_success_whenMediaStoreIsEmpty() = runBlocking {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            MatrixCursor(secondArg<Array<String>?>() ?: emptyArray())
        }

        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver(): android.content.ContentResolver {
                return mockContentResolver
            }
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(testContext)
            .setWorkerFactory(TestSyncWorkerFactory(musicDao))
            .build()

        val result = worker.doWork()
        assertSuccessfulSongCount(result, expectedCount = 0)
        assertThat(musicDao.getSongCount().first()).isEqualTo(0)
        assertThat(musicDao.getAlbumCount().first()).isEqualTo(0)
        assertThat(musicDao.getArtistCount().first()).isEqualTo(0)
    }

    @Test
    fun incrementalSync_recoversAlbumTracksIndexedWithOldTimestamps() = runBlocking {
        val preferences = createTestPreferencesRepository()
        var lastSyncTimestamp = 0L
        coEvery { preferences.getLastSyncTimestamp() } answers { lastSyncTimestamp }
        coEvery { preferences.setLastSyncTimestamp(any()) } answers {
            lastSyncTimestamp = firstArg()
        }

        var visibleTrackCount = 3
        val songSelections = mutableListOf<String>()
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            val projection = secondArg<Array<String>>()
            val cursor = MatrixCursor(projection)
            if (firstArg<Uri>() == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                val selection = thirdArg<String>()
                val selectionArgs = arg<Array<String>>(3)
                val usesTimestamp = selection.contains("${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
                if (MediaStore.Audio.Media.TITLE in projection) {
                    songSelections += selection
                }
                // The scanner exposes the remaining tracks after a previous sync, with
                // timestamps older than its watermark (for example, after restoring files).
                if (!usesTimestamp || 100L > selectionArgs.last().toLong()) {
                    for (track in 1..visibleTrackCount) {
                        val values = mapOf(
                            MediaStore.Audio.Media._ID to track.toLong(),
                            MediaStore.Audio.Media.TITLE to "Track $track",
                            MediaStore.Audio.Media.ARTIST to "milet",
                            MediaStore.Audio.Media.ARTIST_ID to 1L,
                            MediaStore.Audio.Media.ALBUM to "Made of Glass",
                            MediaStore.Audio.Media.ALBUM_ID to 201L,
                            MediaStore.Audio.Media.ALBUM_ARTIST to "milet",
                            MediaStore.Audio.Media.DURATION to 180_000L,
                            MediaStore.Audio.Media.DATA to "/storage/emulated/0/Music/milet - Made of Glass/$track.flac",
                            MediaStore.Audio.Media.MIME_TYPE to "audio/flac",
                            MediaStore.Audio.Media.TRACK to track,
                            MediaStore.Audio.Media.YEAR to 2026,
                            MediaStore.Audio.Media.DATE_ADDED to 100L,
                            MediaStore.Audio.Media.DATE_MODIFIED to 100L
                        )
                        cursor.addRow(projection.map { values[it] })
                    }
                }
            }
            cursor
        }

        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver() = mockContentResolver
        }
        suspend fun sync(): ListenableWorker.Result =
            TestListenableWorkerBuilder<SyncWorker>(testContext)
                .setWorkerFactory(TestSyncWorkerFactory(musicDao, preferences))
                .setInputData(workDataOf(SyncWorker.INPUT_RUN_MAINTENANCE to false))
                .build()
                .doWork()

        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(lastSyncTimestamp).isGreaterThan(100_000L)
        val editedSong = musicDao.getSongByIdOnce(1L)!!.copy(
            title = "My title",
            titleUserEdited = true
        )
        musicDao.updateSongs(listOf(editedSong))

        visibleTrackCount = 16
        assertSuccessfulSongCount(sync(), expectedCount = 16)
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id })
            .containsExactlyElementsIn((1L..16L).toList())
        assertThat(musicDao.getAlbumById(201L).first()?.songCount).isEqualTo(16)
        assertThat(musicDao.getSongByIdOnce(1L)?.title).isEqualTo("My title")
        assertThat(songSelections.last()).doesNotContain("${MediaStore.Audio.Media.DATE_MODIFIED} > ?")

        // Once the IDs match, ordinary incremental scans keep their timestamp filter.
        assertSuccessfulSongCount(sync(), expectedCount = 16)
        assertThat(songSelections.last()).contains("${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
    }

    private fun assertSuccessfulSongCount(
        result: ListenableWorker.Result,
        expectedCount: Int,
    ) {
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertThat(output.getInt(SyncWorker.OUTPUT_TOTAL_SONGS, -1)).isEqualTo(expectedCount)
    }
}

private fun createTestPreferencesRepository(): UserPreferencesRepository {
    val repository = mockk<UserPreferencesRepository>(relaxed = true)
    every { repository.artistDelimitersFlow } returns
        flowOf(UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS)
    every { repository.artistWordDelimitersFlow } returns
        flowOf(UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS)
    every { repository.extractArtistsFromTitleFlow } returns flowOf(false)
    every { repository.groupByAlbumArtistFlow } returns flowOf(false)
    every { repository.artistSettingsRescanRequiredFlow } returns flowOf(false)
    every { repository.allowedDirectoriesFlow } returns flowOf(emptySet())
    every { repository.blockedDirectoriesFlow } returns flowOf(emptySet())
    every { repository.minTracksPerAlbumFlow } returns flowOf(1)
    every { repository.autoScanLrcFilesFlow } returns flowOf(false)
    coEvery { repository.getDirectoryRulesVersion() } returns 0
    coEvery { repository.getLastAppliedDirectoryRulesVersion() } returns 0
    coEvery { repository.getLastSyncTimestamp() } returns 0L
    coEvery { repository.getMinSongDuration() } returns 10_000
    return repository
}

open class ContextWrapper(base: Context) : android.content.ContextWrapper(base)
