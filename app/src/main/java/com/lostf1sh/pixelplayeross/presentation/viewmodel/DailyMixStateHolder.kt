package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Generate and update daily/your mixes
 * - Persist and restore mix state
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    private val owners = OwnerStack<CoroutineScope> { it }

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        owners.push(coroutineScope)
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    /** Replaces today's Daily Mix (e.g. with a Smart Mix preview) and persists it. */
    fun setDailyMixSongs(songs: List<Song>) {
        updateJob?.cancel()
        _dailyMixSongs.value = songs.toImmutableList()
        updateJob = scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    /**
     * Update the daily mix with new songs.
     * Uses getAllSongsOnce() to load songs on-demand instead of keeping a permanent subscription.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            generateMixes(favoriteSongIdsFlow)
        }
    }

    private suspend fun generateMixes(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        val allSongs = musicRepository.getAllSongsOnce()
        if (allSongs.isNotEmpty()) {
            val favoriteIds = favoriteSongIdsFlow.first()

            val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
            _dailyMixSongs.value = mix.toImmutableList()
            userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

            val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
            _yourMixSongs.value = yourMix.toImmutableList()
            userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
        } else {
            _yourMixSongs.value = persistentListOf()
        }
    }

    /**
     * Load persisted daily mix from storage using direct DB queries by IDs
     * instead of combining with the full allSongs flow.
     */
    fun loadPersistedDailyMix() {
        scope?.launch {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(dailyMixIds).first()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }
                    _dailyMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }

        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(yourMixIds).first()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }
                    _yourMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Generates the mixes again on a new day, or when none of today's Daily Mix is in the
     * library (e.g. the library was still empty at the day's first check). Generating ranks the
     * whole library, so finished syncs and cold starts call this rather than [updateDailyMix].
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        // The check or update already running covers this call. Starting another one would read
        // the whole library again while the first copy is still in memory.
        if (updateJob?.isActive == true) return
        updateJob = scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val now = System.currentTimeMillis()
            if (!isDifferentDay(lastUpdate, now) && hasDailyMixInLibrary()) return@launch

            withContext(Dispatchers.IO) { generateMixes(favoriteSongIdsFlow) }
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(now)
        }
    }

    private suspend fun hasDailyMixInLibrary(): Boolean {
        val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
        return dailyMixIds.isNotEmpty() && musicRepository.getSongsByIds(dailyMixIds).first().isNotEmpty()
    }

    /**
     * A stale PlayerViewModel clearing leaves the newer binding alone; the current one clearing
     * hands the holder back to the previous live PlayerViewModel instead of leaving it
     * scope-less, which would stop Daily Mix updates until process restart.
     */
    fun onCleared(owner: CoroutineScope) {
        owners.remove(owner) { next ->
            if (next == null) updateJob?.cancel()
            scope = next
        }
    }
}

/** Whether the two instants fall on different calendar days in [zoneId]. */
internal fun isDifferentDay(
    firstMillis: Long,
    secondMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean =
    Instant.ofEpochMilli(firstMillis).atZone(zoneId).toLocalDate() !=
        Instant.ofEpochMilli(secondMillis).atZone(zoneId).toLocalDate()
