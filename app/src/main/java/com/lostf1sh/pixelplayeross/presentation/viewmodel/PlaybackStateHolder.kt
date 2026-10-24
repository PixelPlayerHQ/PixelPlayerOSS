package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import com.lostf1sh.pixelplayeross.data.preferences.PreviousButtonBehavior
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.shouldRestartCurrentTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.lostf1sh.pixelplayeross.data.model.Song
import timber.log.Timber
import com.lostf1sh.pixelplayeross.utils.QueueUtils
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import kotlin.math.abs

@Singleton
class PlaybackStateHolder @Inject constructor(
    private val dualPlayerEngine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val queueStateHolder: QueueStateHolder,
    @param:ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "PlaybackStateHolder"
        private const val DURATION_MISMATCH_TOLERANCE_MS = 1500L
        private const val PAUSED_OVERRIDE_MAX_AGE_MS = 4_000L
        private const val SLIDER_TICK_MS = 250L
        private const val MINIPLAYER_TICK_MS = 1000L
        private const val BACKGROUND_TICK_MS = 1000L
        /**
         * Threshold above which we skip per-item moveMediaItem calls and use
         * a single setMediaItems call instead. moveMediaItem triggers an IPC
         * round-trip for each call, which freezes the UI on large queues.
         */
        private const val BULK_REPLACE_THRESHOLD = 80
        private const val SHUFFLE_TOGGLE_COOLDOWN_MS = 400L
    }

    @Volatile
    private var scope: CoroutineScope? = null
    private val owners = OwnerStack<CoroutineScope> { it }

    /** Mirrors the "Auto-play on skip" preference; the default matches the repository's. */
    @Volatile
    private var autoplayOnSkip: Boolean = true

    /** Mirrors the "Previous button" preference. */
    @Volatile
    private var previousButtonBehavior: PreviousButtonBehavior = PreviousButtonBehavior.DEFAULT

    var mediaController: MediaController? = null
        private set
    private val mediaControllerStack = mutableListOf<MediaController>()

    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _sliderUiMounted = MutableStateFlow(false)
    fun setSliderUiMounted(mounted: Boolean) {
        _sliderUiMounted.value = mounted
    }

    private var isSeeking = false
    private var activePositionOccurrenceMediaId: String? = null
    private var activePositionOccurrenceToken: Long = 0L
    private var nextPositionOccurrenceToken: Long = 1L
    private var pausedPositionOverrideMediaId: String? = null
    private var pausedPositionOverrideToken: Long? = null
    private var pausedPositionOverrideMs: Long? = null
    private var pausedPositionOverrideSetAtMs: Long = 0L
    private var coldStartSnapshotMediaId: String? = null
    private var coldStartSnapshotToken: Long? = null
    private var coldStartSnapshotPositionMs: Long? = null
    private var shuffleToggleJob: Job? = null
    private var lastShuffleToggleFinishedAtMs: Long = 0L
    private val powerManager: PowerManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private fun clearColdStartSnapshot() {
        coldStartSnapshotMediaId = null
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = null
    }

    /**
     * Binds a restored snapshot to the active playback occurrence when possible.
     *
     * If the first occurrence was already activated before the snapshot finished loading, we
     * attach the snapshot to that token so resume still works. If playback has already advanced
     * past the first occurrence, the snapshot is stale and must be discarded.
     */
    private fun rememberColdStartSnapshot(mediaId: String, positionMs: Long): Boolean {
        coldStartSnapshotMediaId = mediaId
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = positionMs

        if (nextPositionOccurrenceToken == 1L) {
            return true
        }

        if (
            activePositionOccurrenceToken == 1L &&
            nextPositionOccurrenceToken == 2L &&
            activePositionOccurrenceMediaId == mediaId
        ) {
            coldStartSnapshotToken = activePositionOccurrenceToken
            return true
        }

        clearColdStartSnapshot()
        return false
    }

    fun initialize(
        coroutineScope: CoroutineScope
    ) {
        owners.push(coroutineScope)
        this.scope = coroutineScope
        scope?.launch {
            userPreferencesRepository.autoplayOnSkipFlow.collect { enabled ->
                autoplayOnSkip = enabled
            }
        }
        scope?.launch {
            userPreferencesRepository.previousButtonBehaviorFlow.collect { behavior ->
                previousButtonBehavior = behavior
            }
        }
        scope?.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val snapshotMediaId = snapshot.currentMediaId
                ?: snapshot.items.getOrNull(snapshot.currentIndex)?.mediaId
                ?: return@launch
            val snapshotPositionMs = snapshot.currentPositionMs.coerceAtLeast(0L)
            if (snapshotPositionMs <= 0L) return@launch
            if (!rememberColdStartSnapshot(snapshotMediaId, snapshotPositionMs)) {
                return@launch
            }

            val controller = mediaController
            if (
                controller != null &&
                !controller.isPlaying &&
                controller.currentMediaItem?.mediaId == snapshotMediaId &&
                _currentPosition.value == 0L
            ) {
                _currentPosition.value = snapshotPositionMs
            }
        }
    }

    fun setMediaController(controller: MediaController?) {
        if (controller == null) {
            mediaControllerStack.clear()
            mediaController = null
            return
        }

        mediaControllerStack.removeAll { it === controller }
        mediaControllerStack.add(controller)
        mediaController = controller
    }

    fun clearMediaController(controller: MediaController?) {
        if (controller == null) return

        mediaControllerStack.removeAll { it === controller }
        if (mediaController === controller) {
            mediaController = mediaControllerStack.lastOrNull()
        }
    }

    private fun activeLocalPlayer(): Player {
        val controller = mediaController
        return if (controller?.isConnected == true) {
            controller
        } else {
            dualPlayerEngine.masterPlayer
        }
    }
    
    fun updateStablePlayerState(update: (StablePlayerState) -> StablePlayerState) {
        _stablePlayerState.update { current ->
            val updated = update(current)
            if (updated.currentMediaItemIndex == -1) {
                if (dualPlayerEngine.isUsingWindowedQueue()) {
                    updated.copy(currentMediaItemIndex = dualPlayerEngine.getCurrentAbsoluteIndex())
                } else {
                    mediaController?.let { controller ->
                        updated.copy(currentMediaItemIndex = controller.currentMediaItemIndex)
                    } ?: updated
                }
            } else {
                updated
            }
        }
    }

    fun setCurrentPosition(positionMs: Long) {
        _currentPosition.value = positionMs.coerceAtLeast(0L)
    }

    fun syncCurrentPositionFromPlayer(mediaId: String?, reportedPositionMs: Long) {
        _currentPosition.value = resolveUiPosition(mediaId, reportedPositionMs)
    }

    fun ensureCurrentPlaybackOccurrence(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = false)
    }

    fun onPlaybackOccurrenceTransition(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = true)
    }

    fun rememberPausedPositionOverride(mediaId: String?, positionMs: Long) {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return
        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false) ?: return
        val safePosition = positionMs.coerceAtLeast(0L)
        pausedPositionOverrideMediaId = safeMediaId
        pausedPositionOverrideToken = activeToken
        pausedPositionOverrideMs = safePosition
        pausedPositionOverrideSetAtMs = SystemClock.elapsedRealtime()
        _currentPosition.value = safePosition
    }

    fun clearCurrentPositionHints(mediaId: String? = null) {
        if (mediaId == null || pausedPositionOverrideMediaId == mediaId) {
            pausedPositionOverrideMediaId = null
            pausedPositionOverrideToken = null
            pausedPositionOverrideMs = null
            pausedPositionOverrideSetAtMs = 0L
        }
        if (mediaId == null || coldStartSnapshotMediaId == mediaId) {
            clearColdStartSnapshot()
        }
    }

    private fun resolveUiPosition(mediaId: String?, reportedPositionMs: Long): Long {
        val safeReportedPosition = reportedPositionMs.coerceAtLeast(0L)
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (safeMediaId == null) {
            return safeReportedPosition
        }

        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false)
            ?: return safeReportedPosition

        val pausedOverride = pausedPositionOverrideMs
            ?.takeIf {
                pausedPositionOverrideMediaId == safeMediaId &&
                    pausedPositionOverrideToken == activeToken
            }
        val coldStartSeed = coldStartSnapshotPositionMs
            ?.takeIf {
                coldStartSnapshotMediaId == safeMediaId &&
                    coldStartSnapshotToken == activeToken
            }
        val preferredPosition = pausedOverride ?: coldStartSeed

        if (preferredPosition == null) {
            return safeReportedPosition
        }

        if (safeReportedPosition <= 0L) {
            return preferredPosition
        }

        val drift = abs(safeReportedPosition - preferredPosition)
        val pausedOverrideOwnsThisToken =
            pausedPositionOverrideMediaId == safeMediaId &&
                pausedPositionOverrideToken == activeToken
        val pausedOverrideActive = pausedOverride != null
        val overrideIsStale = pausedOverrideActive &&
            pausedPositionOverrideSetAtMs > 0L &&
            SystemClock.elapsedRealtime() - pausedPositionOverrideSetAtMs > PAUSED_OVERRIDE_MAX_AGE_MS
        val coldStartPassed = !pausedOverrideActive && safeReportedPosition >= preferredPosition
        if (drift <= DURATION_MISMATCH_TOLERANCE_MS || overrideIsStale || coldStartPassed) {
            if (pausedOverrideOwnsThisToken) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
                pausedPositionOverrideSetAtMs = 0L
            }
            if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotToken == activeToken) {
                clearColdStartSnapshot()
            }
            return safeReportedPosition
        }

        return preferredPosition
    }

    private fun activatePlaybackOccurrence(
        mediaId: String?,
        forceNewOccurrence: Boolean
    ): Long? {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: run {
            activePositionOccurrenceMediaId = null
            activePositionOccurrenceToken = 0L
            if (forceNewOccurrence) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
                pausedPositionOverrideSetAtMs = 0L
            }
            return null
        }

        val shouldAdvance =
            forceNewOccurrence ||
                activePositionOccurrenceToken == 0L ||
                activePositionOccurrenceMediaId != safeMediaId

        if (!shouldAdvance) {
            return activePositionOccurrenceToken
        }

        activePositionOccurrenceMediaId = safeMediaId
        activePositionOccurrenceToken = nextPositionOccurrenceToken++

        pausedPositionOverrideMediaId = null
        pausedPositionOverrideToken = null
        pausedPositionOverrideMs = null
        pausedPositionOverrideSetAtMs = 0L

        if (coldStartSnapshotToken != null) {
            clearColdStartSnapshot()
        } else if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotPositionMs != null) {
            coldStartSnapshotToken = activePositionOccurrenceToken
        } else if (coldStartSnapshotMediaId != null) {
            clearColdStartSnapshot()
        }

        return activePositionOccurrenceToken
    }
    
    fun playPause() {
        val controller = activeLocalPlayer()
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun seekTo(position: Long) {
        val targetPosition = position.coerceAtLeast(0L)
        val player = activeLocalPlayer()
        val currentMediaId = player.currentMediaItem?.mediaId
        rememberPausedPositionOverride(currentMediaId, targetPosition)
        dualPlayerEngine.notifyExternalSeekInitiated()
        player.seekTo(targetPosition)
    }

    /** The "Previous button" preference as last read, for event-time decisions in the UI. */
    fun currentPreviousButtonBehavior(): PreviousButtonBehavior = previousButtonBehavior

    fun previousSong() {
        val controller = activeLocalPlayer()
        val behavior = previousButtonBehavior
        val positionMs = controller.currentPosition
        val hasPrevious = controller.hasPreviousMediaItem()
        if (behavior.shouldRestartCurrentTrack(positionMs, hasPrevious)) {
            Timber.tag(TAG).d("Previous restarts the track (behavior=%s, position=%d ms)", behavior, positionMs)
            // Route the restart through seekTo() so the position override, the engine seek hint and
            // the UI position are updated together; a bare controller.seekTo(0) left a stale
            // override behind and the displayed position could lag the restart.
            seekTo(0L)
        } else {
            // Not seekToPrevious(): Media3 restarts the track past its own 3 s
            // maxSeekToPreviousPosition, which made this 10 s rule behave like 3 s.
            val wasPaused = !controller.playWhenReady
            controller.seekToPreviousMediaItem()
            resumeAfterSkip(controller, wasPaused)
        }
    }

    /** Always moves to the previous queue item, never restarting the current track. */
    fun forcePreviousSong() {
        val player = activeLocalPlayer()
        val wasPaused = !player.playWhenReady
        val hadPrevious = player.hasPreviousMediaItem()
        player.seekToPreviousMediaItem()
        if (hadPrevious) resumeAfterSkip(player, wasPaused)
    }

    fun nextSong() {
        val player = activeLocalPlayer()
        val wasPaused = !player.playWhenReady
        // On the last item with repeat off the seek is a no-op; resuming there would only
        // unpause the song the user tried to leave.
        val hadNext = player.hasNextMediaItem()
        player.seekToNext()
        if (hadNext) resumeAfterSkip(player, wasPaused)
    }

    /** Starts playback after a skip from a paused player when "Auto-play on skip" is on. */
    private fun resumeAfterSkip(player: Player, wasPaused: Boolean) {
        if (!wasPaused || !autoplayOnSkip) return
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            player.prepare()
        }
        player.play()
    }

    fun cycleRepeatMode() {
        val currentMode = _stablePlayerState.value.repeatMode
        val newMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = newMode
        scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
        _stablePlayerState.update { it.copy(repeatMode = newMode) }
    }

    fun setRepeatMode(mode: Int) {
        mediaController?.repeatMode = mode
        scope?.launch { userPreferencesRepository.setRepeatMode(mode) }
        _stablePlayerState.update { it.copy(repeatMode = mode) }
    }

    private var progressJob: kotlinx.coroutines.Job? = null
    /** The scope [progressJob] runs in, or null while progress updates are stopped. */
    private var progressScope: CoroutineScope? = null

    /**
     * Reconciles duration reported by the player with the current song metadata duration.
     *
     * Why:
     * - During some transitions (notably crossfade player swaps), the reported duration can lag
     *   behind the currently visible track for a short period.
     * - Relying only on one source can make progress run too slow/fast.
     */
    private fun resolveEffectiveDuration(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long {
        val reported = when {
            reportedDurationMs == C.TIME_UNSET -> 0L
            reportedDurationMs < 0L -> 0L
            else -> reportedDurationMs
        }
        val hint = songDurationHintMs.coerceAtLeast(0L)
        val position = currentPositionMs.coerceAtLeast(0L)

        if (reported <= 0L) return hint
        if (hint <= 0L) return reported

        val diff = abs(reported - hint)
        if (diff <= DURATION_MISMATCH_TOLERANCE_MS) return reported

        if (position > hint + DURATION_MISMATCH_TOLERANCE_MS && reported >= position) {
            return reported
        }

        val resolved = minOf(reported, hint)
        if (diff > 10_000L) {
            Timber.tag(TAG).w(
                "Duration mismatch resolved (reported=%dms, hint=%dms, pos=%dms, resolved=%dms)",
                reported, hint, position, resolved
            )
        }
        return resolved
    }

    fun resolveDurationForPlaybackState(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long = resolveEffectiveDuration(
        reportedDurationMs = reportedDurationMs,
        songDurationHintMs = songDurationHintMs,
        currentPositionMs = currentPositionMs
    )

    fun startProgressUpdates() {
        stopProgressUpdates()
        progressScope = scope
        progressJob = scope?.launch {
            _currentPosition.subscriptionCount.collectLatest { subscriberCount ->
                if (subscriberCount == 0) return@collectLatest
                coroutineScope {
                    while (isActive) {
                        val tickMs = currentProgressTickMs()
                        val controller = activeLocalPlayer()
                        if (shouldSampleLocalProgress(controller)) {
                            val visibleSong = _stablePlayerState.value.currentSong
                            val currentMediaId = controller.currentMediaItem?.mediaId
                            val hasMediaMismatch = visibleSong?.id != null &&
                                currentMediaId != null &&
                                visibleSong.id != currentMediaId

                            if (hasMediaMismatch) {
                                Timber.tag(TAG).v(
                                    "Skipping local progress tick due media mismatch (visible=%s, player=%s)",
                                    visibleSong.id,
                                    currentMediaId
                                )
                                delay(tickMs)
                                continue
                            }

                            val currentPosition = controller.currentPosition.coerceAtLeast(0L)
                            val songDurationHint = visibleSong?.duration ?: 0L
                            val duration = resolveEffectiveDuration(
                                reportedDurationMs = controller.duration,
                                songDurationHintMs = songDurationHint,
                                currentPositionMs = currentPosition
                            )

                            val resolvedPosition = resolveUiPosition(currentMediaId, currentPosition)
                            if (_currentPosition.value != resolvedPosition) {
                                _currentPosition.value = resolvedPosition
                            }

                            _stablePlayerState.update { state ->
                                if (state.totalDuration == duration) {
                                    state
                                } else {
                                    state.copy(totalDuration = duration)
                                }
                            }
                        }
                        delay(tickMs)
                    }
                }
            }
        }
    }

    private fun shouldSampleLocalProgress(controller: Player): Boolean {
        if (isSeeking) return false
        if (controller.mediaItemCount <= 0) return false
        if (controller.isPlaying) return true

        return controller.playWhenReady &&
            controller.playbackState != Player.STATE_IDLE &&
            controller.playbackState != Player.STATE_ENDED
    }

    private fun currentProgressTickMs(): Long {
        if (!powerManager.isInteractive) return BACKGROUND_TICK_MS
        return if (_sliderUiMounted.value) SLIDER_TICK_MS else MINIPLAYER_TICK_MS
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
        progressScope = null
    }

    private data class PreparedQueueReplacement(
        val mediaItems: List<MediaItem>,
        val targetIndex: Int
    )

    private data class PreparedQueueSegments(
        val beforeCurrent: List<MediaItem>,
        val afterCurrent: List<MediaItem>
    )

    /**
     * Moves the player's entries into the order of [desiredQueue]. With [desiredTags], the
     * playlist of each [desiredQueue] entry by position, a song queued twice with different
     * tags has each copy moved back to the place of its own tag rather than the first place
     * the song holds.
     */
    private fun reorderQueueInPlace(
        player: Player,
        desiredQueue: List<Song>,
        desiredTags: List<String?>? = null,
    ): Boolean {
        if (desiredQueue.isEmpty()) return false

        val currentCount = player.mediaItemCount
        if (currentCount != desiredQueue.size) {
            Timber.tag(TAG).w(
                "Cannot reorder queue in place: size mismatch (player=%d, desired=%d)",
                currentCount,
                desiredQueue.size
            )
            return false
        }

        val currentIds = MutableList(currentCount) { index ->
            player.getMediaItemAt(index).mediaId
        }
        val desiredIds = desiredQueue.map { it.id }
        val tagsToMatch = desiredTags?.takeIf { it.size == desiredIds.size }
        val currentTags = tagsToMatch?.let {
            MutableList(currentCount) { index ->
                MediaItemBuilder.playlistIdOf(player.getMediaItemAt(index))
            }
        }

        val currentCounts = currentIds.groupingBy { it }.eachCount()
        val desiredCounts = desiredIds.groupingBy { it }.eachCount()
        if (currentCounts != desiredCounts) {
            Timber.tag(TAG).w("Cannot reorder queue in place: mediaId mismatch")
            return false
        }

        for (targetIndex in desiredIds.indices) {
            val desiredId = desiredIds[targetIndex]
            val desiredTag = tagsToMatch?.get(targetIndex)
            val holdsDesiredEntry = { index: Int ->
                currentIds[index] == desiredId &&
                    (currentTags == null || currentTags[index] == desiredTag)
            }
            if (holdsDesiredEntry(targetIndex)) continue

            var fromIndex = (targetIndex + 1 until currentIds.size).firstOrNull(holdsDesiredEntry)
            if (fromIndex == null) {
                // No copy with this tag is left, so any copy of the song takes the place.
                if (currentIds[targetIndex] == desiredId) continue
                fromIndex = (targetIndex + 1 until currentIds.size)
                    .firstOrNull { currentIds[it] == desiredId }
            }

            if (fromIndex == null) {
                Timber.tag(TAG).w(
                    "Cannot reorder queue in place: target mediaId '%s' not found",
                    desiredId
                )
                return false
            }

            player.moveMediaItem(fromIndex, targetIndex)
            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(targetIndex, movedId)
            currentTags?.let { tags -> tags.add(targetIndex, tags.removeAt(fromIndex)) }
        }

        return true
    }

    /**
     * Which playlist each queue entry came from, as (media id, playlist id) in player order.
     * Songs the user added from elsewhere have no tag and must not gain one on a rebuild, or a
     * custom transition would apply to a pair the user put together by hand. This is kept per
     * entry because the same song can be queued from the playlist and again by hand.
     */
    private fun playlistTagsOf(player: Player): List<Pair<String, String?>> =
        List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            item.mediaId to MediaItemBuilder.playlistIdOf(item)
        }

    /**
     * The playlist ids of [playlistTags] by position, when the player holds exactly [queue] in
     * that order; otherwise null.
     */
    private fun playlistIdsLinedUpWith(
        queue: List<Song>,
        playlistTags: List<Pair<String, String?>>,
    ): List<String?>? {
        if (playlistTags.size != queue.size) return null
        if (queue.indices.any { index -> playlistTags[index].first != queue[index].id }) return null
        return playlistTags.map { (_, playlistId) -> playlistId }
    }

    /**
     * Picks the playlist tag of each rebuilt entry of [newQueue], by index. [newQueueTags], the
     * tags of [newQueue]'s own entries by position, are used as they are when they line up with
     * it, as for the original order that turning shuffle off goes back to. Otherwise the tags of
     * the player's entries in [playlistTags] are handed out from a [PlaylistTagPool], after
     * setting aside the tag of [keptItem], which stays in place with its own. The pool hands
     * tags out in call order, so the result has to be called in index order.
     */
    private fun playlistTagSource(
        newQueue: List<Song>,
        newQueueTags: List<String?>?,
        playlistTags: List<Pair<String, String?>>,
        keptItem: MediaItem?,
    ): (Int) -> String? {
        if (newQueueTags != null && newQueueTags.size == newQueue.size) return newQueueTags::get
        val tagPool = PlaylistTagPool(playlistTags)
        keptItem?.let { tagPool.consume(it.mediaId, MediaItemBuilder.playlistIdOf(it)) }
        return { index -> tagPool.take(newQueue[index].id) }
    }

    /**
     * Replaces the player timeline with [newQueue] in a single setMediaItems call,
     * preserving the currently playing song and its position. This is O(1) IPC calls
     * versus O(n) for reorderQueueInPlace, making it suitable for large queue shuffles.
     */
    private suspend fun buildQueueReplacement(
        newQueue: List<Song>,
        targetIndex: Int,
        currentMediaItem: MediaItem?,
        playlistTags: List<Pair<String, String?>>,
        newQueueTags: List<String?>? = null,
    ): PreparedQueueReplacement = withContext(Dispatchers.Default) {
        val safeTargetIndex = targetIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val keptCurrentItem = currentMediaItem
            ?.takeIf { it.mediaId == newQueue.getOrNull(safeTargetIndex)?.id }
        // A reshuffle keeps the same songs, so each keeps its own playlist context too;
        // dropping it would switch off the playlist's transitions.
        val tagFor = playlistTagSource(newQueue, newQueueTags, playlistTags, keptCurrentItem)
        val mediaItems = List(newQueue.size) { index ->
            keptCurrentItem?.takeIf { index == safeTargetIndex }
                ?: rebuildQueueItem(newQueue[index], tagFor(index))
        }

        PreparedQueueReplacement(
            mediaItems = mediaItems,
            targetIndex = safeTargetIndex
        )
    }

    private fun rebuildQueueItem(song: Song, playlistId: String?): MediaItem =
        MediaItemBuilder.withPlaylistId(MediaItemBuilder.build(song), playlistId)

    private suspend fun buildQueueSegments(
        newQueue: List<Song>,
        currentIndex: Int,
        currentMediaItem: MediaItem?,
        playlistTags: List<Pair<String, String?>>,
        newQueueTags: List<String?>? = null,
    ): PreparedQueueSegments? = withContext(Dispatchers.Default) {
        val safeCurrentIndex = currentIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val currentQueueSong = newQueue.getOrNull(safeCurrentIndex) ?: return@withContext null
        if (currentMediaItem == null || currentMediaItem.mediaId != currentQueueSong.id) {
            return@withContext null
        }

        // The current entry stays in place with its own tag.
        val tagFor = playlistTagSource(newQueue, newQueueTags, playlistTags, currentMediaItem)
        val beforeCurrent = List(safeCurrentIndex) { index ->
            rebuildQueueItem(newQueue[index], tagFor(index))
        }
        val afterStartIndex = safeCurrentIndex + 1
        val afterCurrent = List((newQueue.size - afterStartIndex).coerceAtLeast(0)) { offset ->
            val index = afterStartIndex + offset
            rebuildQueueItem(newQueue[index], tagFor(index))
        }

        PreparedQueueSegments(
            beforeCurrent = beforeCurrent,
            afterCurrent = afterCurrent
        )
    }

    // Operates on the passed player (the MediaController), never on the engine's master
    // player directly: mutating the master player behind the session's back desyncs the
    // MediaController's timeline snapshot, the notification and the widget queue preview.
    private fun replacePlayerQueuePreservingCurrent(
        player: Player,
        currentIndex: Int,
        preparedSegments: PreparedQueueSegments
    ): Boolean {
        val mediaItemCount = player.mediaItemCount
        if (currentIndex !in 0 until mediaItemCount) {
            return false
        }

        val afterStartIndex = currentIndex + 1
        if (preparedSegments.beforeCurrent.size != currentIndex) {
            return false
        }
        if (preparedSegments.afterCurrent.size != (mediaItemCount - afterStartIndex)) {
            return false
        }

        if (currentIndex > 0) {
            player.replaceMediaItems(0, currentIndex, preparedSegments.beforeCurrent)
        }
        player.replaceMediaItems(afterStartIndex, mediaItemCount, preparedSegments.afterCurrent)
        return player.currentMediaItemIndex == currentIndex
    }

    private fun replacePlayerQueue(
        player: Player,
        preparedQueue: PreparedQueueReplacement,
        currentPosition: Long
    ) {
        val shouldResumePlayback = player.playWhenReady || player.isPlaying

        player.setMediaItems(
            preparedQueue.mediaItems,
            preparedQueue.targetIndex,
            currentPosition
        )

        if (shouldResumePlayback) {
            player.playWhenReady = true
            if (!player.isPlaying) {
                player.play()
            }
        }
    }

    /**
     * Where the playing entry goes back to in [originalQueue]. When [originalTags] are known, a
     * song the original order holds more than once goes back to the copy with the playing
     * entry's own playlist tag, so it does not take the place of a copy queued from elsewhere.
     */
    private fun originalIndexOf(
        originalQueue: List<Song>,
        originalTags: List<String?>?,
        currentSongId: String?,
        currentMediaItem: MediaItem?,
    ): Int? {
        if (
            originalTags != null &&
            currentMediaItem != null &&
            currentMediaItem.mediaId == currentSongId
        ) {
            val currentTag = MediaItemBuilder.playlistIdOf(currentMediaItem)
            originalQueue.indices
                .firstOrNull { index ->
                    originalQueue[index].id == currentSongId && originalTags[index] == currentTag
                }
                ?.let { return it }
        }
        return originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }
    }

    fun toggleShuffle(
        currentSongs: List<Song>,
        currentSong: Song?,
        updateQueueCallback: (List<Song>) -> Unit
    ) {
        val nowMs = SystemClock.elapsedRealtime()
        if (shuffleToggleJob?.isActive == true) return
        if ((nowMs - lastShuffleToggleFinishedAtMs) < SHUFFLE_TOGGLE_COOLDOWN_MS) return

        val coroutineScope = scope ?: return
        shuffleToggleJob = coroutineScope.launch {
                _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = true) }
                try {
                    val player = mediaController ?: return@launch
                    if (currentSongs.isEmpty()) return@launch

                    val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled

                    if (!isCurrentlyShuffled) {
                        val playlistTags = playlistTagsOf(player)
                        if (!queueStateHolder.hasOriginalQueue()) {
                            // With no original order, for example after a cold start restored
                            // the queue, only the player knows which playlist each entry came
                            // from. The order keeps that so shuffle off can put each tag back.
                            queueStateHolder.setOriginalQueueOrder(
                                currentSongs,
                                playlistIdsLinedUpWith(currentSongs, playlistTags)
                            )
                        }

                        val currentMediaId = player.currentMediaItem?.mediaId ?: currentSong?.id
                        val playerCurrentIndex = player.currentMediaItemIndex
                            .takeIf { it in currentSongs.indices }
                        val currentIndex = when {
                            playerCurrentIndex != null && currentMediaId != null &&
                                currentSongs.getOrNull(playerCurrentIndex)?.id == currentMediaId -> playerCurrentIndex
                            playerCurrentIndex != null && currentMediaId == null -> playerCurrentIndex
                            currentMediaId != null ->
                                currentSongs.indexOfFirst { it.id == currentMediaId }.takeIf { it >= 0 }
                            else -> null
                        } ?: 0
                        val currentPosition = player.currentPosition
                        val wasPlaying = player.isPlaying
                        val currentMediaItem = player.currentMediaItem

                        val shuffledQueue = withContext(Dispatchers.Default) {
                            QueueUtils.buildAnchoredShuffleQueueSuspending(currentSongs, currentIndex)
                        }

                        if (currentSongs.size > BULK_REPLACE_THRESHOLD) {
                            val preservedReplacement = buildQueueSegments(
                                newQueue = shuffledQueue,
                                currentIndex = currentIndex,
                                currentMediaItem = currentMediaItem,
                                playlistTags = playlistTags
                            )
                            val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                replacePlayerQueuePreservingCurrent(player, currentIndex, preparedSegments)
                            } == true

                            if (!replacedInPlace) {
                                val preparedQueue = buildQueueReplacement(
                                    newQueue = shuffledQueue,
                                    targetIndex = currentIndex,
                                    currentMediaItem = currentMediaItem,
                                    playlistTags = playlistTags
                                )
                                replacePlayerQueue(player, preparedQueue, currentPosition)
                            }
                        } else {
                            val reordered = reorderQueueInPlace(player, shuffledQueue)
                            if (!reordered) {
                                val preservedReplacement = buildQueueSegments(
                                    newQueue = shuffledQueue,
                                    currentIndex = currentIndex,
                                    currentMediaItem = currentMediaItem,
                                    playlistTags = playlistTags
                                )
                                val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                    replacePlayerQueuePreservingCurrent(player, currentIndex, preparedSegments)
                                } == true

                                if (!replacedInPlace) {
                                    val preparedQueue = buildQueueReplacement(
                                        newQueue = shuffledQueue,
                                        targetIndex = currentIndex,
                                        currentMediaItem = currentMediaItem,
                                        playlistTags = playlistTags
                                    )
                                    replacePlayerQueue(player, preparedQueue, currentPosition)
                                }
                            }
                        }

                        updateQueueCallback(shuffledQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = true) }
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }

                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(true)
                            }
                        }
                    } else {
                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(false)
                            }
                        }

                        if (!queueStateHolder.hasOriginalQueue()) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        val originalQueue = queueStateHolder.originalQueueOrder
                        // Each original entry's own tag, so a song queued twice with different
                        // tags gets each back at its own place instead of a guess by song id.
                        val originalTags = queueStateHolder.originalQueuePlaylistIds
                            ?.takeIf { it.size == originalQueue.size }
                        val wasPlaying = player.isPlaying
                        val currentPosition = player.currentPosition
                        val currentSongId = currentSong?.id ?: player.currentMediaItem?.mediaId
                        val currentMediaItem = player.currentMediaItem
                        val playlistTags = playlistTagsOf(player)
                        val originalIndex = originalIndexOf(
                            originalQueue,
                            originalTags,
                            currentSongId,
                            currentMediaItem
                        )

                        if (originalIndex == null) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        if (originalQueue.size > BULK_REPLACE_THRESHOLD) {
                            val preservedReplacement = buildQueueSegments(
                                newQueue = originalQueue,
                                currentIndex = originalIndex,
                                currentMediaItem = currentMediaItem,
                                playlistTags = playlistTags,
                                newQueueTags = originalTags
                            )
                            val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                replacePlayerQueuePreservingCurrent(player, originalIndex, preparedSegments)
                            } == true

                            if (!replacedInPlace) {
                                val preparedQueue = buildQueueReplacement(
                                    newQueue = originalQueue,
                                    targetIndex = originalIndex,
                                    currentMediaItem = currentMediaItem,
                                    playlistTags = playlistTags,
                                    newQueueTags = originalTags
                                )
                                replacePlayerQueue(player, preparedQueue, currentPosition)
                            }
                        } else {
                            val reordered = reorderQueueInPlace(player, originalQueue, originalTags)
                            if (!reordered) {
                                val preservedReplacement = buildQueueSegments(
                                    newQueue = originalQueue,
                                    currentIndex = originalIndex,
                                    currentMediaItem = currentMediaItem,
                                    playlistTags = playlistTags,
                                    newQueueTags = originalTags
                                )
                                val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                    replacePlayerQueuePreservingCurrent(player, originalIndex, preparedSegments)
                                } == true

                                if (!replacedInPlace) {
                                    val preparedQueue = buildQueueReplacement(
                                        newQueue = originalQueue,
                                        targetIndex = originalIndex,
                                        currentMediaItem = currentMediaItem,
                                        playlistTags = playlistTags,
                                        newQueueTags = originalTags
                                    )
                                    replacePlayerQueue(player, preparedQueue, currentPosition)
                                }
                            }
                        }

                        updateQueueCallback(originalQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }
                    }
                } finally {
                    lastShuffleToggleFinishedAtMs = SystemClock.elapsedRealtime()
                    _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = false) }
                    shuffleToggleJob = null
                }
            }
    }

    fun onCleared(owner: CoroutineScope) {
        // A stale PlayerViewModel clearing leaves the newer binding alone; the current one
        // (e.g. ExternalPlayerActivity's) clearing hands the holder back to the previous live
        // PlayerViewModel instead of leaving it scope-less.
        owners.remove(owner) { next ->
            if (next == null) {
                shuffleToggleJob?.cancel()
                shuffleToggleJob = null
            }
            scope = next
        }
        // Progress updates die with the scope they were launched in, whichever binding that
        // was. The surviving PlayerViewModel only restarts them on its next play/pause change,
        // so restart them in the current scope now or the seek bar freezes meanwhile.
        if (progressScope === owner) {
            if (scope != null) startProgressUpdates() else stopProgressUpdates()
        }
    }

}
