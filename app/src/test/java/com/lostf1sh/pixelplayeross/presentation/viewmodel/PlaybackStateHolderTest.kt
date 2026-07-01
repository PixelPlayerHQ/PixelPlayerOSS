package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueItemSnapshot
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueSnapshot
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaybackStateHolderTest {

    private val dualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val queueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val powerManager: PowerManager = mockk(relaxed = true)

    private fun createHolder() = PlaybackStateHolder(
        dualPlayerEngine = dualPlayerEngine,
        userPreferencesRepository = userPreferencesRepository,
        queueStateHolder = queueStateHolder,
        appContext = appContext
    )

    private fun snapshot(
        mediaId: String = "duplicate-song",
        positionMs: Long = 48_000L
    ) = PlaybackQueueSnapshot(
        items = listOf(
            PlaybackQueueItemSnapshot(
                mediaId = mediaId,
                uri = "file:///music/$mediaId.mp3"
            )
        ),
        currentMediaId = mediaId,
        currentIndex = 0,
        currentPositionMs = positionMs
    )

    init {
        every { appContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isInteractive } returns true
    }

    @Test
    fun `paused override does not bleed into later occurrence with same media id`() {
        val holder = createHolder()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.rememberPausedPositionOverride("duplicate-song", 91_000L)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `clearing latest media controller restores previous activity controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(mainController)
        holder.setMediaController(externalController)

        assertSame(externalController, holder.mediaController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `clearing stale media controller keeps active controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(externalController)
        holder.setMediaController(mainController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `cold start snapshot only applies to the first matching occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(48_000L, holder.currentPosition.value)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot binds to the already active first occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(0L, holder.currentPosition.value)

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(48_000L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after playback occurrence advances`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `enabling shuffle moves the playing song to the front of the player queue`() {
        // Regression for issue #32: anchoring the current song at its old index left the
        // songs shuffled into the slots before it unreachable by forward playback.
        mockkStatic(SystemClock::class)
        mockkStatic(Uri::class)
        try {
            every { SystemClock.elapsedRealtime() } returns 100_000L
            every { Uri.parse(any()) } returns mockk(relaxed = true)
            coEvery { userPreferencesRepository.persistentShuffleEnabledFlow } returns flowOf(false)
            every { queueStateHolder.hasOriginalQueue() } returns false

            val songs = List(6) { index -> song("song-$index") }
            val startIndex = 3

            val queueItems = songs
                .map { MediaItem.Builder().setMediaId(it.id).build() }
                .toMutableList()
            var playerIndex = startIndex

            val masterPlayer = mockk<Player>(relaxed = true)
            every { masterPlayer.mediaItemCount } answers { queueItems.size }
            every { masterPlayer.currentMediaItemIndex } answers { playerIndex }
            every { masterPlayer.getMediaItemAt(any()) } answers { queueItems[firstArg()] }
            every { masterPlayer.removeMediaItems(any(), any()) } answers {
                val from = firstArg<Int>()
                val to = secondArg<Int>()
                repeat(to - from) { queueItems.removeAt(from) }
                if (playerIndex >= to) playerIndex -= to - from
            }
            every { masterPlayer.replaceMediaItems(any(), any(), any()) } answers {
                val from = firstArg<Int>()
                val to = secondArg<Int>()
                val replacement = thirdArg<List<MediaItem>>()
                repeat(to - from) { queueItems.removeAt(from) }
                queueItems.addAll(from, replacement)
            }
            every { dualPlayerEngine.masterPlayer } returns masterPlayer

            val controller = mockk<MediaController>(relaxed = true)
            every { controller.currentMediaItem } answers { queueItems.getOrNull(playerIndex) }
            every { controller.currentMediaItemIndex } answers { playerIndex }

            runBlocking {
                val holder = createHolder()
                holder.initialize(this)
                holder.setMediaController(controller)

                var uiQueue: List<Song>? = null
                holder.toggleShuffle(
                    currentSongs = songs,
                    currentSong = songs[startIndex],
                    currentQueueSourceName = "Test Queue",
                    updateQueueCallback = { uiQueue = it }
                )
                withTimeout(5_000L) {
                    holder.stablePlayerState.first { it.isShuffleEnabled }
                }

                assertEquals(
                    songs[startIndex].id,
                    queueItems.first().mediaId,
                    "Playing song must move to index 0 so no songs are stranded behind the playhead"
                )
                assertEquals(0, playerIndex, "Player must keep playing the same item at its new index")
                assertEquals(songs.size, queueItems.size, "Queue size must stay the same")
                assertEquals(
                    songs.map { it.id }.toSet(),
                    queueItems.map { it.mediaId }.toSet(),
                    "Shuffled queue must contain the same songs"
                )
                assertEquals(songs[startIndex].id, uiQueue?.firstOrNull()?.id)
            }
        } finally {
            unmockkStatic(SystemClock::class)
            unmockkStatic(Uri::class)
        }
    }

    private fun song(id: String): Song = Song(
        id = id,
        title = "Title $id",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/tmp/$id.mp3",
        contentUriString = "content://pixelplayer/song/$id",
        albumArtUriString = null,
        duration = 180_000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )

    @Test
    fun `late cold start snapshot is discarded after first occurrence already ended`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition(null)

        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }
}
