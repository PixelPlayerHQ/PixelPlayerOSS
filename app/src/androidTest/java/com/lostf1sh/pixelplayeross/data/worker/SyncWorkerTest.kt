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
import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.ArtistEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import com.lostf1sh.pixelplayeross.data.database.SourceType
import com.lostf1sh.pixelplayeross.data.preferences.LocalAlbumGroupingSettings
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.PlaybackActivityTracker
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
import java.util.concurrent.TimeUnit

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
        private val preferences: UserPreferencesRepository = createTestPreferencesRepository(),
        private val tracker: LocalLibraryChangeTracker = LocalLibraryChangeTracker()
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
                    artistImageRepository = mockk(relaxed = true),
                    localLibraryChangeTracker = tracker
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
        PlaybackActivityTracker.setPlaybackActive(false)
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
    fun localAlbumTitleGroupingMergesAndSplitsExistingTracksWithoutChangingRemoteAlbums() = runBlocking {
        val preferences = createTestPreferencesRepository()
        var grouping = LocalAlbumGroupingSettings()
        coEvery { preferences.getLocalAlbumGroupingSettings() } answers { grouping }
        coEvery { preferences.markLocalAlbumGroupingApplied(any()) } answers {
            assertThat(firstArg<String>()).isEqualTo(grouping.indexVersion)
            grouping = grouping.copy(requiresReindex = false)
        }
        var lastSyncTimestamp = 0L
        coEvery { preferences.getLastSyncTimestamp() } answers { lastSyncTimestamp }
        coEvery { preferences.setLastSyncTimestamp(any()) } answers { lastSyncTimestamp = firstArg() }

        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            val projection = secondArg<Array<String>>()
            val selection = thirdArg<String>()
            val cursor = MatrixCursor(projection)
            if (firstArg<Uri>() == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI &&
                "${MediaStore.Audio.Media.DATE_MODIFIED} > ?" !in selection
            ) {
                listOf("Main Artist", "Main Artist & Guest").forEachIndexed { index, albumArtist ->
                    val values = mapOf(
                        MediaStore.Audio.Media._ID to index + 1L,
                        MediaStore.Audio.Media.TITLE to "Track ${index + 1}",
                        MediaStore.Audio.Media.ARTIST to "Main Artist",
                        MediaStore.Audio.Media.ARTIST_ID to 101L,
                        MediaStore.Audio.Media.ALBUM to "Same Title",
                        MediaStore.Audio.Media.ALBUM_ID to index + 201L,
                        MediaStore.Audio.Media.ALBUM_ARTIST to albumArtist,
                        MediaStore.Audio.Media.DURATION to 180_000L,
                        MediaStore.Audio.Media.DATA to "/sdcard/Music/same-title/${index + 1}.m4a",
                        MediaStore.Audio.Media.MIME_TYPE to "audio/mp4",
                        MediaStore.Audio.Media.TRACK to index + 1,
                        MediaStore.Audio.Media.YEAR to 2026,
                        MediaStore.Audio.Media.DATE_ADDED to 100L,
                        MediaStore.Audio.Media.DATE_MODIFIED to 100L
                    )
                    cursor.addRow(projection.map { values[it] })
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

        assertSuccessfulSongCount(sync(), expectedCount = 2)
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id }).containsExactly(1L)
        assertThat(musicDao.getSongsByAlbumId(202L).first().map { it.id }).containsExactly(2L)
        assertThat(lastSyncTimestamp).isGreaterThan(100_000L)

        val firstTrack = requireNotNull(musicDao.getSongByIdOnce(1L))
        musicDao.updateSongs(listOf(firstTrack.copy(title = "Edited title", titleUserEdited = true, isFavorite = true)))

        val remote = firstTrack.copy(
            id = -1L, artistName = "Cloud Artist", artistId = -101L, albumArtistId = -101L,
            albumId = -201L, contentUriString = "jellyfin://remote", sourceType = SourceType.JELLYFIN
        )
        val remoteAlbum = AlbumEntity(-201L, "Same Title", "Main Artist", -101L, null, 1, 0L, 2026)
        musicDao.incrementalSyncMusicData(
            songs = listOf(remote),
            albums = listOf(remoteAlbum),
            artists = listOf(ArtistEntity(id = -101L, name = "Cloud Artist", trackCount = 1)),
            crossRefs = emptyList(),
            deletedSongIds = emptyList()
        )

        grouping = LocalAlbumGroupingSettings(byTitle = true, indexVersion = "true:1", requiresReindex = true)
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(grouping.requiresReindex).isFalse()
        assertThat(musicDao.getAlbumById(201L).first()?.songCount).isEqualTo(2)
        assertThat(musicDao.getAlbumById(202L).first()).isNull()
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id }).containsExactly(1L, 2L).inOrder()

        grouping = LocalAlbumGroupingSettings(byTitle = false, indexVersion = "false:2", requiresReindex = true)
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(grouping.requiresReindex).isFalse()
        assertThat(musicDao.getAlbumById(201L).first()?.songCount).isEqualTo(1)
        assertThat(musicDao.getAlbumById(202L).first()?.songCount).isEqualTo(1)
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id }).containsExactly(1L)
        assertThat(musicDao.getSongsByAlbumId(202L).first().map { it.id }).containsExactly(2L)
        assertThat(musicDao.getAlbumById(-201L).first()).isEqualTo(remoteAlbum)
        assertThat(musicDao.getSongsByAlbumId(-201L).first().map { it.id }).containsExactly(-1L)
        assertThat(musicDao.getSongByIdOnce(1L)?.title).isEqualTo("Edited title")
        assertThat(musicDao.getFavoriteStatus(1L)).isTrue()
    }

    @Test
    fun titleGroupedIncrementalSyncKeepsUnknownAlbumsAndRefreshesMetadataFromAllMembers() = runBlocking {
        val preferences = createTestPreferencesRepository()
        coEvery { preferences.getLocalAlbumGroupingSettings() } returns LocalAlbumGroupingSettings(
            byTitle = true, indexVersion = "true:1"
        )
        var lastSyncTimestamp = 0L
        coEvery { preferences.getLastSyncTimestamp() } answers { lastSyncTimestamp }
        coEvery { preferences.setLastSyncTimestamp(any()) } answers { lastSyncTimestamp = firstArg() }
        val years = mutableMapOf(1L to 2020, 2L to 2021, 3L to 2022)
        var modifiedTracks = emptySet<Long>()
        var revision = 0L
        var incrementalQueries = 0

        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            val projection = secondArg<Array<String>>()
            val cursor = MatrixCursor(projection)
            if (firstArg<Uri>() == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                val isIncremental = "${MediaStore.Audio.Media.DATE_MODIFIED} > ?" in thirdArg<String>()
                if (isIncremental) incrementalQueries++
                for ((id, year) in years) {
                    if (isIncremental && id !in modifiedTracks) continue
                    val unknown = id <= 2L
                    val values = mapOf(
                        MediaStore.Audio.Media._ID to id,
                        MediaStore.Audio.Media.TITLE to "Track $id",
                        MediaStore.Audio.Media.ARTIST to "Artist",
                        MediaStore.Audio.Media.ARTIST_ID to 101L,
                        MediaStore.Audio.Media.ALBUM to if (unknown) "Unknown Album" else "Named Album",
                        MediaStore.Audio.Media.ALBUM_ID to if (unknown) 201L else 202L,
                        MediaStore.Audio.Media.DURATION to 180_000L,
                        MediaStore.Audio.Media.DATA to "/sdcard/Music/${if (unknown) "unfiled" else "named"}/$id.m4a",
                        MediaStore.Audio.Media.MIME_TYPE to "audio/mp4",
                        MediaStore.Audio.Media.TRACK to id.toInt(),
                        MediaStore.Audio.Media.YEAR to year,
                        MediaStore.Audio.Media.DATE_ADDED to 100L,
                        MediaStore.Audio.Media.DATE_MODIFIED to 100L + revision,
                    )
                    cursor.addRow(projection.map { values[it] })
                }
            }
            cursor
        }

        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver() = mockContentResolver
        }
        suspend fun sync() = TestListenableWorkerBuilder<SyncWorker>(testContext)
            .setWorkerFactory(TestSyncWorkerFactory(musicDao, preferences))
            .setInputData(workDataOf(SyncWorker.INPUT_RUN_MAINTENANCE to false))
            .build()
            .doWork()

        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id }).containsExactly(1L, 2L)

        revision++
        modifiedTracks = setOf(1L, 3L)
        years[1L] = 2024
        years[3L] = 2025
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(musicDao.getSongsByAlbumId(201L).first().map { it.id }).containsExactly(1L, 2L)
        assertThat(musicDao.getAlbumById(201L).first()?.year).isEqualTo(2024)
        assertThat(musicDao.getAlbumById(202L).first()?.year).isEqualTo(2025)

        revision++
        modifiedTracks = setOf(2L)
        years[2L] = 2026
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(incrementalQueries).isEqualTo(2)
        assertThat(musicDao.getAlbumCount().first()).isEqualTo(2)
        assertThat(musicDao.getSongByIdOnce(2L)?.year).isEqualTo(2026)
        assertThat(musicDao.getAlbumById(201L).first()?.year).isEqualTo(2024)
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

    @Test
    fun incrementalSync_flagsChangesThatArriveWhileScanning() = runBlocking {
        val tracker = LocalLibraryChangeTracker()
        var visibleTrackCount = 3
        var songQueries = 0
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            val projection = secondArg<Array<String>>()
            val cursor = MatrixCursor(projection)
            if (firstArg<Uri>() == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                addMadeOfGlassRows(cursor, projection, trackCount = visibleTrackCount)
                if (MediaStore.Audio.Media.TITLE in projection && songQueries++ == 0) {
                    // MediaStore finishes indexing the rest of the copy after this scan read it.
                    tracker.onMediaStoreChanged()
                    visibleTrackCount = 16
                }
            }
            cursor
        }

        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver() = mockContentResolver
        }
        suspend fun sync(): ListenableWorker.Result =
            TestListenableWorkerBuilder<SyncWorker>(testContext)
                .setWorkerFactory(TestSyncWorkerFactory(musicDao, tracker = tracker))
                .setInputData(workDataOf(SyncWorker.INPUT_RUN_MAINTENANCE to false))
                .build()
                .doWork()

        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(tracker.hasUnscannedChanges()).isTrue()

        // The follow-up sync SyncManager schedules for that change picks up the rest.
        assertSuccessfulSongCount(sync(), expectedCount = 16)
        assertThat(musicDao.getSongsByAlbumId(201L).first()).hasSize(16)
        assertThat(tracker.hasUnscannedChanges()).isFalse()
    }

    @Test
    fun incrementalSync_defersDuringPlayback_unlessBypassRequested() = runBlocking {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } answers {
            when (firstArg<Uri>().toString()) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString() -> createMockSongCursor()
                else -> createMockGenreCursor()
            }
        }
        val testContext = object : ContextWrapper(context) {
            override fun getContentResolver() = mockContentResolver
        }
        suspend fun sync(
            bypassPlaybackDeferral: Boolean,
            runAttemptCount: Int = 0
        ): ListenableWorker.Result =
            TestListenableWorkerBuilder<SyncWorker>(testContext)
                .setWorkerFactory(TestSyncWorkerFactory(musicDao))
                .setInputData(
                    workDataOf(
                        SyncWorker.INPUT_RUN_MAINTENANCE to false,
                        SyncWorker.INPUT_BYPASS_PLAYBACK_DEFERRAL to bypassPlaybackDeferral
                    )
                )
                .setRunAttemptCount(runAttemptCount)
                .build()
                .doWork()

        PlaybackActivityTracker.setPlaybackActive(true)

        assertThat(sync(bypassPlaybackDeferral = false)).isEqualTo(ListenableWorker.Result.retry())
        assertThat(musicDao.getSongCount().first()).isEqualTo(0)

        assertSuccessfulSongCount(sync(bypassPlaybackDeferral = true), expectedCount = 2)

        // Background syncs still run once the deferral budget is spent.
        assertSuccessfulSongCount(
            sync(
                bypassPlaybackDeferral = false,
                runAttemptCount = SyncWorker.MAX_PLAYBACK_DEFERRALS
            ),
            expectedCount = 2
        )
    }

    @Test
    fun incrementalSync_repairsMetadataTextDamagedBeforeTheCharsetFix() = runBlocking {
        val preferences = createTestPreferencesRepository()
        var repairVersion = SyncWorker.CURRENT_METADATA_CHARSET_REPAIR_VERSION
        coEvery { preferences.getMetadataCharsetRepairVersion() } answers { repairVersion }
        coEvery { preferences.setMetadataCharsetRepairVersion(any()) } answers {
            repairVersion = firstArg()
        }
        var lastSyncTimestamp = 0L
        coEvery { preferences.getLastSyncTimestamp() } answers { lastSyncTimestamp }

        var damaged = true
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
                if (!usesTimestamp || 100L > selectionArgs.last().toLong()) {
                    // Older builds stored U+FFFD where the accented letter was; MediaStore
                    // itself still has the correct text.
                    val replacement = '\uFFFD'
                    val titles = if (damaged) {
                        listOf("Ch${replacement}teau", "For${replacement}t", "Plain Song")
                    } else {
                        listOf("Château", "Forêt", "Plain Song")
                    }
                    val artist = if (damaged) "C${replacement}line" else "Céline"
                    val album = if (damaged) "Ch${replacement}teau Rouge" else "Château Rouge"
                    titles.forEachIndexed { index, title ->
                        val track = index + 1
                        val values = mapOf(
                            MediaStore.Audio.Media._ID to track.toLong(),
                            MediaStore.Audio.Media.TITLE to title,
                            MediaStore.Audio.Media.ARTIST to artist,
                            MediaStore.Audio.Media.ARTIST_ID to 1L,
                            MediaStore.Audio.Media.ALBUM to album,
                            MediaStore.Audio.Media.ALBUM_ID to 301L,
                            MediaStore.Audio.Media.ALBUM_ARTIST to artist,
                            MediaStore.Audio.Media.DURATION to 180_000L,
                            MediaStore.Audio.Media.DATA to "/storage/emulated/0/Music/Chateau/$track.m4a",
                            MediaStore.Audio.Media.MIME_TYPE to "audio/mp4",
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

        // A library synced by an older build, with one title edited by the user afterwards.
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(musicDao.getSongByIdOnce(1L)?.title).isEqualTo("Ch\uFFFDteau")
        musicDao.updateSongs(
            listOf(musicDao.getSongByIdOnce(2L)!!.copy(title = "My title", titleUserEdited = true))
        )

        // After the update: the repair has not run yet, and every file is older than the
        // last sync, so a plain incremental sync would not read any of them.
        damaged = false
        repairVersion = 0
        lastSyncTimestamp = TimeUnit.DAYS.toMillis(365L * 1000)

        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(songSelections.last()).doesNotContain("${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
        assertThat(musicDao.getSongByIdOnce(1L)?.title).isEqualTo("Château")
        assertThat(musicDao.getSongByIdOnce(1L)?.artistName).isEqualTo("Céline")
        assertThat(musicDao.getSongByIdOnce(2L)?.title).isEqualTo("My title")
        assertThat(musicDao.getSongByIdOnce(2L)?.albumName).isEqualTo("Château Rouge")
        assertThat(musicDao.getAlbumById(301L).first()?.title).isEqualTo("Château Rouge")
        assertThat(musicDao.getAlbumCount().first()).isEqualTo(1)
        // The artist stored under the damaged name is left without songs and removed.
        assertThat(musicDao.getAllArtistsListRaw().map { it.name }).containsExactly("Céline")
        assertThat(repairVersion).isEqualTo(SyncWorker.CURRENT_METADATA_CHARSET_REPAIR_VERSION)

        // The repair runs once; the next sync is an ordinary timestamp-filtered one.
        assertSuccessfulSongCount(sync(), expectedCount = 3)
        assertThat(songSelections.last()).contains("${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
    }

    private fun addMadeOfGlassRows(cursor: MatrixCursor, projection: Array<String>, trackCount: Int) {
        for (track in 1..trackCount) {
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
    coEvery { repository.getLocalAlbumGroupingSettings() } returns LocalAlbumGroupingSettings()
    every { repository.artistSettingsRescanRequiredFlow } returns flowOf(false)
    every { repository.allowedDirectoriesFlow } returns flowOf(emptySet())
    every { repository.blockedDirectoriesFlow } returns flowOf(emptySet())
    every { repository.minTracksPerAlbumFlow } returns flowOf(1)
    every { repository.autoScanLrcFilesFlow } returns flowOf(false)
    coEvery { repository.getDirectoryRulesVersion() } returns 0
    coEvery { repository.getLastAppliedDirectoryRulesVersion() } returns 0
    coEvery { repository.getLastSyncTimestamp() } returns 0L
    coEvery { repository.getMinSongDuration() } returns 10_000
    // The one-time charset repair reads MediaStore without a timestamp cutoff, which would hide
    // what the timestamp-based tests check. Tests that cover the repair override this.
    coEvery { repository.getMetadataCharsetRepairVersion() } returns
        SyncWorker.CURRENT_METADATA_CHARSET_REPAIR_VERSION
    coEvery { repository.getEmbeddedTagScanVersion() } returns SyncWorker.CURRENT_EMBEDDED_TAG_SCAN_VERSION
    return repository
}

open class ContextWrapper(base: Context) : android.content.ContextWrapper(base)
