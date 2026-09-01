package com.lostf1sh.pixelplayeross.data.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.ArtistEntity
import com.lostf1sh.pixelplayeross.data.database.MIGRATION_1_2
import com.lostf1sh.pixelplayeross.data.database.MIGRATION_2_3
import com.lostf1sh.pixelplayeross.data.database.MIGRATION_3_4
import com.lostf1sh.pixelplayeross.data.database.MIGRATION_4_5
import com.lostf1sh.pixelplayeross.data.database.MIGRATION_5_6
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Device-level workflow tests that exercise the real [MusicService] through a
 * [MediaController], the same path the in-app UI and external controllers use:
 * library-backed queue edits, playback start/pause, and the custom session
 * commands (shuffle, sleep timers).
 *
 * Songs are seeded into the app's real Room database (the service resolves media
 * ids against it in `onSetMediaItems`) and point at a generated silent WAV file,
 * so the tests run on any device or emulator without needing a media library.
 */
@RunWith(AndroidJUnit4::class)
class MusicServiceWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: PixelPlayerDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var controller: MediaController
    private lateinit var wavFile: File

    private val testSongIds = listOf(TEST_SONG_ID_1, TEST_SONG_ID_2, TEST_SONG_ID_3)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        // A test-only Hilt entry point cannot be installed into the already compiled production
        // SingletonComponent. Open a test-owned connection to the same on-device database instead;
        // MusicService will still exercise its real production Hilt graph and DAO.
        database = Room.databaseBuilder(
            context.applicationContext,
            PixelPlayerDatabase::class.java,
            DATABASE_NAME,
        )
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        musicDao = database.musicDao()

        wavFile = File(context.cacheDir, "workflow_test_tone.wav").apply {
            writeBytes(buildSilentWav(durationMs = 2_000))
        }
        runBlocking {
            removeFixture()
            seedLibrary(wavFile)
        }

        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        controller = MediaController.Builder(context, token)
            .buildAsync()
            .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        if (this::controller.isInitialized) {
            onMain {
                controller.stop()
                controller.clearMediaItems()
                controller.release()
            }
        }
        if (this::database.isInitialized && database.isOpen && this::musicDao.isInitialized) {
            runBlocking { removeFixture() }
        }
        if (this::database.isInitialized) {
            database.close()
        }
        if (this::wavFile.isInitialized) {
            wavFile.delete()
        }
    }

    @Test
    fun controllerFromOwnAppReceivesCustomSessionCommands() {
        val expectedCommands = listOf(
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
            MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
            MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY,
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY,
            MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
            MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER,
        )

        val available = onMain {
            expectedCommands.associateWith { action ->
                controller.isSessionCommandAvailable(SessionCommand(action, Bundle.EMPTY))
            }
        }

        available.forEach { (action, isAvailable) ->
            assertWithMessage("command %s should be available", action).that(isAvailable).isTrue()
        }
    }

    @Test
    fun queueEditsPropagateThroughSession() {
        setSeededQueue()

        waitUntil("queue resolves to 3 items") { controller.mediaItemCount == 3 }

        onMain { controller.removeMediaItem(0) }
        waitUntil("item removed") { controller.mediaItemCount == 2 }

        val orderAfterRemove = onMain { queueMediaIds() }
        assertThat(orderAfterRemove)
            .containsExactly(TEST_SONG_ID_2.toString(), TEST_SONG_ID_3.toString())
            .inOrder()

        onMain { controller.moveMediaItem(0, 1) }
        val orderAfterMove = onMain { queueMediaIds() }
        assertThat(orderAfterMove)
            .containsExactly(TEST_SONG_ID_3.toString(), TEST_SONG_ID_2.toString())
            .inOrder()
    }

    @Test
    fun playbackStartsAndPausesThroughService() {
        setSeededQueue()
        onMain {
            controller.prepare()
            controller.play()
        }

        waitUntil("playback starts") { controller.isPlaying }

        onMain { controller.pause() }
        waitUntil("playback pauses") { !controller.isPlaying }

        val position = onMain { controller.currentPosition }
        assertThat(position).isAtLeast(0L)
    }

    @Test
    fun sleepTimerCommandsAreAcceptedAndCancellable() {
        setSeededQueue()

        val setResult = sendCustomCommand(
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
            Bundle().apply {
                putInt(MusicNotificationProvider.EXTRA_SLEEP_TIMER_MINUTES, 30)
            }
        )
        assertThat(setResult.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS)

        val endOfTrackResult = sendCustomCommand(
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
            Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_END_OF_TRACK_ENABLED, true)
            }
        )
        assertThat(endOfTrackResult.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS)

        val cancelResult = sendCustomCommand(
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
            Bundle.EMPTY
        )
        assertThat(cancelResult.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS)
    }

    @Test
    fun shuffleStateCommandRoundTrips() {
        setSeededQueue()

        val enableResult = sendCustomCommand(
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
            Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, true)
            }
        )
        assertThat(enableResult.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS)

        val disableResult = sendCustomCommand(
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
            Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, false)
            }
        )
        assertThat(disableResult.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS)
    }

    private suspend fun seedLibrary(mediaFile: File) {
        val artist = ArtistEntity(id = TEST_ARTIST_ID, name = "Workflow Test Artist", trackCount = 3)
        val album = AlbumEntity(
            id = TEST_ALBUM_ID,
            title = "Workflow Test Album",
            artistName = artist.name,
            artistId = artist.id,
            albumArtUriString = null,
            songCount = 3,
            dateAdded = 0L,
            year = 2024,
        )
        val songs = testSongIds.mapIndexed { index, id ->
            SongEntity(
                id = id,
                title = "Workflow Test Song ${index + 1}",
                artistName = artist.name,
                artistId = artist.id,
                albumName = album.title,
                albumId = album.id,
                contentUriString = android.net.Uri.fromFile(mediaFile).toString(),
                albumArtUriString = null,
                duration = 2_000L,
                genre = null,
                filePath = mediaFile.absolutePath,
                parentDirectoryPath = mediaFile.parent.orEmpty(),
                mimeType = "audio/wav",
            )
        }
        // Parent rows must exist before songs because the production schema enforces foreign keys.
        // These upserts touch only the reserved fixture ids and leave the emulator's library intact.
        musicDao.insertArtists(listOf(artist))
        musicDao.insertAlbums(listOf(album))
        musicDao.insertSongs(songs)
    }

    private suspend fun removeFixture() {
        database.withTransaction {
            musicDao.deleteCrossRefsBySongIds(testSongIds)
            musicDao.deleteFavoritesBySongIds(testSongIds)
            musicDao.deleteLyricsBySongIds(testSongIds)

            val stringIds = testSongIds.map(Long::toString).toTypedArray<Any>()
            val stringPlaceholders = testSongIds.joinToString(separator = ",") { "?" }
            val sqlite = database.openHelper.writableDatabase
            sqlite.execSQL(
                "DELETE FROM playlist_songs WHERE song_id IN ($stringPlaceholders)",
                stringIds,
            )
            sqlite.execSQL(
                "DELETE FROM song_engagements WHERE song_id IN ($stringPlaceholders)",
                stringIds,
            )
            sqlite.execSQL(
                "DELETE FROM audio_bookmarks WHERE song_id IN ($stringPlaceholders)",
                stringIds,
            )
            sqlite.execSQL(
                "DELETE FROM offline_tracks WHERE song_id IN ($stringPlaceholders)",
                stringIds,
            )
            sqlite.execSQL(
                "DELETE FROM transition_rules " +
                    "WHERE fromTrackId IN ($stringPlaceholders) " +
                    "OR toTrackId IN ($stringPlaceholders)",
                (stringIds.asList() + stringIds.asList()).toTypedArray(),
            )

            musicDao.deleteSongsByIds(testSongIds)
            sqlite.execSQL("DELETE FROM albums WHERE id = ?", arrayOf(TEST_ALBUM_ID))
            sqlite.execSQL("DELETE FROM artists WHERE id = ?", arrayOf(TEST_ARTIST_ID))
        }
    }

    private fun setSeededQueue() {
        val items = testSongIds.map { id ->
            MediaItem.Builder().setMediaId(id.toString()).build()
        }
        onMain { controller.setMediaItems(items) }
        waitUntil("queue applied") { controller.mediaItemCount == items.size }
    }

    private fun queueMediaIds(): List<String> =
        (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }

    private fun sendCustomCommand(action: String, args: Bundle): SessionResult =
        onMain { controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args) }
            .get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (onMain(condition)) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** Minimal 16-bit mono PCM WAV of silence — playable by ExoPlayer without any assets. */
    private fun buildSilentWav(durationMs: Int, sampleRate: Int = 8_000): ByteArray {
        val sampleCount = sampleRate * durationMs / 1000
        val dataSize = sampleCount * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        return buffer.array()
    }

    private companion object {
        const val DATABASE_NAME = "pixelplayer_database"
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val COMMAND_TIMEOUT_SECONDS = 10L
        const val TEST_ARTIST_ID = -9_000_000_000_000_001L
        const val TEST_ALBUM_ID = -9_000_000_000_000_002L
        const val TEST_SONG_ID_1 = -9_000_000_000_000_101L
        const val TEST_SONG_ID_2 = -9_000_000_000_000_102L
        const val TEST_SONG_ID_3 = -9_000_000_000_000_103L
    }
}
