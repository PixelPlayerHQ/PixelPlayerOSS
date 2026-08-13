package com.lostf1sh.pixelplayeross.data.diagnostics

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight, process-wide, in-memory recorder for performance diagnostics.
 *
 * This is intentionally a plain `object` (no DI) so that static call sites can
 * record samples without any extra plumbing.
 *
 * Design constraints:
 *  - Must be cheap on hot paths. Recording a sample is O(1): a couple of
 *    comparisons under a short uncontended `synchronized` block, or an atomic op.
 *  - Must never block the main thread or allocate on the hot path beyond the
 *    first time a metric name is seen.
 *  - Holds only aggregates (count/min/max/sum) plus a tiny bounded event log, so
 *    memory stays flat regardless of library size or session length.
 *
 * Nothing here changes playback behaviour; it only observes work that already
 * happens and exposes a [snapshot] for the [DebugPerformanceReportCollector].
 */
object PerformanceMetrics {

    /** Stable metric names. Kept as constants so producers and the collector agree. */
    object Timings {
        const val FULL_SCAN = "full_scan"
        const val METADATA_READ = "metadata_read"
        const val ARTWORK_EXTRACT = "artwork_extract"
        const val ARTWORK_DECODE = "artwork_decode"
        const val PLAYBACK_PREPARE = "playback_prepare"
        const val AUDIO_DECODER_INIT = "audio_decoder_init"
        const val TRANSITION = "transition"
        const val WIDGET_UPDATE = "widget_update"
        const val MEDIASESSION_ITEM_BUILD = "mediasession_item_build"
    }

    object Counters {
        const val SCAN_RUNS = "scan_runs"
        const val SONGS_SCANNED = "songs_scanned"
        const val METADATA_FALLBACK_JAUDIOTAGGER = "metadata_fallback_jaudiotagger"
        const val ARTWORK_CACHE_HIT = "artwork_cache_hit"
        const val ARTWORK_CACHE_MISS = "artwork_cache_miss"
        const val ARTWORK_EXTRACTED_FRESH = "artwork_extracted_fresh"
        const val ARTWORK_LARGE = "artwork_large" // embedded artwork over LARGE_ARTWORK_BYTES
        const val OFFLOAD_FALLBACKS = "offload_fallbacks"
        const val MULTICHANNEL_PLAYBACKS = "multichannel_playbacks"
    }

    object Maxes {
        const val ARTWORK_BYTES = "artwork_bytes"
        const val DECODED_ARTWORK_WIDTH = "decoded_artwork_width"
        const val DECODED_ARTWORK_HEIGHT = "decoded_artwork_height"
        const val PLAYBACK_CHANNEL_COUNT = "playback_channel_count"
        const val PLAYBACK_SAMPLE_RATE = "playback_sample_rate"
        const val PLAYBACK_PCM_ENCODING = "playback_pcm_encoding"
    }

    /** Embedded artwork at or above this size is flagged as "large" for the report. */
    const val LARGE_ARTWORK_BYTES = 1_000_000L

    private const val MAX_OFFLOAD_EVENTS = 20

    /** Aggregated timing statistic for one metric. All times are milliseconds. */
    class TimingStat {
        private var count = 0L
        private var sumMs = 0.0
        private var minMs = Double.MAX_VALUE
        private var maxMs = 0.0
        private var lastMs = 0.0

        @Synchronized
        fun record(ms: Double) {
            if (ms < 0) return
            count += 1
            sumMs += ms
            if (ms < minMs) minMs = ms
            if (ms > maxMs) maxMs = ms
            lastMs = ms
        }

        @Synchronized
        fun snapshot(): TimingSnapshot? {
            if (count == 0L) return null
            return TimingSnapshot(
                count = count,
                minMs = minMs,
                avgMs = sumMs / count,
                maxMs = maxMs,
                lastMs = lastMs
            )
        }
    }

    data class TimingSnapshot(
        val count: Long,
        val minMs: Double,
        val avgMs: Double,
        val maxMs: Double,
        val lastMs: Double
    )

    data class OffloadEvent(
        val elapsedRealtimeMs: Long,
        val reason: String
    )

    data class ControllerInfo(
        val packageName: String,
        val isWear: Boolean,
        val firstSeenElapsedMs: Long
    )

    data class Snapshot(
        val timings: Map<String, TimingSnapshot>,
        val counters: Map<String, Long>,
        val maxes: Map<String, Long>,
        val offloadEvents: List<OffloadEvent>,
        val controllers: List<ControllerInfo>,
        val widgetActive: Boolean
    )

    private val timings = ConcurrentHashMap<String, TimingStat>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val maxes = ConcurrentHashMap<String, AtomicLong>()
    private val offloadEvents = ArrayDeque<OffloadEvent>(MAX_OFFLOAD_EVENTS)
    private val controllers = ConcurrentHashMap<String, ControllerInfo>()
    private val advancedTimelineTimingNames = setOf(
        Timings.FULL_SCAN,
        Timings.ARTWORK_EXTRACT,
        Timings.ARTWORK_DECODE,
        Timings.PLAYBACK_PREPARE,
        Timings.AUDIO_DECODER_INIT,
        Timings.TRANSITION,
        Timings.MEDIASESSION_ITEM_BUILD
    )

