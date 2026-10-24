package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.stats.resolveStatsArtistImages
import com.lostf1sh.pixelplayeross.utils.GenreParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    data class StatsUiState(
        val selectedRange: StatsTimeRange = StatsTimeRange.WEEK,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val summary: PlaybackStatsSummary? = null,
        val availableRanges: ImmutableList<StatsTimeRange> = StatsTimeRange.entries.toImmutableList()
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _weeklyOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val weeklyOverview: StateFlow<PlaybackStatsSummary?> = _weeklyOverview.asStateFlow()

    private val _homeOverviewState =
        MutableStateFlow<HomeStatsOverviewState>(HomeStatsOverviewState.Loading)
    val homeOverviewState: StateFlow<HomeStatsOverviewState> = _homeOverviewState.asStateFlow()

    /** Only the latest home refresh may publish, so an older result never overwrites a newer one. */
    private var homeOverviewJob: Job? = null

    @Volatile
    private var cachedSongs: List<Song>? = null

    init {
        observeStatsRefreshFlow()
        refreshRange(
            range = StatsTimeRange.WEEK,
            showLoading = true,
            updateWeeklyOverview = true
        )
        refreshHomeOverview()
    }

    fun onRangeSelected(range: StatsTimeRange) {
        if (range == _uiState.value.selectedRange && !_uiState.value.isLoading) {
            return
        }
        refreshRange(
            range = range,
            showLoading = true,
            updateWeeklyOverview = range == StatsTimeRange.WEEK
        )
    }

    fun refreshWeeklyOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(dispatchers.io) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                }
            }.onSuccess { summary ->
                _weeklyOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load weekly stats overview")
                _weeklyOverview.value = null
            }
        }
    }

    fun refreshHomeOverview() {
        homeOverviewJob?.cancel()
        homeOverviewJob = viewModelScope.launch {
            val summary = try {
                withContext(dispatchers.io) {
                    val songs = loadSongs()
                    firstSummaryWithActivity(HomeOverviewRanges) { range ->
                        playbackStatsRepository.loadSummary(range, songs)
                    }
                }
            } catch (cancellation: CancellationException) {
                // A newer refresh replaced this one; it publishes the result instead.
                throw cancellation
            } catch (throwable: Exception) {
                Timber.e(throwable, "Failed to load home stats overview")
                // Keep showing the last good summary rather than flashing the empty state.
                if (_homeOverviewState.value !is HomeStatsOverviewState.Ready) {
                    _homeOverviewState.value = HomeStatsOverviewState.Empty
                }
                return@launch
            }
            _homeOverviewState.value = summary
                ?.let { HomeStatsOverviewState.Ready(it) }
                ?: HomeStatsOverviewState.Empty
        }
    }

    private fun refreshRange(
        range: StatsTimeRange,
        showLoading: Boolean = true,
        updateWeeklyOverview: Boolean = false
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false, selectedRange = range) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, selectedRange = range) }
            }
            val summary = runCatching {
                withContext(dispatchers.io) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(range, songs)
                }
            }
            summary.getOrNull()?.let { loaded ->
                if (updateWeeklyOverview) {
                    _weeklyOverview.value = loaded
                }
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    summary = summary.getOrNull(),
                    selectedRange = range
                )
            }
            summary.exceptionOrNull()?.let { Timber.e(it, "Failed to load stats for range %s", range) }
        }
    }

    private fun observeStatsRefreshFlow() {
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow
                .drop(1)
                .collectLatest {
                    val selectedRange = _uiState.value.selectedRange
                    refreshRange(
                        range = selectedRange,
                        showLoading = false,
                        updateWeeklyOverview = selectedRange == StatsTimeRange.WEEK
                    )
                    if (selectedRange != StatsTimeRange.WEEK) {
                        refreshWeeklyOverview()
                    }
                    refreshHomeOverview()
                }
        }
    }

    fun requestStatsRefresh() {
        playbackStatsRepository.requestRefresh()
    }

    fun forceRegenerateStats() {
        cachedSongs = null
        playbackStatsRepository.requestRefresh()
    }

    private suspend fun loadSongs(): List<Song> {
        cachedSongs?.let { existing ->
            if (existing.isNotEmpty()) return existing
        }
        val songs = musicRepository.getAllSongsOnce()
        cachedSongs = songs
        return songs
    }

    /** Library songs for [songIds], in the given order; ids no longer in the library are dropped. */
    suspend fun resolveSongs(songIds: List<String>): List<Song> = withContext(dispatchers.io) {
        val byId = loadSongs().associateBy { it.id }
        songIds.mapNotNull { byId[it] }
    }

    /**
     * Stats group artists by display name, so the id has to be looked up. Falls back to the
     * library songs for names the artists table does not know (e.g. multi-artist display names).
     */
    suspend fun resolveArtistId(name: String): Long? = withContext(dispatchers.io) {
        musicRepository.getArtistIdByName(name)
            ?: loadSongs().firstOrNull { it.displayArtist == name || it.artist == name }?.artistId
    }

    /**
     * The artist pictures the rest of the app shows: the user's custom image or a local folder
     * portrait, otherwise the Deezer picture when external artist images are enabled.
     * Names with no picture are absent.
     */
    suspend fun resolveArtistImages(names: Collection<String>): Map<String, String> =
        withContext(dispatchers.io) {
            resolveStatsArtistImages(names, musicRepository, artistImageRepository)
        }

    /**
     * Stats group albums by title, which is ambiguous across artists. Prefer an album that owns
     * one of [preferredSongIds] (the tracks actually played in range) before any namesake.
     */
    suspend fun resolveAlbumId(name: String, preferredSongIds: Collection<String>): Long? =
        withContext(dispatchers.io) {
            val preferred = preferredSongIds.toSet()
            val candidates = loadSongs().filter { it.album == name }
            (candidates.firstOrNull { it.id in preferred } ?: candidates.firstOrNull())?.albumId
        }

    /** False for the synthetic "unknown" bucket stats create for songs without a genre tag. */
    suspend fun isLibraryGenre(name: String): Boolean = withContext(dispatchers.io) {
        loadSongs().any { GenreParser.matches(it.genre, name) }
    }

    private companion object {
        val HomeOverviewRanges = listOf(
            StatsTimeRange.WEEK,
            StatsTimeRange.MONTH,
            StatsTimeRange.YEAR,
            StatsTimeRange.ALL
        )
    }
}

/**
 * Loads [ranges] in order and returns the first summary with listening activity, or null when
 * none has any. Ranges after the first match are never loaded.
 */
internal suspend fun firstSummaryWithActivity(
    ranges: List<StatsTimeRange>,
    load: suspend (StatsTimeRange) -> PlaybackStatsSummary
): PlaybackStatsSummary? {
    for (range in ranges) {
        val summary = load(range)
        if (summary.hasListeningActivity()) return summary
    }
    return null
}
