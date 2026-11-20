package com.lostf1sh.pixelplayeross.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.media.TrackBpmRepository
import com.lostf1sh.pixelplayeross.data.model.TransitionMode
import com.lostf1sh.pixelplayeross.data.model.TransitionResolution
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.model.TransitionSource
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.TransitionRepository
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private data class TransitionSettingsSnapshot(
    val resolution: TransitionResolution,
    val isCrossfadeEnabled: Boolean,
    val isSmartEnabled: Boolean,
)

/**
 * Orchestrates song transitions by observing the player state and
 * commanding the DualPlayerEngine.
 */
@OptIn(UnstableApi::class)
@Singleton
class TransitionController @Inject constructor(
    private val engine: DualPlayerEngine,
    private val transitionRepository: TransitionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trackBpmRepository: TrackBpmRepository,
    private val mixPlaybackState: MixPlaybackState,
) {
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null
    private var currentObservedPlayer: Player? = null
    private var needsDurationReschedule = false

    private companion object {
        /** Tries to publish the incoming song's mix point while waiting for the mix to start. */
        const val MAX_INCOMING_MIX_ATTEMPTS = 8
    }

    private val swapListener: (Player) -> Unit = { newPlayer ->
        Timber.tag("TransitionDebug").d("Controller detected player swap. Moving listener.")
        transitionListener?.let { listener ->
            currentObservedPlayer?.removeListener(listener)
            currentObservedPlayer = newPlayer
            newPlayer.addListener(listener)

            if (newPlayer.isPlaying) {
                val item = newPlayer.currentMediaItem
                if (item != null) {
                    scope.launch {
                        delay(1_000L)
                        if (currentObservedPlayer === newPlayer &&
                            newPlayer.currentMediaItem?.mediaId == item.mediaId
                        ) {
                            scheduleTransitionFor(item)
                        }
                    }
                }
            }
        }
    }

    /**
     * Attaches the controller to the player engine to start listening for state changes.
     */
    fun initialize() {
        if (transitionListener != null) return

        Timber.tag("TransitionDebug").d("Initializing TransitionController...")

        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Timber.tag("TransitionDebug").d("onMediaItemTransition: %s (reason=%d)", mediaItem?.mediaId, reason)
                engine.setPauseAtEndOfMediaItems(shouldPause = false)

                mediaItem?.let { scheduleTransitionFor(it) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted || needsDurationReschedule)) {
                    needsDurationReschedule = false
                    Timber.tag("TransitionDebug").d("Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && needsDurationReschedule) {
                    val player = engine.masterPlayer
                    val duration = player.duration
                    if (duration != C.TIME_UNSET && duration > 0) {
                        needsDurationReschedule = false
                        Timber.tag("TransitionDebug").d("Player became ready with valid duration (%d). Rescheduling transition.", duration)
                        player.currentMediaItem?.let { scheduleTransitionFor(it) }
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    Timber.tag("TransitionDebug").d("Timeline changed (reason=%d). Cancelling pending transition.", reason)
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()

                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                } else if (needsDurationReschedule) {
                    val duration = engine.masterPlayer.duration
                    if (duration != C.TIME_UNSET && duration > 0) {
                        needsDurationReschedule = false
                        Timber.tag("TransitionDebug").d("Timeline updated with valid duration (%d). Rescheduling transition.", duration)
                        engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                Timber.tag("TransitionDebug").d("Repeat mode changed to %d. Rescheduling transition.", repeatMode)
                transitionSchedulerJob?.cancel()
                engine.cancelNext()
                engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
            }
        }

        currentObservedPlayer = engine.masterPlayer
        currentObservedPlayer?.addListener(transitionListener!!)
        engine.addPlayerSwapListener(swapListener)

        // A sleep timer that stops at the end of the track must not crossfade into the next
        // one; once it clears, the current track gets its transition back. A crossfade that
        // is already running is left alone: the swap reschedules for the incoming track.
        scope.launch {
            engine.sleepTimerHold
                .map { it.suppressCrossfade }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (engine.isTransitionRunning()) return@collect
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
        }
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        transitionSchedulerJob?.cancel()
        // Mixes announced for other songs are done with. The entry for this song stays: it was
        // published while the previous mix ran, so this song's seek bar is already the right
        // length (the schedule below replaces it with the final values).
        mixPlaybackState.retainOnly(currentMediaItem.mediaId)
        needsDurationReschedule = false
        engine.setPauseAtEndOfMediaItems(shouldPause = false)

        if (engine.sleepTimerHold.value.suppressCrossfade) {
            Timber.tag("TransitionDebug").d("Sleep timer holds playback at track end. No transition.")
            if (!engine.isTransitionRunning()) engine.cancelNext()
            return
        }

        transitionSchedulerJob = scope.launch {
            if (engine.isTransitionRunning()) {
                Timber.tag("TransitionDebug").d("Cancelling active transition to schedule next...")
                engine.cancelNext()
            }

            delay(1500)

            val player = engine.masterPlayer
            val repeatMode = player.repeatMode
            val transitionTarget = engine.getNextTransitionTarget(currentMediaItem, repeatMode)

            if (transitionTarget == null) {
                Timber.tag("TransitionDebug").d(
                    "No next track (currentIndex=%d, count=%d, repeatMode=%d). No transition.",
                    player.currentMediaItemIndex,
                    player.mediaItemCount,
                    repeatMode,
                )
                engine.cancelNext()
                return@launch
            }

            val nextMediaItem = transitionTarget.mediaItem

            val fromTrackId = currentMediaItem.mediaId
            val toTrackId = nextMediaItem.mediaId
            val currentPlaylistId = MediaItemBuilder.playlistIdOf(currentMediaItem)
            val nextPlaylistId = MediaItemBuilder.playlistIdOf(nextMediaItem)
            // A song the user queued from elsewhere carries no playlist tag; next to playlist
            // songs it still takes the playlist's default transition rather than the global one.
            val playlistId = currentPlaylistId ?: nextPlaylistId
            // A pair's own transition only plays while the queue still plays that pair as the
            // playlist has it: both songs queued from the playlist, back to back. Once the user
            // moves, inserts or removes songs around it, it falls back to the playlist default.
            val pairStillFromPlaylist = currentPlaylistId != null && currentPlaylistId == nextPlaylistId

            Timber.tag("TransitionDebug").d(
                "Resolving settings for playlistId=%s, %s -> %s (pairFromPlaylist=%s)",
                playlistId, fromTrackId, toTrackId, pairStillFromPlaylist
            )

            // A playlist the user turned into a mix plays its own DJ-style transitions. This is
            // deliberate opt-in, so it runs even when the global crossfade toggle is off.
            if (playlistId != null && transitionRepository.isMixEnabled(playlistId).first()) {
                scheduleMixTransition(
                    currentMediaItem = currentMediaItem,
                    transitionTarget = transitionTarget,
                    playlistId = playlistId,
                    pairStillFromPlaylist = pairStillFromPlaylist,
                )
                return@launch
            }
            mixPlaybackState.clear()

            val isCrossfadeEnabledFlow = userPreferencesRepository.isCrossfadeEnabledFlow

            val settingsFlow = if (playlistId != null) {
                transitionRepository.resolveTransitionSettings(
                    playlistId,
                    fromTrackId.takeIf { pairStillFromPlaylist },
                    toTrackId.takeIf { pairStillFromPlaylist },
                )
            } else {
                Timber.tag("TransitionDebug").d("Missing playlistId. Using global settings.")
                transitionRepository.getGlobalSettings().map {
                    TransitionResolution(
                        settings = it,
                        source = TransitionSource.GLOBAL_DEFAULT
                    )
                }
            }

            combine(
                settingsFlow,
                isCrossfadeEnabledFlow,
                userPreferencesRepository.smartCrossfadeEnabledFlow,
            ) { resolution, isEnabled, isSmartEnabled ->
                TransitionSettingsSnapshot(
                    resolution = resolution,
                    isCrossfadeEnabled = isEnabled,
                    isSmartEnabled = isSmartEnabled,
                )
            }.distinctUntilChanged()
            .collectLatest { snapshot ->
                needsDurationReschedule = false
                val resolution = snapshot.resolution
                val isEnabled = snapshot.isCrossfadeEnabled

                val settings = resolution.settings
                Timber.tag("TransitionDebug").d(
                    "Settings resolved: Mode=%s, Duration=%dms, GlobalEnabled=%s, Source=%s",
                    settings.mode, settings.durationMs, isEnabled, resolution.source
                )

                // The global toggle is a master kill-switch, deliberately checked before the
                // resolution source: a playlist or per-track rule picks which settings win, not
                // whether crossfade runs at all.
                if (!isEnabled) {
                    Timber.tag("TransitionDebug").d(
                        "Crossfade globally disabled (source=%s). Using default gap.",
                        resolution.source
                    )
                    engine.cancelNext()
                    engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    return@collectLatest
                }

                if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                    Timber.tag("TransitionDebug").d("Transition disabled or zero duration.")
                    engine.cancelNext()
                    engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    return@collectLatest
                }

                // Smart crossfade: tag-embedded tempo for both sides of the transition
                // (cached in-memory after the first read â€” never decodes audio).
                // A user-authored per-pair rule (PLAYLIST_SPECIFIC) is explicit intent and
                // is never overridden by the automatic tempo logic.
                val smartApplies = snapshot.isSmartEnabled &&
                    resolution.source != TransitionSource.PLAYLIST_SPECIFIC
                val outgoingBpm = if (smartApplies) trackBpmRepository.bpmFor(currentMediaItem) else null
                val incomingBpm = if (smartApplies) trackBpmRepository.bpmFor(nextMediaItem) else null

                Timber.tag("TransitionDebug").d("Preparing next track for overlap: %s", nextMediaItem.mediaId)
                engine.prepareNext(transitionTarget)

                var duration = player.duration
                var durationWaitAttempts = 0
                while ((duration == C.TIME_UNSET || duration <= 0) && isActive) {
                    if (++durationWaitAttempts > 10) {
                        Timber.tag("TransitionDebug").w("Timed out waiting for duration (%d). Pausing transition scheduling until duration becomes available.", duration)
                        engine.cancelNext()
                        needsDurationReschedule = true
                        return@collectLatest
                    }
                    delay(500)
                    duration = player.duration
                    Timber.tag("TransitionDebug").v("Waiting for duration... (%d)", duration)
                }

                needsDurationReschedule = false
                if (!isActive) return@collectLatest

                val minFade = 500L
                val guardWindow = 150L

                if (duration < minFade + guardWindow) {
                    Timber.tag("TransitionDebug").w("Track too short for crossfade (duration=%d).", duration)
                    engine.cancelNext()
                    engine.setPauseAtEndOfMediaItems(false)
                    return@collectLatest
                }

                val smartPlan = if (smartApplies) {
                    SmartCrossfadePlanner.plan(
                        base = settings,
                        trackDurationMs = duration,
                        outgoingBpm = outgoingBpm,
                        incomingBpm = incomingBpm,
                        minFadeMs = minFade,
                        guardWindowMs = guardWindow,
                    )
                } else {
                    null
                }

                val maxFadeDuration = (duration - guardWindow).coerceAtLeast(minFade)
                val effectiveSettings = smartPlan?.settings ?: settings
                val effectiveDuration = effectiveSettings.durationMs.toLong()
                    .coerceAtLeast(minFade)
                    .coerceAtMost(maxFadeDuration)

                val transitionPoint = smartPlan?.transitionPointMs ?: (duration - effectiveDuration)

                Timber.tag("TransitionDebug").d(
                    "Scheduled %s at %d ms (SongDur: %d). Fade duration: %d ms (smart=%s)",
                    effectiveSettings.mode, transitionPoint, duration, effectiveDuration,
                    smartPlan?.compatibility
                )

                engine.setPauseAtEndOfMediaItems(shouldPause = true)
                Timber.tag("TransitionDebug").d("Enabled pauseAtEndOfMediaItems to prevent auto-skip.")

                if (transitionPoint <= player.currentPosition) {
                    val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        val adjustedDuration = remaining.coerceAtMost(effectiveDuration)
                        Timber.tag("TransitionDebug").w("Already past transition point! Triggering immediately.")
                        engine.performTransition(effectiveSettings.copy(durationMs = adjustedDuration.toInt()))
                    } else {
                        Timber.tag("TransitionDebug").w("Too close to end (%d ms left). Skipping to avoid glitch.", remaining)
                        engine.cancelNext()
                        engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    }
                    return@collectLatest
                }

                while (player.currentPosition < transitionPoint && isActive) {
                    val remaining = transitionPoint - player.currentPosition
                    val sleep = when {
                        remaining > 5000 -> 1000L
                        remaining > 1000 -> 250L
                        else -> 50L
                    }.coerceAtMost(remaining).coerceAtLeast(1L)
                    if (remaining < 2000 && remaining % 500 < 50) {
                        Timber.tag("TransitionDebug").v("Countdown: %d ms to transition", remaining)
                    }
                    delay(sleep)
                }

                if (isActive) {
                    val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        val adjustedDuration = remaining.coerceAtMost(effectiveDuration)
                        Timber.tag("TransitionDebug").d("FIRING TRANSITION NOW!")
                        engine.performTransition(effectiveSettings.copy(durationMs = adjustedDuration.toInt()))
                    } else {
                        Timber.tag("TransitionDebug").w("Too close to end (%d ms left). Skipping to avoid glitch.", remaining)
                        engine.cancelNext()
                        engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    }
                } else {
                    Timber.tag("TransitionDebug").d("Job cancelled before firing.")
                    engine.setPauseAtEndOfMediaItems(shouldPause = false)
                }
            }
        }
    }

    /**
     * Schedules the transition for a mix playlist: the pair's authored [MixTransition] when the
     * queue still plays the pair as the playlist has it, otherwise an automatic one built from
     * the songs' tempos. Re-runs when the stored mix changes.
     */
    private suspend fun scheduleMixTransition(
        currentMediaItem: MediaItem,
        transitionTarget: DualPlayerEngine.TransitionTarget,
        playlistId: String,
        pairStillFromPlaylist: Boolean,
    ) {
        val player = engine.masterPlayer
        val nextMediaItem = transitionTarget.mediaItem

        val storedFlow = if (pairStillFromPlaylist) {
            transitionRepository.getMixTransition(playlistId, currentMediaItem.mediaId, nextMediaItem.mediaId)
        } else {
            flowOf(null)
        }

        storedFlow.distinctUntilChanged().collectLatest { stored ->
            needsDurationReschedule = false

            // The incoming song must be positioned before it is prepared; only the authored mix
            // knows a non-zero start. An automatic mix always starts the incoming song at 0.
            engine.prepareNext(transitionTarget, startPositionMs = stored?.inStartMs ?: 0L)

            var duration = player.duration
            var attempts = 0
            while ((duration == C.TIME_UNSET || duration <= 0) && currentCoroutineContext().isActive) {
                if (++attempts > 10) {
                    Timber.tag("TransitionDebug").w("Mix: timed out waiting for duration.")
                    mixPlaybackState.remove(currentMediaItem.mediaId)
                    engine.cancelNext()
                    needsDurationReschedule = true
                    return@collectLatest
                }
                delay(500)
                duration = player.duration
            }
            if (!currentCoroutineContext().isActive) return@collectLatest

            val outgoingBpm = trackBpmRepository.bpmFor(currentMediaItem)
            val incomingBpm = trackBpmRepository.bpmFor(nextMediaItem)
            val mix = MixPlanner.fit(
                stored ?: MixPlanner.auto(duration, outgoingBpm, incomingBpm),
                duration,
            )
            if (mix == null) {
                Timber.tag("TransitionDebug").w("Mix: track too short (duration=%d).", duration)
                mixPlaybackState.remove(currentMediaItem.mediaId)
                engine.cancelNext()
                engine.setPauseAtEndOfMediaItems(shouldPause = false)
                return@collectLatest
            }

            Timber.tag("TransitionDebug").d(
                "Mix scheduled: out=%d ms, in=%d ms, window=%d ms, preset=%s, authored=%s",
                mix.outPointMs, mix.inStartMs, mix.windowMs, mix.preset, stored != null
            )

            mixPlaybackState.set(
                ActiveMix(
                    songId = currentMediaItem.mediaId,
                    outPointMs = mix.outPointMs,
                    windowMs = mix.windowMs,
                    outVolume = mix.outVolume,
                    inVolume = mix.inVolume,
                )
            )
            engine.setPauseAtEndOfMediaItems(shouldPause = true)
            val settings = TransitionSettings(
                mode = TransitionMode.OVERLAP,
                durationMs = mix.windowMs.toInt(),
            )

            // The incoming song's own mix point, for its seek bar: needs its duration, which is
            // known once the prepared player is ready. Try now and again just before the mix.
            var incomingPublished = false
            var incomingAttempts = 0

            while (player.currentPosition < mix.outPointMs && currentCoroutineContext().isActive) {
                if (!incomingPublished && incomingAttempts < MAX_INCOMING_MIX_ATTEMPTS) {
                    incomingAttempts++
                    incomingPublished = publishIncomingSongMix(nextMediaItem, playlistId)
                }
                val remaining = mix.outPointMs - player.currentPosition
                val sleep = when {
                    remaining > 5000 -> 1000L
                    remaining > 1000 -> 250L
                    else -> 50L
                }.coerceAtMost(remaining).coerceAtLeast(1L)
                delay(sleep)
            }
            if (!incomingPublished) publishIncomingSongMix(nextMediaItem, playlistId)

            if (!currentCoroutineContext().isActive) {
                engine.setPauseAtEndOfMediaItems(shouldPause = false)
                return@collectLatest
            }

            // Already past the planned start (seek, resume): shrink the window to what is left
            // of the outgoing song rather than skipping the mix.
            val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
            if (remaining < MixPlanner.MIN_WINDOW_MS) {
                Timber.tag("TransitionDebug").w("Mix: too close to end (%d ms left). Skipping.", remaining)
                engine.cancelNext()
                engine.setPauseAtEndOfMediaItems(shouldPause = false)
                return@collectLatest
            }
            val window = mix.windowMs.coerceAtMost(remaining)
            engine.performTransition(settings.copy(durationMs = window.toInt()), mix)
        }
    }

    /**
     * Works out where [incoming] will itself mix into the song after it and publishes that, so the
     * player's seek bar for [incoming] is right from the moment the player switches to it
     * instead of changing length once the current mix is over. Returns false when it couldn't
     * yet (the prepared song's duration isn't known, or there is no following song).
     */
    private suspend fun publishIncomingSongMix(incoming: MediaItem, playlistId: String): Boolean {
        val incomingDuration = engine.preparedNextDurationMs() ?: return false
        val following = engine.getNextTransitionTarget(incoming, engine.masterPlayer.repeatMode)
            ?: return true // nothing follows: the song plays to its end, no mix point to show
        val followingItem = following.mediaItem

        val pairFromPlaylist = MediaItemBuilder.playlistIdOf(incoming) == playlistId &&
            MediaItemBuilder.playlistIdOf(followingItem) == playlistId
        val stored = if (pairFromPlaylist) {
            transitionRepository.getMixTransition(playlistId, incoming.mediaId, followingItem.mediaId).first()
        } else {
            null
        }
        val mix = MixPlanner.fit(
            stored ?: MixPlanner.auto(
                incomingDuration,
                trackBpmRepository.bpmFor(incoming),
                trackBpmRepository.bpmFor(followingItem),
            ),
            incomingDuration,
        ) ?: return true

        mixPlaybackState.set(
            ActiveMix(
                songId = incoming.mediaId,
                outPointMs = mix.outPointMs,
                windowMs = mix.windowMs,
                outVolume = mix.outVolume,
                inVolume = mix.inVolume,
            )
        )
        return true
    }

    /**
     * Cleans up resources and listeners.
     */
    fun release() {
        Timber.tag("TransitionDebug").d("Releasing controller.")
        transitionSchedulerJob?.cancel()
        mixPlaybackState.clear()
        engine.removePlayerSwapListener(swapListener)
        transitionListener?.let { currentObservedPlayer?.removeListener(it) }
        transitionListener = null
        currentObservedPlayer = null
        scope.cancel()
    }
}
