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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var checkJob: Job? = null

    /** Set by every [checkAndUpdateIfNeeded] call; the running check repeats until it is clear. */
    @Volatile
    private var checkRequested = false

    /**
     * When [setDailyMixSongs] last replaced the Daily Mix, set at once; the saved copy is written
     * a moment later and covers restarts. A generation that day keeps the replacement.
     */
    @Volatile
    private var dailyMixReplacedAtMillis = 0L

    /**
     * Guards [dailyMixReplacements] together with the shown Daily Mix, so a generated mix is only
     * shown if no replacement arrived while it was being made.
     */
    private val dailyMixLock = Any()

    /** Counts [setDailyMixSongs] calls; guarded by [dailyMixLock]. */
    private var dailyMixReplacements = 0

    /** Orders the saved Daily Mix ids the same way the shown ones changed. */
    private val dailyMixSaveMutex = Mutex()
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
        val replacedAt = System.currentTimeMillis()
        val replacement = synchronized(dailyMixLock) {
            dailyMixReplacedAtMillis = replacedAt
            _dailyMixSongs.value = songs.toImmutableList()
            ++dailyMixReplacements
        }
        updateJob = scope?.launch {
            dailyMixSaveMutex.withLock {
                // A newer replacement saves its own songs.
                if (replacement != synchronized(dailyMixLock) { dailyMixReplacements }) return@withLock
                userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
                userPreferencesRepository.saveDailyMixReplacedAt(replacedAt)
            }
        }
    }

    /**
     * Update the daily mix with new songs.
     * Uses getAllSongsOnce() to load songs on-demand instead of keeping a permanent subscription.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        checkJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            generateMixes(favoriteSongIdsFlow, keepReplacedDailyMix = false)
        }
    }

    private suspend fun generateMixes(
        favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>,
        keepReplacedDailyMix: Boolean
    ) {
        val replacementsBefore = synchronized(dailyMixLock) { dailyMixReplacements }
        val allSongs = musicRepository.getAllSongsOnce()
        if (allSongs.isNotEmpty()) {
            val favoriteIds = favoriteSongIdsFlow.first()

            val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
            if (!(keepReplacedDailyMix && isKeepingReplacedDailyMix())) {
                dailyMixSaveMutex.withLock {
                    val shown = synchronized(dailyMixLock) {
                        // A Daily Mix the user applied while this one was made wins, even if
                        // the checks above still saw the one before it.
                        (dailyMixReplacements == replacementsBefore).also { unchanged ->
                            if (unchanged) _dailyMixSongs.value = mix.toImmutableList()
                        }
                    }
                    if (shown) userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })
                }
            }

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
        checkRequested = true
        // A check already running goes again once it finishes (e.g. to see the songs of a sync
        // that ended meanwhile), rather than a second check reading the whole library while the
        // first one's copy is still in memory.
        if (checkJob?.isActive == true) return
        checkJob = scope?.launch {
            while (checkRequested) {
                checkRequested = false
                // A regeneration from Regenerate Daily Mix finishes first, so two never read
                // the library at once.
                updateJob?.join()
                updateIfDue(favoriteSongIdsFlow)
            }
        }
    }

    private suspend fun updateIfDue(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
        val now = System.currentTimeMillis()
        if (!isDifferentDay(lastUpdate, now) && hasDailyMixInLibrary()) {
            refreshTodaysMixes()
            return
        }
        withContext(Dispatchers.IO) { generateMixes(favoriteSongIdsFlow, keepReplacedDailyMix = true) }
        userPreferencesRepository.saveLastDailyMixUpdateTimestamp(now)
    }

    /**
     * Whether the user replaced the Daily Mix today and some of it is still in the library. A
     * replacement whose songs a sync removed is generated over like any other mix.
     */
    private suspend fun isKeepingReplacedDailyMix(): Boolean {
        val replacedAt = maxOf(dailyMixReplacedAtMillis, userPreferencesRepository.dailyMixReplacedAtFlow.first())
        if (isDifferentDay(replacedAt, System.currentTimeMillis())) return false
        // The shown mix is the replacement as soon as it is applied, while its saved ids may still
        // be on their way; after a restart nothing may be shown yet, so the saved ids decide.
        val replacementIds = _dailyMixSongs.value.map { it.id }
            .ifEmpty { userPreferencesRepository.dailyMixSongIdsFlow.first() }
        return replacementIds.isNotEmpty() &&
            musicRepository.getSongsByIds(replacementIds).first().isNotEmpty()
    }

    private suspend fun hasDailyMixInLibrary(): Boolean {
        val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
        return dailyMixIds.isNotEmpty() && musicRepository.getSongsByIds(dailyMixIds).first().isNotEmpty()
    }

    /** Keeps today's picks and their order, minus songs a sync removed, with current details. */
    private suspend fun refreshTodaysMixes() {
        val shownIds = (_dailyMixSongs.value + _yourMixSongs.value).map { it.id }.distinct()
        if (shownIds.isEmpty()) return
        val inLibrary = musicRepository.getSongsByIds(shownIds).first().associateBy { it.id }
        val checkedIds = shownIds.toSet()
        // A song that joined a mix after the read (e.g. the saved mix finished loading) stays.
        fun List<Song>.refreshed() = mapNotNull { song ->
            if (song.id in checkedIds) inLibrary[song.id] else song
        }.toImmutableList()
        _dailyMixSongs.update { it.refreshed() }
        _yourMixSongs.update { it.refreshed() }
    }

    /**
     * A stale PlayerViewModel clearing leaves the newer binding alone; the current one clearing
     * hands the holder back to the previous live PlayerViewModel instead of leaving it
     * scope-less, which would stop Daily Mix updates until process restart.
     */
    fun onCleared(owner: CoroutineScope) {
        owners.remove(owner) { next ->
            if (next == null) {
                updateJob?.cancel()
                checkJob?.cancel()
            }
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
