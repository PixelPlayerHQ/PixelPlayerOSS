package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.RecapYearTotal
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.recap.RecapEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class RecapEntryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("Asia/Seoul")
    private val enough = TimeUnit.HOURS.toMillis(6)

    private val refreshVersion = MutableStateFlow(0L)

    @Volatile
    private var totals: List<RecapYearTotal> = emptyList()

    private val playbackStatsRepository = mockk<PlaybackStatsRepository> {
        every { refreshFlow } returns refreshVersion
        coEvery { loadRecapYearTotals() } coAnswers { totals }
    }

    private val dismissedYear = MutableStateFlow(0)

    private val userPreferencesRepository = mockk<UserPreferencesRepository> {
        every { recapHomeCardDismissedYearFlow } returns dismissedYear
        coEvery { setRecapHomeCardDismissedYear(any()) } coAnswers { dismissedYear.value = firstArg() }
    }

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.Main
        override val default: CoroutineDispatcher get() = Dispatchers.Main
    }

    @Test
    fun `home card is null outside december and early january without reading history`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        val viewModel = createViewModel(today = LocalDate.of(2026, 10, 23))

        assertThat(currentValue(viewModel.homeCard)).isNull()
        coVerify(exactly = 0) { playbackStatsRepository.loadRecapYearTotals() }
    }

    @Test
    fun `home card shown in december when the year has enough listening`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        val viewModel = createViewModel(today = LocalDate.of(2026, 12, 20))

        assertThat(currentValue(viewModel.homeCard)).isEqualTo(RecapEntry(year = 2026, isSoFar = false))
    }

    @Test
    fun `home card offers the year that just ended in early january`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough), RecapYearTotal(2027, enough))
        val viewModel = createViewModel(today = LocalDate.of(2027, 1, 9))

        assertThat(currentValue(viewModel.homeCard)).isEqualTo(RecapEntry(year = 2026, isSoFar = false))
    }

    @Test
    fun `home card hidden when that year has too little listening`() = runTest {
        totals = listOf(RecapYearTotal(2026, TimeUnit.MINUTES.toMillis(10)))
        val viewModel = createViewModel(today = LocalDate.of(2026, 12, 20))

        assertThat(currentValue(viewModel.homeCard)).isNull()
    }

    @Test
    fun `home card hidden after dismissing that year`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        val december = createViewModel(today = LocalDate.of(2026, 12, 20))
        assertThat(currentValue(december.homeCard)).isNotNull()

        december.dismissHomeCard(2026)
        runCurrent()

        assertThat(december.homeCard.value).isNull()
        // Still dismissed in January, which offers the same year.
        val january = createViewModel(today = LocalDate.of(2027, 1, 3))
        assertThat(currentValue(january.homeCard)).isNull()
    }

    @Test
    fun `stats entries list qualifying years`() = runTest {
        totals = listOf(RecapYearTotal(2024, enough), RecapYearTotal(2025, enough), RecapYearTotal(2026, enough))
        val viewModel = createViewModel(today = LocalDate.of(2026, 10, 23))

        assertThat(currentValue(viewModel.statsEntries)).containsExactly(
            RecapEntry(year = 2025, isSoFar = false),
            RecapEntry(year = 2026, isSoFar = true)
        ).inOrder()
    }

    @Test
    fun `stats entries follow newly recorded listening`() = runTest {
        val viewModel = createViewModel(today = LocalDate.of(2026, 10, 23))
        assertThat(currentValue(viewModel.statsEntries)).isEmpty()

        totals = listOf(RecapYearTotal(2026, enough))
        refreshVersion.update { it + 1 }
        runCurrent()

        assertThat(viewModel.statsEntries.value).containsExactly(RecapEntry(year = 2026, isSoFar = true))
    }

    @Test
    fun `home card appears when december starts while home stays open`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        val viewModel = createViewModel(clock = virtualClock(LocalDateTime.of(2026, 11, 30, 23, 58)))
        assertThat(currentValue(viewModel.homeCard)).isNull()

        // No play completes and the preference stays put: only the date moves on.
        advanceTimeBy(TimeUnit.MINUTES.toMillis(5))
        runCurrent()

        assertThat(viewModel.homeCard.value).isEqualTo(RecapEntry(year = 2026, isSoFar = false))
    }

    @Test
    fun `home card leaves after january 15 while home stays open`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        val viewModel = createViewModel(clock = virtualClock(LocalDateTime.of(2027, 1, 15, 23, 30)))
        assertThat(currentValue(viewModel.homeCard)).isEqualTo(RecapEntry(year = 2026, isSoFar = false))

        advanceTimeBy(TimeUnit.HOURS.toMillis(1))
        runCurrent()

        assertThat(viewModel.homeCard.value).isNull()
    }

    @Test
    fun `stats entries move on at new year while stats stays open`() = runTest {
        totals = listOf(RecapYearTotal(2025, enough), RecapYearTotal(2026, enough))
        val viewModel = createViewModel(clock = virtualClock(LocalDateTime.of(2026, 12, 31, 23, 59)))
        assertThat(currentValue(viewModel.statsEntries)).containsExactly(
            RecapEntry(year = 2026, isSoFar = false),
            RecapEntry(year = 2025, isSoFar = false)
        ).inOrder()

        advanceTimeBy(TimeUnit.MINUTES.toMillis(2))
        runCurrent()

        // 2025 is no longer the running or the previous year.
        assertThat(viewModel.statsEntries.value).containsExactly(RecapEntry(year = 2026, isSoFar = false))
    }

    @Test
    fun `home card follows a time zone change that crosses into december`() = runTest {
        totals = listOf(RecapYearTotal(2026, enough))
        // One instant: still November 30 in UTC, already December 1 in Seoul.
        val instant = LocalDateTime.of(2026, 12, 1, 1, 0).atZone(zone).toInstant()
        val viewModel = createViewModel(clock = Clock.fixed(instant, ZoneId.of("UTC")))
        assertThat(currentValue(viewModel.homeCard)).isNull()

        viewModel.clock = Clock.fixed(instant, zone)
        advanceTimeBy(TimeUnit.MINUTES.toMillis(2))
        runCurrent()

        assertThat(viewModel.homeCard.value).isEqualTo(RecapEntry(year = 2026, isSoFar = false))
    }

    private fun createViewModel(today: LocalDate) =
        createViewModel(clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone))

    private fun createViewModel(clock: Clock) = RecapEntryViewModel(
        playbackStatsRepository = playbackStatsRepository,
        userPreferencesRepository = userPreferencesRepository,
        dispatchers = dispatchers
    ).apply {
        this.clock = clock
    }

    /** A clock that starts at [start] and runs with the test's virtual time. */
    private fun TestScope.virtualClock(start: LocalDateTime): Clock {
        val clockZone = zone
        val startInstant = start.atZone(clockZone).toInstant()
        val scheduler = testScheduler
        return object : Clock() {
            override fun getZone(): ZoneId = clockZone
            override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
            override fun instant(): Instant = startInstant.plusMillis(scheduler.currentTime)
        }
    }

    /**
     * Subscribes like a screen would (the flows only compute while collected) and settles. Runs
     * only what is due now: the date check repeats while collected, so it never goes idle.
     */
    private fun <T> TestScope.currentValue(flow: StateFlow<T>): T {
        backgroundScope.launch { flow.collect { } }
        runCurrent()
        return flow.value
    }
}
