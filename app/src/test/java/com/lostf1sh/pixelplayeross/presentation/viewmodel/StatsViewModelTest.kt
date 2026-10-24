package com.lostf1sh.pixelplayeross.presentation.viewmodel

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
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(testDispatcher)

    // Loads run on the test scheduler: one still running on a real pool when a test ends would
    // resume through Dispatchers.Main after it is reset and fail a later test.
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    /**
     * Builds summaries with the real aggregation code. Its history file is not used: AtomicFile is
     * a no-op stub in JVM tests, so the "recorded" plays live in [events] instead.
     */
    private val summaryBuilder = PlaybackStatsRepository(
        mockk(relaxed = true) {
            every { filesDir } returns createTempDirectory("stats-view-model-test-").toFile()
            every {
                getString(R.string.presentation_batch_g_stats_week_label, *anyVararg())
            } returns "Week"
        }
    )

    @Volatile
    private var events: List<PlaybackEvent> = emptyList()

    @Volatile
    private var loadGate: CompletableDeferred<Unit>? = null

    @Volatile
    private var loadDelayMs = 0L

    @Volatile
    private var loadFailure: Exception? = null

    private val refreshVersion = MutableStateFlow(0L)

    private val playbackStatsRepository = mockk<PlaybackStatsRepository> {
        every { refreshFlow } returns refreshVersion
        every { requestRefresh() } answers { refreshVersion.update { it + 1 } }
        coEvery { loadSummary(any(), any(), any()) } coAnswers {
            loadGate?.await()
            if (loadDelayMs > 0L) delay(loadDelayMs)
            loadFailure?.let { throw it }
            summaryBuilder.buildSummaryFromEvents(
                range = firstArg(),
                songs = secondArg(),
                nowMillis = thirdArg(),
                allEvents = events
            )
        }
    }

    private val musicRepository = mockk<MusicRepository>(relaxed = true) {
        coEvery { getAllSongsOnce() } returns listOf(song("s1"), song("s2"))
    }

    private val artistImageRepository = mockk<ArtistImageRepository>(relaxed = true)

    @Test
    fun `each genre of a multi-genre song opens as a library genre`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns
            listOf(song("s1").copy(genre = "Alternative; Indie Rock"))
        val viewModel = createViewModel()

        assertThat(viewModel.isLibraryGenre("Indie Rock")).isTrue()
        assertThat(viewModel.isLibraryGenre("alternative")).isTrue()
        assertThat(viewModel.isLibraryGenre("Alternative Rock")).isFalse()
        assertThat(viewModel.isLibraryGenre("Unknown Genre")).isFalse()
    }

    @Test
    fun `home overview is Empty instead of hidden when there is no listening history`() = runTest {
        loadGate = CompletableDeferred()
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            assertThat(awaitItem()).isEqualTo(HomeStatsOverviewState.Loading)
            loadGate?.complete(Unit)
            assertThat(awaitItem()).isEqualTo(HomeStatsOverviewState.Empty)
        }
    }

    @Test
    fun `home overview is Ready for the week after a play is recorded`() = runTest {
        events = listOf(playThisWeek("s1"))
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            val state = awaitFirstResult()

            assertThat(state).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
            val summary = (state as HomeStatsOverviewState.Ready).summary
            assertThat(summary.range).isEqualTo(StatsTimeRange.WEEK)
            assertThat(summary.totalPlayCount).isEqualTo(1)
        }
    }

    @Test
    fun `home overview falls back to all time when only old plays exist`() = runTest {
        val longAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(400)
        events = listOf(play("s1", startMillis = longAgo, durationMs = 60_000L))
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            val state = awaitFirstResult()

            assertThat(state).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
            assertThat((state as HomeStatsOverviewState.Ready).summary.range)
                .isEqualTo(StatsTimeRange.ALL)
        }
    }

    @Test
    fun `home overview switches from Empty to Ready when the repository records a play`() = runTest {
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            assertThat(awaitFirstResult()).isEqualTo(HomeStatsOverviewState.Empty)

            // What recordPlayback does once the play is persisted.
            events = listOf(playThisWeek("s1"))
            playbackStatsRepository.requestRefresh()

            val state = awaitItem()
            assertThat(state).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
            assertThat((state as HomeStatsOverviewState.Ready).summary.totalPlayCount).isEqualTo(1)
        }
    }

    @Test
    fun `home overview is Empty when loading songs fails`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } throws IllegalStateException("library unavailable")
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            assertThat(awaitFirstResult()).isEqualTo(HomeStatsOverviewState.Empty)
        }
    }

    @Test
    fun `a superseded refresh does not publish Empty before the newer result`() = runTest {
        events = listOf(playThisWeek("s1"))
        loadGate = CompletableDeferred()
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            assertThat(awaitItem()).isEqualTo(HomeStatsOverviewState.Loading)

            // Replaces the initial refresh while it is still loading.
            viewModel.refreshHomeOverview()
            loadGate?.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
        }
    }

    @Test
    fun `back to back refreshes settle on the newest summary without passing through Empty`() = runTest {
        events = listOf(playThisWeek("s1"))
        val viewModel = createViewModel()

        viewModel.homeOverviewState.test {
            assertThat(awaitFirstResult()).isInstanceOf(HomeStatsOverviewState.Ready::class.java)

            events = listOf(playThisWeek("s1"), playThisWeek("s2", endsAgoMs = 120_000L))
            // Slow loads guarantee the first two refreshes are cancelled while in flight.
            loadDelayMs = 200L
            viewModel.refreshHomeOverview()
            viewModel.refreshHomeOverview()
            viewModel.refreshHomeOverview()

            do {
                val state = awaitItem()
                assertThat(state).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
            } while ((state as HomeStatsOverviewState.Ready).summary.totalPlayCount != 2)
        }
    }

    @Test
    fun `a failed refresh keeps the last Ready summary`() = runTest {
        events = listOf(playThisWeek("s1"))
        val failure = IllegalStateException("history unreadable")
        val failureLogged = CompletableDeferred<Unit>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // Coroutine stack-trace recovery may hand over a copy that wraps the original.
                if (generateSequence(t) { it.cause }.any { it === failure }) {
                    failureLogged.complete(Unit)
                }
            }
        }
        Timber.plant(tree)
        try {
            val viewModel = createViewModel()

            viewModel.homeOverviewState.test {
                assertThat(awaitFirstResult()).isInstanceOf(HomeStatsOverviewState.Ready::class.java)

                loadFailure = failure
                viewModel.refreshHomeOverview()
                failureLogged.await()

                loadFailure = null
                events = listOf(playThisWeek("s1"), playThisWeek("s2", endsAgoMs = 120_000L))
                viewModel.refreshHomeOverview()

                // The next state is the new summary, not an Empty left behind by the failure.
                val state = awaitItem()
                assertThat(state).isInstanceOf(HomeStatsOverviewState.Ready::class.java)
                assertThat((state as HomeStatsOverviewState.Ready).summary.totalPlayCount)
                    .isEqualTo(2)
            }
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `firstSummaryWithActivity returns the first active range and stops loading after it`() = runTest {
        val loaded = mutableListOf<StatsTimeRange>()

        val result = firstSummaryWithActivity(HomeRanges) { range ->
            loaded += range
            if (range == StatsTimeRange.MONTH) activeSummary(range) else emptySummary(range)
        }

        assertThat(result?.range).isEqualTo(StatsTimeRange.MONTH)
        assertThat(loaded).containsExactly(StatsTimeRange.WEEK, StatsTimeRange.MONTH).inOrder()
    }

    @Test
    fun `firstSummaryWithActivity returns null when no range has activity`() = runTest {
        val loaded = mutableListOf<StatsTimeRange>()

        val result = firstSummaryWithActivity(HomeRanges) { range ->
            loaded += range
            emptySummary(range)
        }

        assertThat(result).isNull()
        assertThat(loaded).containsExactlyElementsIn(HomeRanges).inOrder()
    }

    private fun createViewModel() = StatsViewModel(
        playbackStatsRepository = playbackStatsRepository,
        musicRepository = musicRepository,
        artistImageRepository = artistImageRepository,
        dispatchers = dispatchers
    )

    /** Skips the initial Loading state when the first load has not finished yet. */
    private suspend fun ReceiveTurbine<HomeStatsOverviewState>.awaitFirstResult(): HomeStatsOverviewState {
        val first = awaitItem()
        return if (first == HomeStatsOverviewState.Loading) awaitItem() else first
    }

    private fun emptySummary(range: StatsTimeRange): PlaybackStatsSummary =
        summaryBuilder.buildSummaryFromEvents(
            range = range,
            songs = listOf(song("s1")),
            nowMillis = System.currentTimeMillis(),
            allEvents = emptyList()
        )

    private fun activeSummary(range: StatsTimeRange): PlaybackStatsSummary =
        summaryBuilder.buildSummaryFromEvents(
            range = range,
            songs = listOf(song("s1")),
            nowMillis = System.currentTimeMillis(),
            allEvents = listOf(playThisWeek("s1"))
        )

    /** A one-minute play that ends [endsAgoMs] before now, clipped to the current week. */
    private fun playThisWeek(songId: String, endsAgoMs: Long = 0L): PlaybackEvent {
        val now = System.currentTimeMillis()
        val weekStart = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val end = (now - endsAgoMs).coerceAtLeast(weekStart + 2L)
        val start = (end - 60_000L).coerceAtLeast(weekStart + 1L)
        return play(songId, startMillis = start, durationMs = end - start)
    }

    private fun play(songId: String, startMillis: Long, durationMs: Long) = PlaybackEvent(
        songId = songId,
        timestamp = startMillis + durationMs,
        durationMs = durationMs,
        startTimestamp = startMillis,
        endTimestamp = startMillis + durationMs
    )

    private fun song(songId: String): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = 5 * 60 * 1000L,
        genre = null,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )

    private companion object {
        val HomeRanges = listOf(
            StatsTimeRange.WEEK,
            StatsTimeRange.MONTH,
            StatsTimeRange.YEAR,
            StatsTimeRange.ALL
        )
    }
}
