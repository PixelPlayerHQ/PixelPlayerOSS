package com.lostf1sh.pixelplayeross.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.lostf1sh.pixelplayeross.BuildConfig
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.preferences.EqualizerPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import com.lostf1sh.pixelplayeross.utils.AudioMetaUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles a [DebugPerformanceReport] from already-known data: device/build info,
 * single-pass DB aggregates, the live player/engine state, user settings, and the
 * passively-accumulated [PerformanceMetrics].
 *
 * It deliberately does no expensive probing — no per-file metadata reads, no
 * filesystem stats, no decoding. Everything it reads was either captured while the
 * app did its normal work or is a cheap single SQL aggregate. [generate] runs off
 * the main thread.
 */
@Singleton
class DebugPerformanceReportCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val engine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository
) {
    /** Lossless audio container/codec short names as produced by [AudioMetaUtils.mimeTypeToFormat]. */
    private val losslessFormats = setOf("flac", "alac", "wav", "aiff")

    /** Player/engine fields that must be read on the player's (Main) thread. */
    private data class EngineState(
        val format: DualPlayerEngine.AudioFormatSnapshot?,
        val decoderName: String?,
        val decoderHardware: Boolean?,
        val audioOffloadEnabled: Boolean
    )

    suspend fun generate(): DebugPerformanceReport {
        // ExoPlayer is thread-confined to its application (Main) thread and throws on
        // wrong-thread access, so read all player/engine state on Main *before* the
        // IO assembly below. These are O(1) field reads — no work happens on Main.
        val engineState = withContext(Dispatchers.Main) {
            val decoder = engine.activeDecoderInfo.value
            EngineState(
                format = engine.currentAudioFormatSnapshot(),
                decoderName = decoder?.name,
                decoderHardware = decoder?.isHardware,
                audioOffloadEnabled = engine.isAudioOffloadEnabled
            )
        }

        return withContext(Dispatchers.IO) {
            val metrics = PerformanceMetrics.snapshot()
            val stats = musicDao.getLibraryAudioStats()
            val mimeRows = musicDao.getMimeTypeCounts()

            val mimeCounts = mimeRows.associate { (it.mimeType ?: "unknown") to it.count }
            val losslessCount = mimeRows
                .filter { AudioMetaUtils.mimeTypeToFormat(it.mimeType) in losslessFormats }
                .sumOf { it.count }

            // Read playback-related settings once.
            val crossfadeEnabled = userPreferencesRepository.isCrossfadeEnabledFlow.first()
            val crossfadeDuration = userPreferencesRepository.crossfadeDurationFlow.first()
            val audioOutputMode = userPreferencesRepository.audioOutputModeFlow.first().storageKey
            val replayGainEnabled = userPreferencesRepository.replayGainEnabledFlow.first()
            val replayGainAlbumMode = userPreferencesRepository.replayGainUseAlbumGainFlow.first()
            val equalizerEnabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()

            DebugPerformanceReport(
                generatedAtIso = isoNow(),
                device = collectDevice(),
                app = collectApp(),
                library = collectLibrary(stats, mimeCounts, metrics),
                hiRes = collectHiRes(stats, losslessCount, metrics),
                artwork = collectArtwork(metrics),
                playback = collectPlayback(
                    format = engineState.format,
                    decoderName = engineState.decoderName,
                    decoderHardware = engineState.decoderHardware,
                    audioOffloadEnabled = engineState.audioOffloadEnabled,
                    metrics = metrics,
                    audioOutputMode = audioOutputMode,
                    crossfadeEnabled = crossfadeEnabled,
                    crossfadeDuration = crossfadeDuration,
                    equalizerEnabled = equalizerEnabled,
                    replayGainEnabled = replayGainEnabled,
                    replayGainAlbumMode = replayGainAlbumMode
                ),
                controllers = collectControllers(metrics),
                timings = metrics.timings.mapValues { (_, t) ->
                    ReportTiming(
                        count = t.count,
                        minMs = t.minMs,
                        avgMs = t.avgMs,
                        maxMs = t.maxMs,
                        lastMs = t.lastMs
                    )
                },
                offloadEvents = metrics.offloadEvents.map {
                    OffloadEventEntry(it.elapsedRealtimeMs, it.reason)
                },
                advancedDiagnostics = collectAdvancedDiagnostics(),
                notes = buildNotes()
            )
        }
    }

    private fun collectDevice(): DeviceSection {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return DeviceSection(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            device = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            memoryClassMb = activityManager.memoryClass,
            isLowRamDevice = activityManager.isLowRamDevice,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem
        )
    }

    private fun collectApp(): AppSection = AppSection(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        buildType = BuildConfig.BUILD_TYPE,
        applicationId = BuildConfig.APPLICATION_ID
    )

    private fun collectLibrary(
        stats: com.lostf1sh.pixelplayeross.data.database.LibraryAudioStatsRow,
        mimeCounts: Map<String, Int>,
        metrics: PerformanceMetrics.Snapshot
    ): LibrarySection = LibrarySection(
        totalSongs = stats.totalCount,
        localSongs = stats.localCount,
        cloudSongs = stats.cloudCount,
        mimeCounts = mimeCounts,
        maxBitrate = stats.maxBitrate?.takeIf { it > 0 },
        minSampleRate = stats.minSampleRate?.takeIf { it > 0 },
        maxSampleRate = stats.maxSampleRate?.takeIf { it > 0 },
        maxObservedChannels = metrics.maxes[PerformanceMetrics.Maxes.PLAYBACK_CHANNEL_COUNT]?.toInt(),
        maxObservedPcmEncoding = metrics.maxes[PerformanceMetrics.Maxes.PLAYBACK_PCM_ENCODING]?.toInt(),
        estFileSizeMinBytes = stats.estMinBytes,
        estFileSizeAvgBytes = stats.estAvgBytes,
        estFileSizeMaxBytes = stats.estMaxBytes
    )

    private fun collectHiRes(
        stats: com.lostf1sh.pixelplayeross.data.database.LibraryAudioStatsRow,
        losslessCount: Int,
        metrics: PerformanceMetrics.Snapshot
    ): HiResSection = HiResSection(
        hiResCount = stats.hiResCount,
        ultraHiResCount = stats.ultraHiResCount,
        losslessCodecCount = losslessCount,
        likelyExpensiveToDecodeCount = stats.likelyExpensiveCount,
        multichannelPlaybackObservations =
            metrics.counters[PerformanceMetrics.Counters.MULTICHANNEL_PLAYBACKS]?.toInt() ?: 0,
        largeArtworkObservations =
            metrics.counters[PerformanceMetrics.Counters.ARTWORK_LARGE]?.toInt() ?: 0
    )

    private fun collectArtwork(metrics: PerformanceMetrics.Snapshot): ArtworkSection {
        val maxBytes = metrics.maxes[PerformanceMetrics.Maxes.ARTWORK_BYTES]
        return ArtworkSection(
            embeddedArtSeen = (maxBytes ?: 0L) > 0L,
            maxEmbeddedBytes = maxBytes?.takeIf { it > 0L },
            maxDecodedWidth = metrics.maxes[PerformanceMetrics.Maxes.DECODED_ARTWORK_WIDTH]?.toInt(),
            maxDecodedHeight = metrics.maxes[PerformanceMetrics.Maxes.DECODED_ARTWORK_HEIGHT]?.toInt(),
            cacheHits = metrics.counters[PerformanceMetrics.Counters.ARTWORK_CACHE_HIT] ?: 0L,
            cacheMisses = metrics.counters[PerformanceMetrics.Counters.ARTWORK_CACHE_MISS] ?: 0L,
            freshExtractions = metrics.counters[PerformanceMetrics.Counters.ARTWORK_EXTRACTED_FRESH] ?: 0L,
            extractionTiming = metrics.timings[PerformanceMetrics.Timings.ARTWORK_EXTRACT]?.toReportTiming(),
            decodeTiming = metrics.timings[PerformanceMetrics.Timings.ARTWORK_DECODE]?.toReportTiming()
        )
    }

    private fun collectPlayback(
        format: DualPlayerEngine.AudioFormatSnapshot?,
        decoderName: String?,
        decoderHardware: Boolean?,
        audioOffloadEnabled: Boolean,
        metrics: PerformanceMetrics.Snapshot,
        audioOutputMode: String,
        crossfadeEnabled: Boolean,
        crossfadeDuration: Int,
        equalizerEnabled: Boolean,
        replayGainEnabled: Boolean,
        replayGainAlbumMode: Boolean
    ): PlaybackSection = PlaybackSection(
        currentMime = format?.sampleMimeType,
        sampleRate = format?.sampleRate?.takeIf { it > 0 },
        bitrate = format?.bitrate?.takeIf { it > 0 },
        channelCount = format?.channelCount?.takeIf { it > 0 },
        pcmEncoding = format?.pcmEncoding?.takeIf { it > 0 },
        decoderName = decoderName,
        decoderHardware = decoderHardware,
        audioOffloadEnabled = audioOffloadEnabled,
        offloadFallbackCount = metrics.counters[PerformanceMetrics.Counters.OFFLOAD_FALLBACKS] ?: 0L,
        audioOutputMode = audioOutputMode,
        crossfadeEnabled = crossfadeEnabled,
        crossfadeDurationMs = crossfadeDuration,
        equalizerEnabled = equalizerEnabled,
        replayGainEnabled = replayGainEnabled,
        replayGainAlbumMode = replayGainAlbumMode
    )

    private fun collectControllers(metrics: PerformanceMetrics.Snapshot): ControllerSection =
        ControllerSection(
            widgetActive = metrics.widgetActive,
            wearActive = metrics.controllers.any { it.isWear },
            connectedControllers = metrics.controllers.map {
                ConnectedController(it.packageName, it.isWear)
            }
        )

    private fun collectAdvancedDiagnostics(): AdvancedDiagnosticsSection {
        val snapshot = AdvancedPerformanceDiagnostics.snapshot()
        return AdvancedDiagnosticsSection(
            enabled = snapshot.enabled,
            sessionStartedIso = snapshot.sessionStartedEpochMs?.let(::isoFromEpochMs),
            expiresAtIso = snapshot.expiresAtEpochMs?.let(::isoFromEpochMs),
            eventCount = snapshot.events.size,
            droppedEventCount = snapshot.droppedEventCount,
            events = snapshot.events.map { event ->
                AdvancedDiagnosticEventEntry(
                    elapsedRealtimeMs = event.elapsedRealtimeMs,
                    type = event.type,
                    name = event.name,
                    details = event.details
                )
            }
        )
    }

    private fun buildNotes(): List<String> = listOf(
        "Hi-res = sample rate > 48 kHz; ultra-hi-res = sample rate >= 176.4 kHz.",
        "File sizes are estimated from bitrate × duration (raw sizes are not stored).",
        "Channel count, bit depth, multichannel and embedded-artwork figures are observed " +
            "while the app works (scan / playback); they reflect this session, not an exhaustive library probe.",
        "Timings are in milliseconds and only appear once at least one sample was collected.",
        "Advanced diagnostics are opt-in and only appear when enabled before reproducing lag.",
        "This report contains no file paths, titles, or artists and is safe to share."
    )

    private fun PerformanceMetrics.TimingSnapshot.toReportTiming() =
        ReportTiming(count = count, minMs = minMs, avgMs = avgMs, maxMs = maxMs, lastMs = lastMs)

    private fun isoNow(): String = isoFromEpochMs(System.currentTimeMillis())

    private fun isoFromEpochMs(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMs))
    }
}