    @Volatile
    var widgetActive: Boolean = false
        private set

    // ---- Producers -----------------------------------------------------------

    fun recordTiming(name: String, durationMs: Long) {
        if (durationMs < 0) return
        timings.computeIfAbsent(name) { TimingStat() }.record(durationMs.toDouble())
        if (AdvancedPerformanceDiagnostics.isEnabled && name in advancedTimelineTimingNames) {
            AdvancedPerformanceDiagnostics.recordEvent(
                type = when (name) {
                    Timings.ARTWORK_EXTRACT, Timings.ARTWORK_DECODE ->
                        AdvancedPerformanceDiagnostics.EventTypes.ARTWORK
                    Timings.FULL_SCAN ->
                        AdvancedPerformanceDiagnostics.EventTypes.WORKER
                    else ->
                        AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK
                },
                name = "timing_$name",
                details = mapOf("durationMs" to durationMs.toString())
            )
        }
    }

    /** Times [block], records the elapsed wall time under [name], and returns its result. */
    inline fun <T> time(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            recordTiming(name, (System.nanoTime() - start) / 1_000_000)
        }
    }

    fun increment(name: String, delta: Long = 1) {
        counters.computeIfAbsent(name) { AtomicLong(0) }.addAndGet(delta)
    }

    /** Records [value] only if it exceeds the current maximum for [name]. */
    fun recordMax(name: String, value: Long) {
        if (value <= 0) return
        val holder = maxes.computeIfAbsent(name) { AtomicLong(0) }
        var prev = holder.get()
        while (value > prev && !holder.compareAndSet(prev, value)) {
            prev = holder.get()
        }
    }

    /** Records embedded-artwork size, updating the large-artwork counter when appropriate. */
    fun recordEmbeddedArtwork(bytes: Long) {
        if (bytes <= 0) return
        recordMax(Maxes.ARTWORK_BYTES, bytes)
        if (bytes >= LARGE_ARTWORK_BYTES) increment(Counters.ARTWORK_LARGE)
    }

    /**
     * Records the dimensions of a bitmap the app decoded as part of its normal work
     * (e.g. notification/MediaSession artwork). This only reads an already-decoded
     * bitmap's width/height — the diagnostic never triggers a decode itself.
     */
    fun recordDecodedArtworkDimensions(width: Int, height: Int) {
        recordMax(Maxes.DECODED_ARTWORK_WIDTH, width.toLong())
        recordMax(Maxes.DECODED_ARTWORK_HEIGHT, height.toLong())
    }

    /** Records the live audio format seen at decoder init time. */
    fun recordPlaybackFormat(channelCount: Int, sampleRate: Int, pcmEncoding: Int) {
        recordMax(Maxes.PLAYBACK_CHANNEL_COUNT, channelCount.toLong())
        recordMax(Maxes.PLAYBACK_SAMPLE_RATE, sampleRate.toLong())
        if (pcmEncoding > 0) recordMax(Maxes.PLAYBACK_PCM_ENCODING, pcmEncoding.toLong())
        if (channelCount > 2) increment(Counters.MULTICHANNEL_PLAYBACKS)
    }

    fun recordOffloadFallback(reason: String, elapsedRealtimeMs: Long) {
        increment(Counters.OFFLOAD_FALLBACKS)
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.OFFLOAD,
            name = "offload_fallback",
            elapsedRealtimeMs = elapsedRealtimeMs
        ) {
            mapOf("reason" to reason)
        }
        synchronized(offloadEvents) {
            if (offloadEvents.size >= MAX_OFFLOAD_EVENTS) offloadEvents.pollFirst()
            offloadEvents.addLast(OffloadEvent(elapsedRealtimeMs, reason))
        }
    }

    fun recordControllerConnected(
        packageName: String,
        isWear: Boolean,
        elapsedRealtimeMs: Long
    ) {
        controllers.putIfAbsent(
            packageName,
            ControllerInfo(packageName, isWear, elapsedRealtimeMs)
        )
    }

    fun setWidgetActive(active: Boolean) {
        widgetActive = active
    }

    // ---- Consumer ------------------------------------------------------------

    fun snapshot(): Snapshot {
        val timingSnap = timings.entries
            .mapNotNull { (k, v) -> v.snapshot()?.let { k to it } }
            .toMap()
        val counterSnap = counters.entries.associate { (k, v) -> k to v.get() }
        val maxSnap = maxes.entries.associate { (k, v) -> k to v.get() }
        val events = synchronized(offloadEvents) { offloadEvents.toList() }
        return Snapshot(
            timings = timingSnap,
            counters = counterSnap,
            maxes = maxSnap,
            offloadEvents = events,
            controllers = controllers.values.sortedBy { it.firstSeenElapsedMs },
            widgetActive = widgetActive
        )
    }

    /** Test-only: clears all accumulated state. */
    fun resetForTest() {
        timings.clear()
        counters.clear()
        maxes.clear()
        synchronized(offloadEvents) { offloadEvents.clear() }
        controllers.clear()
        widgetActive = false
    }
}
