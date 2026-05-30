package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListeningStatsTrackerTest {

    private val dailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val playbackStatsRepository: PlaybackStatsRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(SystemClock::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `finalizeCurrentSession preserves listening longer than track duration`() {
        val tracker = ListeningStatsTracker(
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository
        )
        val song = song(
            songId = "looped-song",
            durationMs = TimeUnit.MINUTES.toMillis(3)
        )
        val listenedMs = TimeUnit.MINUTES.toMillis(12)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            1_000L,
            1_000L + listenedMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(timeout = 2_000) {
            dailyMixManager.recordPlay(song.id, listenedMs, any())
        }
        coVerify(timeout = 2_000) {
            playbackStatsRepository.recordPlayback(song.id, listenedMs, any())
        }
    }

    @Test
    fun `onProgress accumulates incremental listening time`() {
        val tracker = ListeningStatsTracker(
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository
        )
        val song = song(songId = "song-1")
        val firstChunkMs = 7_000L
        val secondChunkMs = 8_000L
        val expectedDurationMs = firstChunkMs + secondChunkMs

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            5_000L,
            5_000L + firstChunkMs,
            5_000L + firstChunkMs + secondChunkMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.onProgress(positionMs = firstChunkMs, isPlaying = true)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(timeout = 2_000) {
            playbackStatsRepository.recordPlayback(song.id, expectedDurationMs, any())
        }
        assertThat(expectedDurationMs).isGreaterThan(TimeUnit.SECONDS.toMillis(5))
    }

    private fun song(songId: String, durationMs: Long = 5 * 60 * 1000L): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = durationMs,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
