package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackEvent
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.recap.RecapSlide
import com.lostf1sh.pixelplayeross.presentation.recap.RecapUiModel
import com.lostf1sh.pixelplayeross.presentation.recap.RecapUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("Europe/Berlin")

    /** "Now" for every recap built here: the 2025 recap is complete, the 2026 one is not. */
    private val now = LocalDateTime.of(2026, 3, 10, 12, 0)

    /** Builds recaps with the real aggregation code; the "recorded" plays live in [events]. */
    private val recapBuilder = PlaybackStatsRepository(
        mockk(relaxed = true) {
            every { filesDir } returns createTempDirectory("recap-view-model-test-").toFile()
            every { getString(R.string.presentation_batch_g_stats_week_label, *anyVararg()) } returns "Week"
        }
    )

    @Volatile
    private var events: List<PlaybackEvent> = emptyList()

    private val playbackStatsRepository = mockk<PlaybackStatsRepository> {
        coEvery { loadRecap(any(), any(), any()) } coAnswers {
            recapBuilder.buildRecapFromEvents(
                year = firstArg(),
                songs = secondArg(),
                nowMillis = millis(now),
                allEvents = events,
                zoneId = zone
            )
        }
    }

    private val librarySongs = listOf(
        song("a", artist = "Aurora Fields", art = "content://art/a"),
        song("c", artist = "Blue Quartet", art = "content://art/c")
    )

    private val musicRepository = mockk<MusicRepository>(relaxed = true) {
        coEvery { getAllSongsOnce() } returns librarySongs
        coEvery { getArtistIdByName(any()) } returns null
    }

    private val artistImageRepository = mockk<ArtistImageRepository> {
        coEvery { getEffectiveArtistImageUrl(any(), any()) } returns null
    }

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.Main
        override val default: CoroutineDispatcher get() = Dispatchers.Main
    }

    @Test
    fun `emits NotEnoughData below the minimum listening time`() = runTest {
        events = listOf(play("a", LocalDateTime.of(2025, 5, 1, 10, 0), minutes = 20))

        createViewModel(year = 2025).uiState.test {
            assertThat(awaitResult()).isEqualTo(RecapUiState.NotEnoughData(2025))
        }
    }

    @Test
    fun `emits Ready with slides for a year with history`() = runTest {
        events = yearOfListening()

        createViewModel(year = 2025).uiState.test {
            val model = (awaitResult() as RecapUiState.Ready).model

            assertThat(model.year).isEqualTo(2025)
            assertThat(model.isSoFar).isFalse()
            assertThat(model.slides.first()).isEqualTo(RecapSlide.Intro(year = 2025, isSoFar = false))
            val summary = model.slides.last() as RecapSlide.Summary
            assertThat(summary.topSong?.songId).isEqualTo("a")
            assertThat(summary.canPlayTopSongs).isTrue()
        }
    }

    @Test
    fun `a running year before december reads so far`() = runTest {
        events = listOf(play("a", LocalDateTime.of(2026, 2, 1, 21, 0), minutes = 90))

        createViewModel(year = 2026).uiState.test {
            val model = (awaitResult() as RecapUiState.Ready).model

            assertThat(model.isSoFar).isTrue()
            assertThat(model.slides.first()).isEqualTo(RecapSlide.Intro(year = 2026, isSoFar = true))
        }
    }

    @Test
    fun `topSongsQueue keeps recap order and drops songs missing from the library`() = runTest {
        events = yearOfListening()
        val viewModel = createViewModel(year = 2025)

        viewModel.uiState.test {
            awaitResult()
            // "b" was the second most played song but has since been deleted.
            assertThat(viewModel.topSongsQueue.map { it.id }).containsExactly("a", "c").inOrder()
        }
    }

    @Test
    fun `artists without a picture borrow the cover of their most played track`() = runTest {
        events = yearOfListening()
        coEvery { artistImageRepository.getEffectiveArtistImageUrl(any(), "Blue Quartet") } returns "https://img/blue"

        createViewModel(year = 2025).uiState.test {
            val model = (awaitResult() as RecapUiState.Ready).model
            val artists = model.slides.filterIsInstance<RecapSlide.TopArtists>().single().artists

            assertThat(artists.associate { it.name to it.imageUri }).containsExactly(
                "Aurora Fields", "content://art/a",
                "Blue Quartet", "https://img/blue"
            )
        }
    }

    @Test
    fun `the story shows with cover stand-ins before slow artist pictures arrive`() = runTest {
        events = yearOfListening()
        val slowPicture = CompletableDeferred<String?>()
        coEvery { artistImageRepository.getEffectiveArtistImageUrl(any(), "Aurora Fields") } coAnswers {
            slowPicture.await()
        }

        createViewModel(year = 2025).uiState.test {
            // Ready while the picture request is still out: the listening data alone is enough.
            val first = (awaitResult() as RecapUiState.Ready).model
            assertThat(first.artistImages()).containsExactly(
                "Aurora Fields", "content://art/a",
                "Blue Quartet", "content://art/c"
            )

            slowPicture.complete("https://img/aurora")

            val updated = (awaitItem() as RecapUiState.Ready).model
            assertThat(updated.artistImages()).containsExactly(
                "Aurora Fields", "https://img/aurora",
                "Blue Quartet", "content://art/c"
            )
            assertThat(updated.slides.filterIsInstance<RecapSlide.Summary>().single().topArtist?.imageUri)
                .isEqualTo("https://img/aurora")
            // Only the pictures change; the story itself stays the same snapshot.
            assertThat(updated.slides.map { it.key }).isEqualTo(first.slides.map { it.key })
        }
    }

    @Test
    fun `a failed artist picture lookup keeps the story with cover stand-ins`() = runTest {
        events = yearOfListening()
        coEvery { musicRepository.getArtistIdByName(any()) } throws IllegalStateException("database closed")

        createViewModel(year = 2025).uiState.test {
            val model = (awaitResult() as RecapUiState.Ready).model

            assertThat(model.artistImages()).containsExactly(
                "Aurora Fields", "content://art/a",
                "Blue Quartet", "content://art/c"
            )
        }
    }

    @Test
    fun `a failed load ends in NotEnoughData instead of loading forever`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } throws IllegalStateException("library unavailable")

        createViewModel(year = 2025).uiState.test {
            assertThat(awaitResult()).isEqualTo(RecapUiState.NotEnoughData(2025))
        }
    }

    private fun createViewModel(year: Int) = RecapViewModel(
        savedStateHandle = SavedStateHandle(mapOf(RecapViewModel.YEAR_ARG to year)),
        playbackStatsRepository = playbackStatsRepository,
        musicRepository = musicRepository,
        artistImageRepository = artistImageRepository,
        dispatchers = dispatchers
    )

    private fun RecapUiModel.artistImages(): Map<String, String?> =
        slides.filterIsInstance<RecapSlide.TopArtists>().single().artists.associate { it.name to it.imageUri }

    private suspend fun ReceiveTurbine<RecapUiState>.awaitResult(): RecapUiState {
        val first = awaitItem()
        return if (first == RecapUiState.Loading) awaitItem() else first
    }

    private fun yearOfListening(): List<PlaybackEvent> = listOf(
        play("a", LocalDateTime.of(2025, 3, 1, 21, 0), minutes = 120),
        play("b", LocalDateTime.of(2025, 6, 1, 21, 0), minutes = 90),
        play("c", LocalDateTime.of(2025, 9, 1, 21, 0), minutes = 60)
    )

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun play(songId: String, start: LocalDateTime, minutes: Long): PlaybackEvent {
        val startMillis = millis(start)
        val endMillis = startMillis + TimeUnit.MINUTES.toMillis(minutes)
        return PlaybackEvent(
            songId = songId,
            timestamp = endMillis,
            durationMs = endMillis - startMillis,
            startTimestamp = startMillis,
            endTimestamp = endMillis
        )
    }

    private fun song(id: String, artist: String, art: String?) = Song(
        id = id,
        title = "Song $id",
        artist = artist,
        artistId = 1L,
        album = "Album $id",
        albumId = id.hashCode().toLong(),
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = art,
        duration = TimeUnit.MINUTES.toMillis(4),
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
