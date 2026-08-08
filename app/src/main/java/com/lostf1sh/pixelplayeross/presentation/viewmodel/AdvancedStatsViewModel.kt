package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_LISTENING_GOAL_MINUTES
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class AdvancedStatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    data class MusicPersonality(
        val title: String,
        val description: String,
        val emoji: String
    )

    data class AdvancedStatsUiState(
        val currentMonth: YearMonth = YearMonth.now(),
        val selectedDate: LocalDate = LocalDate.now(),
        val dailyDurations: Map<LocalDate, Long> = emptyMap(),
        val selectedDaySummary: PlaybackStatsSummary? = null,
        val hourlyDistribution: List<Long> = List(6) { 0L }, // 6 buckets of 4 hours
        val isLoading: Boolean = false,
        val isDaySummaryLoading: Boolean = false,
        
        // Redesign states
        val activeTab: Int = 0, // 0 = Daily Heatmap, 1 = Habits & Insights
        val selectedRange: StatsTimeRange = StatsTimeRange.WEEK,
        val rangeSummary: PlaybackStatsSummary? = null,
        val isRangeSummaryLoading: Boolean = false,

        // Listening goal (gamification)
        val goalMinutes: Int = DEFAULT_LISTENING_GOAL_MINUTES,
        val goalStreak: Int = 0
    )

    private val _uiState = MutableStateFlow(AdvancedStatsUiState())
    val uiState: StateFlow<AdvancedStatsUiState> = _uiState.asStateFlow()

    init {
        loadMonthData(YearMonth.now())
        loadSelectedDayData(LocalDate.now())
        loadRangeData(StatsTimeRange.WEEK)

        userPreferencesRepository.listeningGoalMinutesFlow
            .onEach { minutes ->
                _uiState.update { it.copy(goalMinutes = minutes) }
                refreshGoalStreak(minutes)
            }
            .launchIn(viewModelScope)

        playbackStatsRepository.refreshFlow
            .onEach { refreshGoalStreak(_uiState.value.goalMinutes) }
            .launchIn(viewModelScope)
    }

    fun setGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setListeningGoalMinutes(minutes)
        }
    }

    private fun refreshGoalStreak(goalMinutes: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    playbackStatsRepository.getGoalStreak(goalMinutes * 60_000L)
                }
            }.onSuccess { streak ->
                _uiState.update { it.copy(goalStreak = streak) }
            }.onFailure { t ->
                Timber.e(t, "Failed to compute goal streak")
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
        if (tabIndex == 1 && _uiState.value.rangeSummary == null) {
            loadRangeData(_uiState.value.selectedRange)
        }
    }

    fun selectRange(range: StatsTimeRange) {
        _uiState.update { it.copy(selectedRange = range) }
        loadRangeData(range)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadSelectedDayData(date)
    }

    fun changeMonth(month: YearMonth) {
        _uiState.update { it.copy(currentMonth = month) }
        loadMonthData(month)
    }

    private fun loadMonthData(month: YearMonth) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val start = month.atDay(1)
                    val end = month.atEndOfMonth()
                    playbackStatsRepository.getDailyPlaybackDurationMap(start, end)
                }
            }.onSuccess { map ->
                _uiState.update { it.copy(dailyDurations = map, isLoading = false) }
            }.onFailure { t ->
                Timber.e(t, "Failed to load daily durations map")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadSelectedDayData(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDaySummaryLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = musicRepository.getAllSongsOnce()
                    val summary = playbackStatsRepository.loadDaySummary(date, songs)
                    
                    val events = playbackStatsRepository.exportEventsForBackup()
                    val zoneId = java.time.ZoneId.systemDefault()
                    val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

                    val buckets = MutableList(6) { 0L }
                    for (event in events) {
                        if (event.timestamp in startOfDay until endOfDay) {
                            val localTime = java.time.Instant.ofEpochMilli(event.timestamp).atZone(zoneId).toLocalTime()
                            val hour = localTime.hour
                            val bucketIndex = (hour / 4).coerceIn(0, 5)
                            buckets[bucketIndex] += event.durationMs
                        }
                    }
                    Pair(summary, buckets)
                }
            }.onSuccess { (summary, buckets) ->
                _uiState.update { it.copy(selectedDaySummary = summary, hourlyDistribution = buckets, isDaySummaryLoading = false) }
            }.onFailure { t ->
                Timber.e(t, "Failed to load selected day data")
                _uiState.update { it.copy(isDaySummaryLoading = false) }
            }
        }
    }

    private fun loadRangeData(range: StatsTimeRange) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRangeSummaryLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = musicRepository.getAllSongsOnce()
                    playbackStatsRepository.loadSummary(range, songs)
                }
            }.onSuccess { summary ->
                _uiState.update { it.copy(rangeSummary = summary, isRangeSummaryLoading = false) }
            }.onFailure { t ->
                Timber.e(t, "Failed to load range stats summary for %s", range)
                _uiState.update { it.copy(isRangeSummaryLoading = false) }
            }
        }
    }

    fun getMusicPersonality(summary: PlaybackStatsSummary?): MusicPersonality {
        if (summary == null || summary.totalPlayCount == 0) {
            return MusicPersonality(
                title = "The Melodic Voyager",
                description = "Your listening patterns are taking shape. Keep enjoying your music to reveal your listener archetype!",
                emoji = "🎧"
            )
        }
        
        val uniqueSongs = summary.uniqueSongs
        val totalPlays = summary.totalPlayCount
        val uniqueRatio = uniqueSongs.toDouble() / totalPlays.coerceAtLeast(1)
        val peakDay = summary.peakDayLabel ?: ""
        
        return when {
            uniqueRatio > 0.65 -> MusicPersonality(
                title = "The Sonic Explorer",
                description = "You love discovery! You constantly seek out different tracks rather than playing the same ones on repeat.",
                emoji = "🧭"
            )
            uniqueRatio < 0.25 -> MusicPersonality(
                title = "The Loyal Devotee",
                description = "You find comfort in the classics. You have a curated circle of favorite tracks that you love repeating.",
                emoji = "❤️"
            )
            summary.averageDailyDurationMs > 2.5 * 3600 * 1000L -> MusicPersonality(
                title = "The Music Enthusiast",
                description = "Music is the soundtrack to your life! You spend hours every day immersed in different melodies.",
                emoji = "🎵"
            )
            peakDay.contains("Friday", ignoreCase = true) || 
            peakDay.contains("Saturday", ignoreCase = true) || 
            peakDay.contains("Sunday", ignoreCase = true) -> MusicPersonality(
                title = "The Weekend Viber",
                description = "You let loose with music on the weekends! Your listening activity spikes when it's time to relax.",
                emoji = "🎉"
            )
            else -> MusicPersonality(
                title = "The Balanced Listener",
                description = "You keep a perfectly balanced habit. Music accompanies you in just the right moments to keep your day smooth.",
                emoji = "🎶"
            )
        }
    }
}
