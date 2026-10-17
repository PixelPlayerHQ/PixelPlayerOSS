package com.lostf1sh.pixelplayeross.data.service.player

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.flac.FlacExtractor
import com.lostf1sh.pixelplayeross.data.model.AudioOutputMode
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import com.lostf1sh.pixelplayeross.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeStreamProxy

data class ActiveDecoderInfo(
    val name: String,
    val isHardware: Boolean
)

internal class TransitionRunTracker {
    private var generation = 0L

    fun start(): Long {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(runId: Long): Boolean = runId == generation
}

internal data class PlayerRebuildQueueWindow(
    val startIndex: Int,
    val usesWindowedQueue: Boolean
)

internal fun shouldUsePresentedAuxiliaryForPlayerRebuild(
    transitionRunning: Boolean,
    auxiliaryPlayerPresented: Boolean,
    auxiliaryMediaItemCount: Int
): Boolean {
    return transitionRunning && auxiliaryPlayerPresented && auxiliaryMediaItemCount > 0
}

internal fun selectPlayerRebuildQueueWindow(
    useAuxiliaryPlayer: Boolean,
    activeWindowStartIndex: Int,
    activePlayerUsesWindowedQueue: Boolean,
    preparedWindowStartIndex: Int,
    preparedPlayerUsesWindowedQueue: Boolean
): PlayerRebuildQueueWindow {
    return if (useAuxiliaryPlayer) {
        PlayerRebuildQueueWindow(preparedWindowStartIndex, preparedPlayerUsesWindowedQueue)
    } else {
        PlayerRebuildQueueWindow(activeWindowStartIndex, activePlayerUsesWindowedQueue)
    }
}

internal fun shouldResumeAfterTransientAudioFocusLoss(
    masterPlayWhenReady: Boolean,
    masterIsPlaying: Boolean,
    transitionRunning: Boolean,
    auxiliaryPlayWhenReady: Boolean,
    auxiliaryIsPlaying: Boolean
): Boolean {
    return masterPlayWhenReady ||
        masterIsPlaying ||
        (transitionRunning && (auxiliaryPlayWhenReady || auxiliaryIsPlaying))
}

internal fun shouldDisableAudioOffloadByDefaultForDevice(
    manufacturer: String,
    brand: String,
    model: String,
    hardware: String,
    sdkInt: Int
): Boolean {
    val manufacturerName = manufacturer.trim().lowercase()
    val brandName = brand.trim().lowercase()
    val modelName = model.trim().lowercase()
    val hardwareName = hardware.trim().lowercase()

    val isXiaomiFamilyDevice = manufacturerName == "xiaomi" ||
        brandName == "xiaomi" ||
        brandName == "redmi" ||
        brandName == "poco"
    if (isXiaomiFamilyDevice && sdkInt >= 36) return true

    val isGooglePixelDevice = manufacturerName == "google" || brandName == "google"
    if (isGooglePixelDevice && sdkInt >= 37) return true

    val isLavaDevice =
        manufacturerName == "lava" ||
            brandName == "lava"
    val looksLikeMtkHardware =
        hardwareName.startsWith("mt") ||
            hardwareName.contains("mediatek") ||
            hardwareName.contains("mtk")
    val isReportedLxxFamily = modelName.startsWith("lxx") && isLavaDevice
    val isMtkLavaVariant = isLavaDevice && looksLikeMtkHardware

    return sdkInt >= 35 && (isReportedLxxFamily || isMtkLavaVariant)
}

/**
 * Offload is skipped at startup once it has stalled on this exact OS build. Keyed on the
 * build fingerprint so a system update (which may fix the vendor HAL) re-tests offload
 * instead of leaving the battery saving off forever.
 */
internal fun isAudioOffloadMarkedBroken(
    storedBuildFingerprint: String?,
    currentBuildFingerprint: String
): Boolean = !storedBuildFingerprint.isNullOrBlank() && storedBuildFingerprint == currentBuildFingerprint

internal fun shouldEnableAudioOffloadForMode(
    audioOffloadAvailableForSession: Boolean,
    audioOutputMode: AudioOutputMode
): Boolean {
    return audioOffloadAvailableForSession && audioOutputMode == AudioOutputMode.SYSTEM_DEFAULT
}

internal fun shouldTriggerAudioOffloadStallFallback(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    isCurrentMasterPlayer: Boolean,
    mediaIdMatches: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackSuppressionReason: Int
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        isCurrentMasterPlayer &&
        mediaIdMatches &&
        playWhenReady &&
        !isPlaying &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED
}

/**
 * Decides whether an early STATE_BUFFERING (within ~500ms of audio playing) should be read
 * as a HAL offload reset and trigger disabling offload for the session.
 *
 * The buffering is NOT treated as a HAL reset when it is explained by a recent user seek
 * ([isPostSeekBuffering]) or by a just-finished crossfade ([isPostTransitionBuffering]) —
 * in those cases the buffering is expected, and disabling offload would needlessly drop the
 * battery saving and rebuild the player (an audible glitch).
 */
internal fun shouldDisableAudioOffloadOnEarlyBuffering(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    lastPlayingAtMs: Long,
    timeSincePlayingMs: Long,
    isPostSeekBuffering: Boolean,
    isPostTransitionBuffering: Boolean,
    isPostMediaItemTransition: Boolean
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        lastPlayingAtMs > 0L &&
        timeSincePlayingMs < 500L &&
        !isPostSeekBuffering &&
        !isPostTransitionBuffering &&
        !isPostMediaItemTransition
}

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player. During a crossfade the MediaSession can
 * expose Player B early for UI continuity, while Player A remains alive to fade out.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinStreamProxy,
    private val cloudOfflineRepository: CloudOfflineRepository
) {
    private companion object {
        // A local file reaches READY in ~100ms with a working output path, so 1.5s of
        // "playWhenReady but not playing" already means the offload stream never started.
        // 4s was exactly the start delay users saw on devices whose offload HAL fails to open.
        private const val AUDIO_OFFLOAD_STALL_FALLBACK_MS = 1_500L
        private const val OFFLOAD_HEALTH_PREFS = "audio_offload_health"
        private const val KEY_OFFLOAD_STALLED_BUILD = "offload_stalled_build_fingerprint"
        private const val POST_TRANSITION_OFFLOAD_GUARD_MS = 2_000L
        private const val MAX_AUXILIARY_TIMELINE_ITEMS = QueueWindowPolicy.MAX_AUXILIARY_TIMELINE_ITEMS
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "navidrome", "jellyfin")
        private val CLOUD_PROXY_SCHEMES = setOf("navidrome", "jellyfin")
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var audioOutputMode: AudioOutputMode = AudioOutputMode.SYSTEM_DEFAULT
        private set
    private val offloadHealthPrefs by lazy {
        context.getSharedPreferences(OFFLOAD_HEALTH_PREFS, Context.MODE_PRIVATE)
    }
    private var audioOffloadEnabled =
        !shouldDisableAudioOffloadByDefault() && !isAudioOffloadKnownBrokenOnThisBuild()
    private val audioOffloadEnabledForCurrentMode: Boolean
        get() = shouldEnableAudioOffloadForMode(audioOffloadEnabled, audioOutputMode)
    private val transitionRunTracker = TransitionRunTracker()
    private var transitionJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private var transitionRunning = false
    private var auxiliaryPlayerPresented = false
    private var preResolutionJob: Job? = null
    private var queueSnapshot: List<MediaItem> = emptyList()
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    private lateinit var playerA: ExoPlayer
    private var playerB: ExoPlayer? = null

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()
    private val onPausedAtEndOfItemListeners = mutableListOf<() -> Unit>()

    /**
     * Sleep-timer hold, owned by SleepTimerController. Survives player swaps because it is
     * re-applied to whichever player is master: crossfades stay off while it is set, and
     * [SleepTimerHold.pauseAtEndOfCurrentItem] pauses the master when the current item ends
     * whatever the repeat or shuffle mode (ExoPlayer pauses before advancing or looping).
     */
    data class SleepTimerHold(
        val suppressCrossfade: Boolean = false,
        val pauseAtEndOfCurrentItem: Boolean = false,
        val keepCpuAwake: Boolean = false,
    )

    private val _sleepTimerHold = MutableStateFlow(SleepTimerHold())
    val sleepTimerHold: StateFlow<SleepTimerHold> = _sleepTimerHold.asStateFlow()

    /** What TransitionController asked for; the effective flag also honours the sleep-timer hold. */
    private var transitionPauseAtEnd = false
    private var lastSleepingForOffload = false

    @Volatile
    private var preferredAudioDevice: android.media.AudioDeviceInfo? = null

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    private data class PlayerRebuildState(
        val mediaItems: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int,
        val shuffleModeEnabled: Boolean,
        val volume: Float,
        val pauseAtEndOfMediaItems: Boolean,
        val playbackParameters: PlaybackParameters,
        val queueWindow: PlayerRebuildQueueWindow,
        val promotedPresentedAuxiliary: Boolean
    )

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

    /**
     * Whether ExoPlayer audio offload is currently enabled for this session. Exposed
     * read-only for the diagnostic performance report.
     */
    val isAudioOffloadEnabled: Boolean
        get() = audioOffloadEnabledForCurrentMode

    /** Lightweight, allocation-cheap snapshot of the live audio format, for diagnostics. */
    data class AudioFormatSnapshot(
        val sampleMimeType: String?,
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int,
        val bitrate: Int
    )

    /** Returns the current master-player audio format, or null when nothing is decoding. */
    fun currentAudioFormatSnapshot(): AudioFormatSnapshot? {
        if (!::playerA.isInitialized) return null
        val format = playerA.audioFormat ?: return null
        fun Int.orZero() = if (this == Format.NO_VALUE) 0 else this
        val bitrate = when {
            format.averageBitrate != Format.NO_VALUE -> format.averageBitrate
            format.peakBitrate != Format.NO_VALUE -> format.peakBitrate
            else -> 0
        }
        return AudioFormatSnapshot(
            sampleMimeType = format.sampleMimeType,
            sampleRate = format.sampleRate.orZero(),
            channelCount = format.channelCount.orZero(),
            pcmEncoding = format.pcmEncoding.orZero(),
            bitrate = bitrate
        )
    }

    private var sharedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false
    private var lastPlayWhenReadyAtMs: Long = 0L
    private var lastPlayingAtMs: Long = 0L
    private var lastSeekAtMs: Long = 0L
    private var lastTransitionFinishedAtMs: Long = 0L
    private var lastMediaItemTransitionAtMs: Long = 0L

    /**
     * Set by MusicService once ReplayGain for the incoming track is known.
     * The crossfade loop reads this at the end instead of hard-coding 1f,
     * so the incoming track reaches its correct RG volume without a jump.
     * Reset to null after each transition.
     */
    var incomingTrackReplayGainVolume: Float? = null

    /** Master volume (ReplayGain or user) captured when a crossfade starts; null otherwise. */
    private var volumeBeforeTransition: Float? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB?.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                val auxiliaryPlayer = playerB
                isFocusLossPause = shouldResumeAfterTransientAudioFocusLoss(
                    masterPlayWhenReady = playerA.playWhenReady,
                    masterIsPlaying = playerA.isPlaying,
                    transitionRunning = transitionRunning,
                    auxiliaryPlayWhenReady = auxiliaryPlayer?.playWhenReady == true,
                    auxiliaryIsPlaying = auxiliaryPlayer?.isPlaying == true
                )
                playerA.playWhenReady = false
                auxiliaryPlayer?.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB?.playWhenReady = true
                }
            }
        }
    }

    private val masterPlayerListener = object : Player.Listener, AnalyticsListener, ExoPlayer.AudioOffloadListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                lastPlayWhenReadyAtMs = SystemClock.elapsedRealtime()
                requestAudioFocus()
                scheduleAudioOffloadFallbackIfNeeded(playerA)
            } else {
                cancelAudioOffloadFallback()
                // A crossfade's outgoing player also pauses at its end, but by then the
                // incoming one is the track the sleep timer is waiting on.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    _sleepTimerHold.value.pauseAtEndOfCurrentItem &&
                    !transitionRunning
                ) {
                    onPausedAtEndOfItemListeners.toList().forEach { it() }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastPlayingAtMs = SystemClock.elapsedRealtime()
                cancelAudioOffloadFallback()
            }
        }

        /**
         * Fires when ExoPlayer believes the audio HAL is producing output via
         * offload and the renderer thread can stop polling — at that point the
         * CPU genuinely doesn't need a wake lock to keep playing audio. When
         * [sleepingForOffload] flips back to false (track change, format
         * mismatch, fallback path), restore [C.WAKE_MODE_LOCAL] so the
         * non-offload PCM path keeps the CPU awake correctly.
         *
         * Battery: this is what actually lets the SoC race-to-sleep during
         * music playback. The static [C.WAKE_MODE_LOCAL] we set at build time
         * is the safe default; this callback is the dynamic optimisation.
         */
        @Suppress("UnsafeOptInUsageError")
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            lastSleepingForOffload = sleepingForOffload
            applyOffloadAwareWakeMode()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHardware = AudioDecoderPolicy.isLikelyHardwareDecoder(decoderName)
            _activeDecoderInfo.value = ActiveDecoderInfo(decoderName, isHardware)
            Timber.tag("DualPlayerEngine").d("Audio decoder initialized: %s (Hardware: %b)", decoderName, isHardware)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != 0 && _activeAudioSessionId.value != audioSessionId) {
                _activeAudioSessionId.value = audioSessionId
                Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastMediaItemTransitionAtMs = SystemClock.elapsedRealtime()
            cancelAudioOffloadFallback()
            
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelNext()
            }

            applyWakeModeForCurrentItem()

            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(600)
                try {
                    val currentIndex = playerA.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        if (currentIndex + 1 < playerA.mediaItemCount) {
                            playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                        if (currentIndex - 1 >= 0) {
                            playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Pre-resolution error")
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (transitionRunning) return
            // While the master timeline is only a crossfade window over the real queue,
            // rebuilding the snapshot from it would silently truncate the queue to
            // MAX_AUXILIARY_TIMELINE_ITEMS. restoreFullMasterTimelineIfWindowed() clears
            // the flag as soon as the full timeline is back, so this is a short window.
            if (activePlayerUsesWindowedQueue) return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || queueSnapshot.isEmpty()) {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    val timeSincePlayingMs = now - lastPlayingAtMs
                    val timeSinceSeekMs = now - lastSeekAtMs
                    val timeSinceTransitionMs = now - lastTransitionFinishedAtMs
                    val timeSinceMediaItemTransitionMs = now - lastMediaItemTransitionAtMs
                    val isPostSeekBuffering = lastSeekAtMs > 0L && timeSinceSeekMs < 1_500L
                    val isPostTransitionBuffering = lastTransitionFinishedAtMs > 0L &&
                        timeSinceTransitionMs < POST_TRANSITION_OFFLOAD_GUARD_MS
                    val isPostMediaItemTransition = lastMediaItemTransitionAtMs > 0L &&
                        timeSinceMediaItemTransitionMs < 2_000L
                    if (shouldDisableAudioOffloadOnEarlyBuffering(
                            audioOffloadEnabled = audioOffloadEnabledForCurrentMode,
                            transitionRunning = transitionRunning,
                            lastPlayingAtMs = lastPlayingAtMs,
                            timeSincePlayingMs = timeSincePlayingMs,
                            isPostSeekBuffering = isPostSeekBuffering,
                            isPostTransitionBuffering = isPostTransitionBuffering,
                            isPostMediaItemTransition = isPostMediaItemTransition
                        )
                    ) {
                        disableAudioOffloadForSession(
                            reason = "HAL offload reset detected: STATE_BUFFERING after ${timeSincePlayingMs}ms of playback"
                        )
                    } else {
                        scheduleAudioOffloadFallbackIfNeeded(playerA)
                    }
                }
                Player.STATE_READY -> scheduleAudioOffloadFallbackIfNeeded(playerA)
                Player.STATE_IDLE, Player.STATE_ENDED -> cancelAudioOffloadFallback()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                lastSeekAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun addMasterPlayerListeners(player: ExoPlayer) {
        player.addListener(masterPlayerListener)
        player.addAnalyticsListener(masterPlayerListener)
        player.addAudioOffloadListener(masterPlayerListener)
    }

    private fun removeMasterPlayerListeners(player: ExoPlayer) {
        player.removeListener(masterPlayerListener)
        player.removeAnalyticsListener(masterPlayerListener)
        player.removeAudioOffloadListener(masterPlayerListener)
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    fun addTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.add(listener)
    }

    fun removeTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.remove(listener)
    }

    fun addTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.add(listener)
    }

    /**
     * Notifies the engine that an external caller (UI seek, etc.) is about to issue a
     * seek through the MediaController. Used to mark the upcoming STATE_BUFFERING as
     * seek-driven so the HAL-reset heuristic does not trigger a player rebuild that
     * would race with the in-flight seek command.
     *
     * Setting this here (synchronously, before the seek dispatches) is more reliable
     * than waiting for onPositionDiscontinuity, which is delivered on the next event
     * batch and can race with onPlaybackStateChanged on some Media3 versions.
     */
    fun notifyExternalSeekInitiated() {
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() {
            initialize()
            return playerA
        }

    fun isTransitionRunning(): Boolean = transitionRunning

    fun isUsingWindowedQueue(): Boolean = activePlayerUsesWindowedQueue

    fun getFullQueue(): List<MediaItem> = ensureQueueSnapshot()

    fun getCurrentAbsoluteIndex(): Int {
        if (!::playerA.isInitialized) return 0
        val mediaItem = playerA.currentMediaItem ?: return playerA.currentMediaItemIndex.coerceAtLeast(0)
        val snapshot = ensureQueueSnapshot()
        val index = resolveCurrentAbsoluteIndex(mediaItem, snapshot)
        return if (index == C.INDEX_UNSET) {
            if (activePlayerUsesWindowedQueue) {
                (activeWindowStartIndex + playerA.currentMediaItemIndex).coerceIn(0, (snapshot.size - 1).coerceAtLeast(0))
            } else {
                playerA.currentMediaItemIndex.coerceAtLeast(0)
            }
        } else {
            index
        }
    }

    fun triggerAdjacentPreResolution() {
        if (!::playerA.isInitialized) return
        preResolutionJob?.cancel()
        val currentIndex = playerA.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET) {
            val adjacentCloudUris = mutableListOf<Uri>()
            if (currentIndex + 1 < playerA.mediaItemCount) {
                playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in CLOUD_PROXY_SCHEMES) adjacentCloudUris.add(uri)
                }
            }
            if (currentIndex - 1 >= 0) {
                playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in CLOUD_PROXY_SCHEMES) adjacentCloudUris.add(uri)
                }
            }

            if (adjacentCloudUris.isNotEmpty()) {
                preResolutionJob = scope.launch {
                    delay(600)
                    try {
                        for (uriToResolve in adjacentCloudUris) {
                            resolveCloudUri(uriToResolve)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution triggered manually")
                    }
                }
            }
        }
    }

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    private var isReleased = false
    private val resolvedUriCache = LruCache<String, Uri>(100)

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { }
        }
        playerB?.let { try { it.release() } catch (e: Exception) { } }
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)

        _activeAudioSessionId.value = playerA.audioSessionId
        isReleased = false
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAcceptsDelayedFocusGain(true)
            .build()

        val result = audioManager.requestAudioFocus(request)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                audioFocusRequest = request
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                audioFocusRequest = request
                isFocusLossPause = true
                playerA.playWhenReady = false
                if (transitionRunning) playerB?.playWhenReady = false
            }
            else -> {
                Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
                playerA.playWhenReady = false
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun scheduleAudioOffloadFallbackIfNeeded(player: ExoPlayer) {
        cancelAudioOffloadFallback()
        if (!audioOffloadEnabledForCurrentMode || transitionRunning || !player.playWhenReady || player.isPlaying) return
        if (!isLikelyLocalMedia(player.currentMediaItem)) return

        val watchedMediaId = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        bufferingFallbackJob = scope.launch {
            delay(AUDIO_OFFLOAD_STALL_FALLBACK_MS)

            val currentMediaId = player.currentMediaItem?.mediaId
            val shouldFallback = shouldTriggerAudioOffloadStallFallback(
                audioOffloadEnabled = audioOffloadEnabledForCurrentMode,
                transitionRunning = transitionRunning,
                isCurrentMasterPlayer = player === playerA,
                mediaIdMatches = currentMediaId == watchedMediaId,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackSuppressionReason = player.playbackSuppressionReason
            )
            if (!shouldFallback) return@launch

            // The offload output never started. Remember it for this OS build so the next
            // app start begins on the PCM path instead of paying the stall again.
            rememberAudioOffloadBrokenOnThisBuild()
            disableAudioOffloadForSession(
                reason = "Local media did not produce audio for " +
                    "${AUDIO_OFFLOAD_STALL_FALLBACK_MS}ms (state=${player.playbackState})"
            )
        }
    }

    private fun cancelAudioOffloadFallback() {
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
    }

    private fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
    }

    private fun wakeModeFor(mediaItem: MediaItem?): Int {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
            C.WAKE_MODE_NETWORK
        } else {
            C.WAKE_MODE_LOCAL
        }
    }

    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = wakeModeFor(playerA.currentMediaItem)
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            playerB?.setWakeMode(mode)
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d", mode)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to update wake mode")
        }
    }

    private fun shouldDisableAudioOffloadByDefault(): Boolean {
        return shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    private fun isAudioOffloadKnownBrokenOnThisBuild(): Boolean =
        isAudioOffloadMarkedBroken(
            storedBuildFingerprint = runCatching {
                offloadHealthPrefs.getString(KEY_OFFLOAD_STALLED_BUILD, null)
            }.getOrNull(),
            currentBuildFingerprint = Build.FINGERPRINT
        )

    private fun rememberAudioOffloadBrokenOnThisBuild() {
        runCatching {
            offloadHealthPrefs.edit().putString(KEY_OFFLOAD_STALLED_BUILD, Build.FINGERPRINT).apply()
        }
    }

    private fun disableAudioOffloadForSession(reason: String) {
        if (!audioOffloadEnabledForCurrentMode) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload fallback during active transition. %s", reason)
            return
        }

        audioOffloadEnabled = false
        rebuildPlayersPreservingMasterState(
            logMessage = "Audio offload disabled for current session. $reason"
        )
    }

    private fun rebuildPlayersPreservingMasterState(logMessage: String) {
        val state = capturePlayerRebuildState()
        cancelTransitionForPlayerRebuild()
        cancelAudioOffloadFallback()

        removeMasterPlayerListeners(playerA)
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB?.release()
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)
        playerA.volume = state.volume
        playerA.pauseAtEndOfMediaItems =
            state.pauseAtEndOfMediaItems || _sleepTimerHold.value.pauseAtEndOfCurrentItem
        playerA.playbackParameters = state.playbackParameters

        if (state.mediaItems.isNotEmpty()) {
            activeWindowStartIndex = state.queueWindow.startIndex
            activePlayerUsesWindowedQueue = state.queueWindow.usesWindowedQueue
            playerA.setMediaItems(state.mediaItems, state.currentIndex, state.positionMs)
            restoreFullMasterTimelineIfWindowed()
            playerA.repeatMode = state.repeatMode
            playerA.shuffleModeEnabled = state.shuffleModeEnabled
            playerA.prepare()
            playerA.playWhenReady = state.playWhenReady
            applyWakeModeForCurrentItem()
        } else {
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
        }
        resetPreparedWindowState()

        _activeAudioSessionId.value = playerA.audioSessionId
        onPlayerSwappedListeners.forEach { it(playerA) }
        if (state.promotedPresentedAuxiliary) {
            lastTransitionFinishedAtMs = SystemClock.elapsedRealtime()
            onTransitionFinishedListeners.forEach { it() }
        }

        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    private fun capturePlayerRebuildState(): PlayerRebuildState {
        val auxiliaryPlayer = playerB
        val useAuxiliaryPlayer = shouldUsePresentedAuxiliaryForPlayerRebuild(
            transitionRunning = transitionRunning,
            auxiliaryPlayerPresented = auxiliaryPlayerPresented,
            auxiliaryMediaItemCount = auxiliaryPlayer?.mediaItemCount ?: 0
        )
        val sourcePlayer = if (useAuxiliaryPlayer) auxiliaryPlayer!! else playerA
        val queueWindow = selectPlayerRebuildQueueWindow(
            useAuxiliaryPlayer = useAuxiliaryPlayer,
            activeWindowStartIndex = activeWindowStartIndex,
            activePlayerUsesWindowedQueue = activePlayerUsesWindowedQueue,
            preparedWindowStartIndex = preparedWindowStartIndex,
            preparedPlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        )
        val mediaItemCount = sourcePlayer.mediaItemCount
        val mediaItems = ArrayList<MediaItem>(mediaItemCount)
        for (i in 0 until mediaItemCount) mediaItems.add(sourcePlayer.getMediaItemAt(i))
        val currentIndex = sourcePlayer.currentMediaItemIndex.coerceIn(
            minimumValue = 0,
            maximumValue = (mediaItemCount - 1).coerceAtLeast(0)
        )
        val positionMs = if (useAuxiliaryPlayer) {
            sourcePlayer.currentPosition.coerceAtLeast(0L)
        } else {
            sourcePlayer.currentPosition.takeIf { it > 5_000L } ?: 0L
        }
        val transitionWasRunning = transitionRunning
        val stableVolume = when {
            useAuxiliaryPlayer -> incomingTrackReplayGainVolume ?: 1f
            transitionWasRunning -> volumeBeforeTransition ?: sourcePlayer.volume
            else -> sourcePlayer.volume
        }

        return PlayerRebuildState(
            mediaItems = mediaItems,
            currentIndex = currentIndex,
            positionMs = positionMs,
            playWhenReady = sourcePlayer.playWhenReady,
            repeatMode = sourcePlayer.repeatMode,
            shuffleModeEnabled = sourcePlayer.shuffleModeEnabled,
            volume = stableVolume,
            pauseAtEndOfMediaItems = if (transitionWasRunning) {
                false
            } else {
                sourcePlayer.pauseAtEndOfMediaItems
            },
            playbackParameters = sourcePlayer.playbackParameters,
            queueWindow = queueWindow,
            promotedPresentedAuxiliary = useAuxiliaryPlayer
        )
    }

    private fun cancelTransitionForPlayerRebuild() {
        if (!transitionRunning && transitionJob == null) return

        invalidateActiveTransition()
        resetPreparedWindowState()
        incomingTrackReplayGainVolume = null
        if (::playerA.isInitialized) {
            restoreVolumeBeforeTransition()
            setPauseAtEndOfMediaItems(false)
        }
        Timber.tag("TransitionDebug").d("Cancelled active transition before rebuilding players.")
    }

    private fun invalidateActiveTransition() {
        transitionRunTracker.invalidate()
        transitionJob?.cancel()
        transitionJob = null
        transitionRunning = false
        auxiliaryPlayerPresented = false
    }

    /**
     * Returns a [DefaultLoadControl] tuned to the device's RAM tier.
     *
     * Low-RAM devices ([ActivityManager.isLowRamDevice]) receive halved buffer ceilings
     * to prevent memory pressure when both players co-exist during a crossfade.
     * [bufferForPlaybackMs] is set to 250 ms and rebuffer to 500 ms for sub-150ms instant
     * playback start on local media files.
     */
    private fun buildAdaptiveLoadControl(): DefaultLoadControl {
        val isLowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isLowRamDevice
        return if (isLowRam) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    30_000,
                    250,
                    500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,
                    60_000,
                    250,
                    500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    /**
     * Returns the process-wide audio session id every player is pinned to, generating it on first
     * use, or `null` when the platform refuses to allocate one (then ExoPlayer keeps its
     * per-instance behaviour).
     */
    private fun sharedAudioSessionIdOrNull(): Int? {
        if (sharedAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            sharedAudioSessionId = try {
                audioManager.generateAudioSessionId()
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").w(e, "Failed to generate a shared audio session id")
                AudioManager.ERROR
            }
        }
        return sharedAudioSessionId.takeIf {
            it != AudioManager.ERROR && it != C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun buildPlayer(): ExoPlayer {
        // Decoder selection happens on the playback thread. Keep it tied to this player's sink
        // even while a mode change is rebuilding the players.
        val outputMode = audioOutputMode
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos, outputMode)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                if (outputMode.usesUnmodifiedMedia3AudioSink) {
                    // Android's audio policy may
                    // grant a DIRECT thread for a compatible device/format, or safely fall back
                    // to the mixed path. This deliberately does not promise exclusive output.
                    return requireNotNull(
                        super.buildAudioSink(
                            context,
                            false,
                            enableAudioOutputPlaybackParams
                        )
                    ) { "Media3 did not create its default AudioSink" }
                }
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(outputMode.usesFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
            }
        }.setEnableAudioFloatOutput(outputMode.usesFloatOutput)
         .setMediaCodecSelector(mediaCodecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val scheme = uri.scheme
                if (scheme in CLOUD_PROXY_SCHEMES) {
                    val originalUri = uri.toString()
                    val offlineUri = try {
                        runBlocking { cloudOfflineRepository.resolveLocalUri(originalUri) }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Offline copy lookup failed")
                        null
                    }
                    if (offlineUri != null) {
                        return dataSpec.buildUpon().setUri(offlineUri).build()
                    }
                    val resolved = resolvedUriCache.get(originalUri)
                        ?: resolveReadyCloudProxyUri(uri)?.also { proxyUri ->
                            resolvedUriCache.put(originalUri, proxyUri)
                        }
                    if (resolved != null) {
                        return dataSpec.buildUpon().setUri(resolved).build()
                    }
                    // Cache miss: resolve inline. resolveDataSpec runs on ExoPlayer's
                    // loading thread, where blocking I/O is allowed. This makes cloud
                    // playback independent of whether the dispatch path pre-resolved
                    // the URI — seeks into unresolved queue items, add-to-queue,
                    // controller-driven playback and restored queues would otherwise
                    // hand a raw cloud scheme to DefaultDataSource and surface a
                    // "Source error" toast.
                    Timber.tag("DualPlayerEngine").d("resolveDataSpec: cache miss for %s — resolving inline", scheme)
                    val inlineResolved = try {
                        runBlocking { resolveCloudUri(uri) }
                    } catch (e: Exception) {
                        // Keep loader failures on the IOException path: ExoPlayer treats
                        // unexpected RuntimeExceptions from a DataSource as fatal.
                        throw IOException("Failed to resolve $scheme stream", e)
                    }
                    if (inlineResolved != uri) {
                        return dataSpec.buildUpon().setUri(inlineResolved).build()
                    }
                    // No proxy can serve a raw cloud scheme; fail with a clear cause
                    // instead of letting DefaultDataSource report an opaque scheme error.
                    throw IOException("Could not resolve $scheme stream (offline or provider unavailable)")
                }
                return dataSpec
            }
        }
        
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory, resolver)
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setFlacExtractorFlags(FlacExtractor.FLAG_DISABLE_ID3_METADATA)

        val loadControl = buildAdaptiveLoadControl()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            if (!outputMode.usesUnmodifiedMedia3AudioSink) {
                sharedAudioSessionIdOrNull()?.let { setAudioSessionId(it) }
            }
            addListener(UnsupportedAudioListener(this))
            setAudioAttributes(audioAttributes, false)
            // Gapless support is required, not optional: on a HAL that only advertises plain
            // offload, the data written ahead for the next item is dropped at the automatic
            // transition and the track starts seconds in. Devices without gapless offload fall
            // back to the regular PCM path instead.
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabledForCurrentMode) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .setIsGaplessSupportRequired(true)
                .build()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(offloadPreferences)
                .build()
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            // Rebuilt players (offload fallback, output-mode change, crossfade auxiliary)
            // keep the user's chosen output.
            setPreferredAudioDevice(preferredAudioDevice)
            playWhenReady = false
        }
    }

    private fun resolveReadyCloudProxyUri(uri: Uri): Uri? {
        val uriString = uri.toString()
        val proxyUrl = when (uri.scheme) {
            "navidrome" -> navidromeStreamProxy
                .takeIf { it.isReady() }
                ?.resolveNavidromeUri(uriString)
            "jellyfin" -> jellyfinStreamProxy
                .takeIf { it.isReady() }
                ?.resolveJellyfinUri(uriString)
            else -> null
        }
        return proxyUrl?.let(Uri::parse)
    }

    private fun getOrCreateAuxiliaryPlayer(): ExoPlayer {
        playerB?.let { return it }
        return buildPlayer().also { player ->
            player.setWakeMode(currentWakeMode)
            playerB = player
        }
    }

    /**
     * Routes this app's playback to [device] (null = follow the system route). Applied to
     * both crossfade players so a transition never jumps back to the default output.
     */
    fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
        preferredAudioDevice = device
        if (::playerA.isInitialized) playerA.setPreferredAudioDevice(device)
        playerB?.setPreferredAudioDevice(device)
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        transitionPauseAtEnd = shouldPause
        applyPauseAtEndOfMediaItems()
    }

    private fun applyPauseAtEndOfMediaItems() {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems =
                transitionPauseAtEnd || _sleepTimerHold.value.pauseAtEndOfCurrentItem
        }
    }

    fun setSleepTimerHold(hold: SleepTimerHold) {
        val previous = _sleepTimerHold.value
        if (previous == hold) return
        _sleepTimerHold.value = hold
        applyPauseAtEndOfMediaItems()
        if (previous.keepCpuAwake != hold.keepCpuAwake) applyOffloadAwareWakeMode()
    }

    /** Fires on the main thread when the master paused because a sleep-timer hold asked it to. */
    fun addPausedAtEndOfItemListener(listener: () -> Unit) {
        onPausedAtEndOfItemListeners.add(listener)
    }

    fun removePausedAtEndOfItemListener(listener: () -> Unit) {
        onPausedAtEndOfItemListeners.remove(listener)
    }

    /**
     * While ExoPlayer reports the audio HAL is playing via offload, the CPU doesn't need a
     * wake lock to keep playing, so drop to [C.WAKE_MODE_NONE]. Not while a duration sleep
     * timer is pending, though: its countdown runs on a Handler, whose clock stops while the
     * CPU sleeps. Only local media is overridden; remote schemes keep [C.WAKE_MODE_NETWORK]
     * for the wifi lock.
     */
    private fun applyOffloadAwareWakeMode() {
        if (!::playerA.isInitialized) return
        val baseMode = wakeModeFor(playerA.currentMediaItem)
        val desiredMode = if (
            lastSleepingForOffload &&
            baseMode == C.WAKE_MODE_LOCAL &&
            !_sleepTimerHold.value.keepCpuAwake
        ) {
            C.WAKE_MODE_NONE
        } else {
            baseMode
        }
        if (currentWakeMode == desiredMode) return

        try {
            playerA.setWakeMode(desiredMode)
            playerB?.setWakeMode(desiredMode)
            currentWakeMode = desiredMode
            Timber.tag("DualPlayerEngine").d(
                "Wake mode -> %d (sleepingForOffload=%b)",
                desiredMode,
                lastSleepingForOffload
            )
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to apply offload-aware wake mode")
        }
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
            else -> currentAbsoluteIndex + 1
        }

        val targetItem = snapshot.getOrNull(targetIndex) ?: return null
        return TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = snapshot.size
        )
    }

    fun setAudioOutputMode(mode: AudioOutputMode) {
        val resolvedMode = if (
            mode == AudioOutputMode.PCM_FLOAT && !HiFiCapabilityChecker.isSupported()
        ) {
            Timber.tag("DualPlayerEngine")
                .w("PCM float output requested but device does not support PCM_FLOAT; using system default")
            AudioOutputMode.SYSTEM_DEFAULT
        } else {
            mode
        }
        if (audioOutputMode == resolvedMode) return
        audioOutputMode = resolvedMode
        rebuildPlayersPreservingMasterState("Audio output mode set to ${resolvedMode.storageKey}")
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        resolvePreferredCloudPlaybackValue(
            original = uri,
            resolveOffline = {
                try {
                    cloudOfflineRepository.resolveLocalUri(uriString)
                } catch (error: Exception) {
                    Timber.tag("DualPlayerEngine").w(error, "Offline copy lookup failed")
                    null
                }
            },
            resolveCachedRemote = { resolvedUriCache.get(uriString) },
            resolveRemote = {
                when (uri.scheme) {
                    "navidrome" -> resolveNavidromeUriAsync(uriString)
                    "jellyfin" -> resolveJellyfinUriAsync(uriString)
                    else -> null
                }
            },
            onRemoteResolved = { resolved -> resolvedUriCache.put(uriString, resolved) }
        )
    }

    private suspend fun resolveNavidromeUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!navidromeStreamProxy.ensureReady(5_000L)) return@withContext null
        navidromeStreamProxy.warmUpStreamUrl(uriString)
        navidromeStreamProxy.resolveNavidromeUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveJellyfinUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!jellyfinStreamProxy.ensureReady(5_000L)) return@withContext null
        jellyfinStreamProxy.warmUpStreamUrl(uriString)
        jellyfinStreamProxy.resolveJellyfinUri(uriString)?.let { Uri.parse(it) }
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val playerUri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val originalContentUri = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
        val canonicalUriString = selectCanonicalCloudPlaybackUri(
            playerUri = playerUri.toString(),
            originalContentUri = originalContentUri,
        )
        if (!isCanonicalCloudPlaybackUri(canonicalUriString)) return mediaItem

        val canonicalUri = Uri.parse(canonicalUriString)
        // Warm the offline/proxy path, but keep the durable URI in the player's timeline. The
        // ResolvingDataSource selects the current offline copy or proxy again for every load, so
        // removing a download cannot strand a queued item on a stale file:// URI.
        resolveCloudUri(canonicalUri)
        return if (playerUri == canonicalUri) {
            mediaItem
        } else {
            mediaItem.buildUpon().setUri(canonicalUri).build()
        }
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        prepareNext(target.mediaItem, target.absoluteIndex, startPositionMs)
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val preferredIndex = findMediaItemIndex(
            items = ensureQueueSnapshot(),
            mediaId = mediaItem.mediaId,
            preferAfterExclusive = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, queueSnapshot)
        )
        prepareNext(mediaItem, preferredIndex, startPositionMs)
    }

    private suspend fun prepareNext(mediaItem: MediaItem, preferredAbsoluteIndex: Int, startPositionMs: Long = 0L) {
        try {
            val snapshot = ensureQueueSnapshot()
            val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, snapshot)
            val targetIndex = when {
                preferredAbsoluteIndex in snapshot.indices &&
                    snapshot[preferredAbsoluteIndex].mediaId == mediaItem.mediaId -> preferredAbsoluteIndex
                else -> findMediaItemIndex(snapshot, mediaItem.mediaId, currentAbsoluteIndex)
            }
            val resolvedItem = resolveMediaItem(mediaItem)
            val auxiliaryPlayer = getOrCreateAuxiliaryPlayer()

            auxiliaryPlayer.stop()
            auxiliaryPlayer.clearMediaItems()

            if (targetIndex != C.INDEX_UNSET && snapshot.isNotEmpty()) {
                val count = snapshot.size
                val (start, end) = QueueWindowPolicy.windowBounds(targetIndex, count)
                val windowItems = ArrayList<MediaItem>(end - start)
                for (i in start until end) {
                    val item = snapshot[i]
                    windowItems.add(if (i == targetIndex) resolvedItem else item)
                }
                preparedWindowStartIndex = start
                preparedPlayerUsesWindowedQueue = count > MAX_AUXILIARY_TIMELINE_ITEMS
                auxiliaryPlayer.setMediaItems(windowItems, targetIndex - start, startPositionMs)
            } else {
                resetPreparedWindowState()
                auxiliaryPlayer.setMediaItem(resolvedItem)
                auxiliaryPlayer.seekTo(startPositionMs)
            }

            auxiliaryPlayer.prepare()
            auxiliaryPlayer.volume = 0f
            auxiliaryPlayer.pause()
        } catch (e: Exception) {
            resetPreparedWindowState()
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    fun cancelNext() {
        val shouldPublishMasterPlayer = auxiliaryPlayerPresented
        invalidateActiveTransition()
        resetPreparedWindowState()
        playerB?.takeIf { it.mediaItemCount > 0 }?.let { auxiliaryPlayer ->
            try {
                auxiliaryPlayer.stop()
                auxiliaryPlayer.clearMediaItems()
            } catch (e: Exception) { }
        }
        if (::playerA.isInitialized) {
            restoreVolumeBeforeTransition()
            if (shouldPublishMasterPlayer) {
                onPlayerSwappedListeners.forEach { it(playerA) }
            }
        }
        incomingTrackReplayGainVolume = null
        setPauseAtEndOfMediaItems(false)
    }

    /**
     * Puts the master player back at the volume it had before a crossfade started fading it.
     * Leaves it untouched when no crossfade ran, so ReplayGain or user volume survives queue
     * edits and other cancellations.
     */
    private fun restoreVolumeBeforeTransition() {
        volumeBeforeTransition?.let { playerA.volume = it }
        volumeBeforeTransition = null
    }

    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        if (volumeBeforeTransition == null) volumeBeforeTransition = playerA.volume
        val transitionRunId = transitionRunTracker.start()
        transitionRunning = true
        auxiliaryPlayerPresented = false
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (transitionRunTracker.isCurrent(transitionRunId)) {
                    Timber.tag("TransitionDebug").e(e, "Error performing transition")
                    restoreVolumeBeforeTransition()
                    setPauseAtEndOfMediaItems(false)
                    playerB?.stop()
                }
            } finally {
                if (transitionRunTracker.isCurrent(transitionRunId)) {
                    transitionJob = null
                    transitionRunning = false
                    auxiliaryPlayerPresented = false
                    lastTransitionFinishedAtMs = SystemClock.elapsedRealtime()
                    onTransitionFinishedListeners.forEach { it() }
                }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        val auxiliaryPlayer = playerB
        if (auxiliaryPlayer == null || auxiliaryPlayer.mediaItemCount == 0) {
            restoreVolumeBeforeTransition()
            setPauseAtEndOfMediaItems(false)
            return
        }

        if (auxiliaryPlayer.playbackState == Player.STATE_IDLE) auxiliaryPlayer.prepare()
        // READY can also mean that no audio track can be decoded. Do not fade into silence.
        if (!awaitPlayerReady(auxiliaryPlayer, 3000L)) {
            restoreVolumeBeforeTransition()
            setPauseAtEndOfMediaItems(false)
            return
        }

        val outgoingStartVolume = (volumeBeforeTransition ?: playerA.volume).coerceIn(0f, 1f)
        auxiliaryPlayer.volume = 0f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) playerA.play()
        auxiliaryPlayer.playWhenReady = true
        auxiliaryPlayer.play()

        val outgoingPlayer = playerA
        val incomingPlayer = auxiliaryPlayer

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        outgoingPlayer.pauseAtEndOfMediaItems = true
        incomingPlayer.pauseAtEndOfMediaItems = false
        auxiliaryPlayerPresented = true
        onTransitionDisplayPlayerListeners.forEach { it(incomingPlayer) }

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 32L
        val startedAtMs = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volIn = envelope(progress, settings.curveIn)
            val volOut = 1f - envelope(progress, settings.curveOut)
            val incomingTarget = incomingTrackReplayGainVolume ?: 1f
            incomingPlayer.volume = (volIn * incomingTarget).coerceIn(0f, 1f)
            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)

            if (elapsed >= duration) break
            delay(stepMs)
        }

        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTrackReplayGainVolume ?: 1f
        incomingTrackReplayGainVolume = null
        volumeBeforeTransition = null

        removeMasterPlayerListeners(outgoingPlayer)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        auxiliaryPlayerPresented = false
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB?.pauseAtEndOfMediaItems = false
        addMasterPlayerListeners(playerA)

        // Splice the rest of the queue back in *before* the new master is published, so
        // the MediaSession, the queue sheet and the persisted snapshot never observe the
        // truncated crossfade window.
        restoreFullMasterTimelineIfWindowed()

        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        playerB?.pause()
        playerB?.stop()
        playerB?.clearMediaItems()

        setPauseAtEndOfMediaItems(false)
    }

    private fun ensureQueueSnapshot(): List<MediaItem> {
        if (queueSnapshot.isEmpty() ||
            (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount)
        ) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    private fun refreshQueueSnapshotFromMaster(windowStartIndex: Int, usesWindowedQueue: Boolean) {
        if (!::playerA.isInitialized) return

        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    private fun resolveCurrentAbsoluteIndex(mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    private fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    /**
     * Puts the whole queue back on the master player after a crossfade swap or a player
     * rebuild that promoted the windowed auxiliary player.
     *
     * [prepareNext] only gives the auxiliary player a [MAX_AUXILIARY_TIMELINE_ITEMS]-wide
     * window around the incoming track, so preparing a crossfade stays cheap on huge queues.
     * Once that player becomes the master, its timeline *is* the app's queue: the
     * MediaSession, the queue sheet, every absolute-index queue edit and the persisted
     * snapshot all read it. Leaving it windowed truncated large queues to the window.
     *
     * The tail is appended before the head so the append indices stay valid; prepending
     * afterwards shifts the current index, which Media3 handles without interrupting playback.
     */
    private fun restoreFullMasterTimelineIfWindowed() {
        if (!activePlayerUsesWindowedQueue) return
        if (!::playerA.isInitialized) return

        val snapshot = queueSnapshot
        val windowStart = activeWindowStartIndex
        val windowLength = playerA.mediaItemCount

        val plan = QueueWindowPolicy.restorePlan(windowStart, windowLength, snapshot.size)
        // If the snapshot no longer describes the queue this window was cut from we cannot
        // splice safely; fall back to trusting the player timeline rather than corrupting it.
        if (plan == null || !windowMatchesSnapshot(windowStart, windowStart + windowLength, snapshot)) {
            Timber.tag("DualPlayerEngine").w(
                "Cannot restore full timeline: window [%d,%d) does not match snapshot of %d",
                windowStart, windowStart + windowLength, snapshot.size
            )
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            return
        }

        try {
            plan.tail?.let { playerA.addMediaItems(ArrayList(snapshot.subList(it.first, it.last + 1))) }
            plan.head?.let { playerA.addMediaItems(0, ArrayList(snapshot.subList(it.first, it.last + 1))) }
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").e(e, "Failed to restore full master timeline")
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            return
        }

        refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
    }

    /**
     * Cheap sanity check that the master's windowed timeline is still the slice of
     * [snapshot] it was cut from. Anything that edited the queue while the window was in
     * place invalidates the offsets, and splicing against it would duplicate or drop tracks.
     */
    private fun windowMatchesSnapshot(
        windowStart: Int,
        windowEnd: Int,
        snapshot: List<MediaItem>
    ): Boolean {
        if (windowEnd <= windowStart) return false
        val lastPlayerIndex = windowEnd - windowStart - 1
        return playerA.getMediaItemAt(0).mediaId == snapshot[windowStart].mediaId &&
            playerA.getMediaItemAt(lastPlayerIndex).mediaId == snapshot[windowEnd - 1].mediaId
    }

    private fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    private suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.playbackState == Player.STATE_READY) {
            return !AudioDecoderPolicy.hasUnsupportedAudio(player.currentTracks)
        }
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_BUFFERING) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(
                                playbackState == Player.STATE_READY &&
                                    !AudioDecoderPolicy.hasUnsupportedAudio(player.currentTracks)
                            )
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }

    fun release() {
        invalidateActiveTransition()
        preResolutionJob?.cancel()
        cancelAudioOffloadFallback()
        scope.coroutineContext[Job]?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        playerB?.release()
        playerB = null
        isReleased = true
    }
}
