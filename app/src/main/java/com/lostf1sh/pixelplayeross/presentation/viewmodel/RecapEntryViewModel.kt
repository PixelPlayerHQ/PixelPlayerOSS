package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.recap.RecapEntry
import com.lostf1sh.pixelplayeross.presentation.recap.RecapSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Decides which recaps the Stats screen and the seasonal Home card offer. Both flows only
 * compute while collected, so a screen that shows one of them never pays for the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapEntryViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    dispatchers: DispatcherProvider
) : ViewModel() {

    /** Source of "now"; null follows the system clock and zone. Replaced in tests before collecting. */
    @VisibleForTesting
    internal var clock: Clock? = null

    /**
     * Today's date, emitted again whenever it changes while collected: at midnight, and within
     * [DATE_CHECK_INTERVAL_MS] of the clock or time zone being changed. Without it a screen left
     * open across midnight would keep offering yesterday's recaps.
     */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock ?: Clock.systemDefaultZone())
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceIn(1L, DATE_CHECK_INTERVAL_MS))
        }
    }.distinctUntilChanged()

    /** Recaps offered on the Stats screen, most relevant first; empty when none qualifies. */
    val statsEntries: StateFlow<ImmutableList<RecapEntry>> = combine(
        playbackStatsRepository.refreshFlow,
        today
    ) { _, today -> today }
        .mapLatest { today ->
            val totals = playbackStatsRepository.loadRecapYearTotals()
            RecapSchedule.statsEntries(today, totals).toImmutableList()
        }
        .distinctUntilChanged()
        .catch { throwable ->
            Timber.e(throwable, "Failed to load recap entries")
            emit(persistentListOf())
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

    /**
     * The seasonal recap card on Home: December through mid-January, for a year with enough
     * listening, until the user dismisses it for that year. History is only read in season.
     */
    val homeCard: StateFlow<RecapEntry?> = combine(
        playbackStatsRepository.refreshFlow,
        userPreferencesRepository.recapHomeCardDismissedYearFlow,
        today
    ) { _, dismissedYear, today -> dismissedYear to today }
        .mapLatest { (dismissedYear, today) ->
            val year = RecapSchedule.homeCardYear(today)
            if (year == null || year == dismissedYear) return@mapLatest null
            val totals = playbackStatsRepository.loadRecapYearTotals()
            if (!RecapSchedule.hasEnoughListening(year, totals)) return@mapLatest null
            RecapEntry(year = year, isSoFar = RecapSchedule.isSoFar(year, today))
        }
        .distinctUntilChanged()
        .catch { throwable ->
            Timber.e(throwable, "Failed to load the seasonal recap card")
            emit(null)
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun dismissHomeCard(year: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setRecapHomeCardDismissedYear(year)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How often a collected date looks again, in case the clock or time zone was changed. */
        val DATE_CHECK_INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(1)
    }
}
