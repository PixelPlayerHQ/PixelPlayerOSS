package com.lostf1sh.pixelplayeross.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
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
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null
    private var currentObservedPlayer: Player? = null

    private val swapListener: (Player) -> Unit = { newPlayer ->
        Timber.tag("TransitionDebug").d("Controller detected player swap. Moving listener.")
        transitionListener?.let { listener ->
            currentObservedPlayer?.removeListener(listener)
            currentObservedPlayer = newPlayer
            newPlayer.addListener(listener)

            // Trigger check for the new player immediately
            if (newPlayer.isPlaying) {
                newPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
            }
        }
    }

    /**
     * Attaches the controller to the player engine to start listening for state changes.
     */
    fun initialize() {
        if (transitionListener != null) return // Already initialized

        Timber.tag("TransitionDebug").d("Initializing TransitionController...")

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Timber.tag("TransitionDebug").d("onMediaItemTransition: %s (reason=%d)", mediaItem?.mediaId, reason)
                // When we naturally move to a new song, ensure pauseAtEnd is OFF by default.
                engine.setPauseAtEndOfMediaItems(shouldPause = false)

                mediaItem?.let { scheduleTransitionFor(it) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted)) {
                    // If playback resumes and no transition is scheduled, schedule one.
                    Timber.tag("TransitionDebug").d("Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    // The queue has changed (e.g., reordered, item removed).
                    Timber.tag("TransitionDebug").d("Timeline changed (reason=%d). Cancelling pending transition.", reason)
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()

                    // Try to reschedule for the current item
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

        // Initial setup
        currentObservedPlayer = engine.masterPlayer
        currentObservedPlayer?.addListener(transitionListener!!)
        engine.addPlayerSwapListener(swapListener)
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        // Cancel any existing job first and reset pauseAtEnd so a stale `true`
        // from the previous job doesn't cause an unexpected pause.
        transitionSchedulerJob?.cancel()
        engine.setPauseAtEndOfMediaItems(shouldPause = false)

        transitionSchedulerJob = scope.launch {
            // If a transition is currently running, cancel it immediately.
            // We are on a new track (or starting fresh), so the old crossfade is stale.
            if (engine.isTransitionRunning()) {
                Timber.tag("TransitionDebug").d("Cancelling active transition to schedule next...")
                engine.cancelNext()
            }

            val player = engine.masterPlayer
            val repeatMode = player.repeatMode
            val transitionTarget = engine.getNextTransitionTarget(currentMediaItem, repeatMode)

            // If there is no next track and we're not looping, cancel any pending transition and stop.
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

            // Check global crossfade toggle first
            val isCrossfadeEnabledFlow = userPreferencesRepository.isCrossfadeEnabledFlow

            // Use collectLatest to automatically cancel and restart the logic if settings change.
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

            combine(settingsFlow, isCrossfadeEnabledFlow) { resolution, isEnabled ->
                Pair(resolution, isEnabled)
            }.distinctUntilChanged() // Crucial: prevents restarting the job if the same settings are emitted again
            .collectLatest { (resolution, isEnabled) ->

                val settings = resolution.settings
                Timber.tag("TransitionDebug").d(
                    "Settings resolved: Mode=%s, Duration=%dms, GlobalEnabled=%s, Source=%s",
                    settings.mode, settings.durationMs, isEnabled, resolution.source
                )

                val isGloballyDisabled = resolution.source == TransitionSource.GLOBAL_DEFAULT && !isEnabled

                // If globally disabled and we are using the global defaults, use no transition.
                if (isGloballyDisabled) {
                    Timber.tag("TransitionDebug").d("Crossfade globally disabled. Using default gap.")
                    engine.cancelNext()
                    engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    return@collectLatest
                }

                // If transition is disabled or has no duration, do nothing.
                if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                    Timber.tag("TransitionDebug").d("Transition disabled or zero duration.")
                    engine.cancelNext()
                    engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    return@collectLatest
                }

                Timber.tag("TransitionDebug").d("Preparing next track for overlap: %s", nextMediaItem.mediaId)
                engine.prepareNext(transitionTarget)

                // Wait for the player to report a valid duration.
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

                val maxFadeDuration = (duration - guardWindow).coerceAtLeast(minFade)
                val effectiveDuration = settings.durationMs.toLong()
                    .coerceAtLeast(minFade)
                    .coerceAtMost(maxFadeDuration)

                val transitionPoint = duration - effectiveDuration

                Timber.tag("TransitionDebug").d(
                    "Scheduled %s at %d ms (SongDur: %d). Fade duration: %d ms",
                    settings.mode, transitionPoint, duration, effectiveDuration
                )

                // --- CRITICAL FIX: Enable Pause At End ---
                // We want to control the transition manually, so we prevent auto-advance.
                engine.setPauseAtEndOfMediaItems(shouldPause = true)
                Timber.tag("TransitionDebug").d("Enabled pauseAtEndOfMediaItems to prevent auto-skip.")

                if (transitionPoint <= player.currentPosition) {
                    val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        val adjustedDuration = remaining.coerceAtMost(effectiveDuration)
                        Timber.tag("TransitionDebug").w("Already past transition point! Triggering immediately.")
                        engine.performTransition(settings.copy(durationMs = adjustedDuration.toInt()))
                    } else {
                        Timber.tag("TransitionDebug").w("Too close to end (%d ms left). Skipping to avoid glitch.", remaining)
                        engine.cancelNext()
                        engine.setPauseAtEndOfMediaItems(shouldPause = false)
                    }
                    return@collectLatest
                }

                // Wait loop with adaptive sleep. 250ms near-end cadence still lands the crossfade
                // within ±125ms of the target — imperceptible for a multi-second overlap, and 5×
                // fewer wakeups in the last second of every track.
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

                // Final check to ensure the job wasn't cancelled while waiting.
                if (isActive) {
                    val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        val adjustedDuration = remaining.coerceAtMost(effectiveDuration)
                        Timber.tag("TransitionDebug").d("FIRING TRANSITION NOW!")
                        engine.performTransition(settings.copy(durationMs = adjustedDuration.toInt()))
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
