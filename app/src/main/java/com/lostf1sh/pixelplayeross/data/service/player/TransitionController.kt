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
import com.lostf1sh.pixelplayeross.data.model.TransitionSource
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.TransitionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
) {
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null
    private var currentObservedPlayer: Player? = null

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
                if (isPlaying && (job == null || job.isCompleted)) {
                    Timber.tag("TransitionDebug").d("Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    Timber.tag("TransitionDebug").d("Timeline changed (reason=%d). Cancelling pending transition.", reason)
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()

                     engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
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
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        transitionSchedulerJob?.cancel()
        engine.setPauseAtEndOfMediaItems(shouldPause = false)

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

            val playlistId = currentMediaItem.mediaMetadata.extras?.getString("playlistId")
            val fromTrackId = currentMediaItem.mediaId
            val toTrackId = nextMediaItem.mediaId

            Timber.tag("TransitionDebug").d("Resolving settings for playlistId=%s, %s -> %s", playlistId, fromTrackId, toTrackId)

            val isCrossfadeEnabledFlow = userPreferencesRepository.isCrossfadeEnabledFlow

            val settingsFlow = if (playlistId != null) {
                transitionRepository.resolveTransitionSettings(playlistId, fromTrackId, toTrackId)
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
                // (cached in-memory after the first read — never decodes audio).
                // A user-authored per-pair rule (PLAYLIST_SPECIFIC) is explicit intent and
                // is never overridden by the automatic tempo logic.
                val smartApplies = snapshot.isSmartEnabled &&
                    resolution.source != TransitionSource.PLAYLIST_SPECIFIC
                val outgoingBpm = if (smartApplies) trackBpmRepository.bpmFor(currentMediaItem) else null
                val incomingBpm = if (smartApplies) trackBpmRepository.bpmFor(nextMediaItem) else null

                Timber.tag("TransitionDebug").d("Preparing next track for overlap: %s", nextMediaItem.mediaId)
                engine.prepareNext(transitionTarget)

                var duration = player.duration
                while ((duration == C.TIME_UNSET || duration <= 0) && isActive) {
                    delay(500)
                    duration = player.duration
                    Timber.tag("TransitionDebug").v("Waiting for duration... (%d)", duration)
                }

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
     * Cleans up resources and listeners.
     */
    fun release() {
        Timber.tag("TransitionDebug").d("Releasing controller.")
        transitionSchedulerJob?.cancel()
        engine.removePlayerSwapListener(swapListener)
        transitionListener?.let { currentObservedPlayer?.removeListener(it) }
        transitionListener = null
        currentObservedPlayer = null
        scope.cancel()
    }
}
