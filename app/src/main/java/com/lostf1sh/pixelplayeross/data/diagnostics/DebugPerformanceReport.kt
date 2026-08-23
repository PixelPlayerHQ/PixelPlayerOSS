package com.lostf1sh.pixelplayeross.data.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Structured, shareable diagnostic snapshot for performance investigations.
 * Designed so a user can export one report and let us classify whether lag
 * comes from scanning, metadata, artwork, playback prepare, transitions,
 * offload, widgets, MediaSession, or UI work.
 *
 * The model is deliberately a pure data structure (no Android imports) so it can
 * be unit tested on the JVM. [DebugPerformanceReportCollector] populates it.
 *
 * Privacy: this report contains only aggregates, counts, device capabilities and
 * format metadata. It never includes file paths, titles, artists, or any other
 * library content, so it is safe to share.
 */
@Serializable
data class DebugPerformanceReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val generatedAtIso: String,
    val device: DeviceSection,
    val app: AppSection,
    val library: LibrarySection,
    val hiRes: HiResSection,
    val artwork: ArtworkSection,
    val playback: PlaybackSection,
    val controllers: ControllerSection,
    val timings: Map<String, ReportTiming>,
    val offloadEvents: List<OffloadEventEntry>,
    val advancedDiagnostics: AdvancedDiagnosticsSection = AdvancedDiagnosticsSection(),
    val notes: List<String>
) {
    fun toJson(): String = PRETTY_JSON.encodeToString(this)

    /** Human-readable rendering for users who prefer text over JSON. */
    fun toPlainText(): String = buildString {
        appendLine("PixelPlayer performance report (schema v$schemaVersion)")
        appendLine("Generated: $generatedAtIso")
        appendLine()

        section("DEVICE")
        kv("Manufacturer", device.manufacturer)
        kv("Model", device.model)
        kv("Brand", device.brand)
        kv("Android", "${device.androidVersion} (SDK ${device.sdkInt})")
        kv("ABIs", device.supportedAbis.joinToString(", "))
        kv("RAM class", "${device.memoryClassMb} MB")
        kv("Total RAM", bytes(device.totalRamBytes))
        kv("Low-RAM device", device.isLowRamDevice.toString())

        section("APP")
        kv("Version", "${app.versionName} (${app.versionCode})")
        kv("Build type", app.buildType)
        kv("Application id", app.applicationId)

        section("LIBRARY")
        kv("Total songs", library.totalSongs.toString())
        kv("Local songs", library.localSongs.toString())
        kv("Cloud/remote songs", library.cloudSongs.toString())
        kv("Max bitrate", library.maxBitrate?.let { "${it / 1000} kbps" } ?: UNKNOWN)
        kv("Sample rate range", sampleRateRange(library.minSampleRate, library.maxSampleRate))
        kv("Max channels (observed)", library.maxObservedChannels?.toString() ?: NOT_OBSERVED)
        kv("Max PCM encoding (observed)", library.maxObservedPcmEncoding?.let(::pcmEncodingLabel) ?: NOT_OBSERVED)
        kv("Est. file size min/avg/max", estSizes(library))
        if (library.mimeCounts.isNotEmpty()) {
            appendLine("  MIME/type counts:")
            library.mimeCounts.entries.sortedByDescending { it.value }.forEach { (mime, count) ->
                appendLine("    $mime: $count")
            }
        }

        section("HI-RES CLASSIFICATION")
        kv("Hi-res files (>48 kHz)", hiRes.hiResCount.toString())
        kv("Ultra-hi-res files (>=176.4 kHz)", hiRes.ultraHiResCount.toString())
        kv("Lossless-codec files", hiRes.losslessCodecCount.toString())
        kv("Likely expensive to decode", hiRes.likelyExpensiveToDecodeCount.toString())
        kv("Multichannel playbacks (observed)", hiRes.multichannelPlaybackObservations.toString())
        kv("Large-artwork files (>=1 MB, observed)", hiRes.largeArtworkObservations.toString())

        section("ARTWORK")
        kv("Embedded art seen", artwork.embeddedArtSeen.toString())
        kv("Max embedded bytes", artwork.maxEmbeddedBytes?.let(::bytes) ?: NOT_OBSERVED)
        kv("Max decoded dimensions (observed)", dimensions(artwork.maxDecodedWidth, artwork.maxDecodedHeight))
        kv("Cache hits", artwork.cacheHits.toString())
        kv("Cache misses", artwork.cacheMisses.toString())
        kv("Fresh extractions", artwork.freshExtractions.toString())
        kv("Extraction timing", artwork.extractionTiming?.format() ?: NOT_OBSERVED)
        kv("Decode timing", artwork.decodeTiming?.format() ?: NOT_OBSERVED)

        section("PLAYBACK")
        kv("Current MIME", playback.currentMime ?: NOT_PLAYING)
        kv("Sample rate", playback.sampleRate?.let { "$it Hz" } ?: NOT_PLAYING)
        kv("Bitrate", playback.bitrate?.let { "${it / 1000} kbps" } ?: NOT_PLAYING)
        kv("Channels", playback.channelCount?.toString() ?: NOT_PLAYING)
        kv("PCM encoding", playback.pcmEncoding?.let(::pcmEncodingLabel) ?: NOT_PLAYING)
        kv("Decoder", playback.decoderName ?: UNKNOWN)
        kv("Decoder class", playback.decoderHardware?.let { if (it) "hardware" else "software" } ?: UNKNOWN)
        kv("Audio offload enabled", playback.audioOffloadEnabled.toString())
        kv("Offload fallbacks this session", playback.offloadFallbackCount.toString())
        kv("Audio output mode", playback.audioOutputMode)
        kv("Crossfade", if (playback.crossfadeEnabled) "on (${playback.crossfadeDurationMs} ms)" else "off")
        kv("Equalizer", playback.equalizerEnabled.toString())
        kv("ReplayGain", if (playback.replayGainEnabled) "on (${if (playback.replayGainAlbumMode) "album" else "track"})" else "off")

        section("CONTROLLERS")
        kv("Widget active", controllers.widgetActive.toString())
        kv("Wear active", controllers.wearActive.toString())
        if (controllers.connectedControllers.isNotEmpty()) {
            appendLine("  Connected controllers:")
            controllers.connectedControllers.forEach {
                val tags = buildList {
                    if (it.isWear) add("wear")
                }.joinToString(",").ifEmpty { "external" }
                appendLine("    ${it.packageName} ($tags)")
            }
        }

        section("TIMINGS (ms)")
        if (timings.isEmpty()) {
            appendLine("  (no timing samples collected yet)")
        } else {
            timings.entries.sortedBy { it.key }.forEach { (name, t) ->
                appendLine("  $name: ${t.format()}")
            }
        }

        if (offloadEvents.isNotEmpty()) {
            section("OFFLOAD EVENTS")
            offloadEvents.forEach { appendLine("  [+${it.elapsedRealtimeMs} ms] ${it.reason}") }
        }

        if (advancedDiagnostics.enabled || advancedDiagnostics.events.isNotEmpty()) {
            section("ADVANCED DIAGNOSTICS")
            kv("Enabled", advancedDiagnostics.enabled.toString())
            kv("Started", advancedDiagnostics.sessionStartedIso ?: NOT_OBSERVED)
            kv("Expires", advancedDiagnostics.expiresAtIso ?: NOT_OBSERVED)
            kv("Events retained", advancedDiagnostics.eventCount.toString())
            kv("Events dropped", advancedDiagnostics.droppedEventCount.toString())
            advancedDiagnostics.events.forEach { event ->
                val details = event.details.entries.joinToString(", ") { (key, value) -> "$key=$value" }
                appendLine(
                    buildString {
                        append("  [+${event.elapsedRealtimeMs} ms] ${event.type}/${event.name}")
                        if (details.isNotBlank()) append(" ($details)")
                    }
                )
            }
        }

        if (notes.isNotEmpty()) {
            section("NOTES")
            notes.forEach { appendLine("  - $it") }
        }
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("== $title ==")
    }

    private fun StringBuilder.kv(key: String, value: String) {
        appendLine("  $key: $value")
    }

    companion object {
        const val SCHEMA_VERSION = 3
        private const val UNKNOWN = "unknown"
        private const val NOT_OBSERVED = "not observed"
        private const val NOT_PLAYING = "not playing"

        private val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        fun bytes(value: Long): String = when {
            value >= 1_000_000_000 -> "%.2f GB".format(Locale.ROOT, value / 1_000_000_000.0)
            value >= 1_000_000 -> "%.2f MB".format(Locale.ROOT, value / 1_000_000.0)
            value >= 1_000 -> "%.1f KB".format(Locale.ROOT, value / 1_000.0)
            else -> "$value B"
        }

        fun pcmEncodingLabel(encoding: Int): String = when (encoding) {
            // Mirrors androidx.media3.common.C ENCODING_PCM_* values; kept literal to
            // avoid pulling a Media3 dependency into this pure data model.
            2 -> "16-bit"
            3 -> "8-bit"
            4 -> "32-bit float"
            21 -> "24-bit"
            22 -> "32-bit"
            536870912 -> "16-bit big-endian"
            else -> "encoding=$encoding"
        }

        private fun sampleRateRange(min: Int?, max: Int?): String = when {
            min == null && max == null -> UNKNOWN
            min == max -> "${max ?: UNKNOWN} Hz"
            else -> "${min ?: "?"}–${max ?: "?"} Hz"
        }

        private fun dimensions(w: Int?, h: Int?): String =
            if (w == null || h == null) NOT_OBSERVED else "${w}x$h"

        private fun estSizes(library: LibrarySection): String {
            val min = library.estFileSizeMinBytes
            val avg = library.estFileSizeAvgBytes
            val max = library.estFileSizeMaxBytes
            if (min == null && avg == null && max == null) return UNKNOWN
            return "${min?.let(::bytes) ?: "?"} / ${avg?.let { bytes(it.toLong()) } ?: "?"} / ${max?.let(::bytes) ?: "?"}"
        }
    }
}

@Serializable
data class DeviceSection(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long
)

@Serializable
data class AppSection(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val applicationId: String
)

@Serializable
data class LibrarySection(
    val totalSongs: Int,
    val localSongs: Int,
    val cloudSongs: Int,
    val mimeCounts: Map<String, Int>,
    val maxBitrate: Int?,
    val minSampleRate: Int?,
    val maxSampleRate: Int?,
    val maxObservedChannels: Int?,
    val maxObservedPcmEncoding: Int?,
    val estFileSizeMinBytes: Long?,
    val estFileSizeAvgBytes: Double?,
    val estFileSizeMaxBytes: Long?
)

@Serializable
data class HiResSection(
    val hiResCount: Int,
    val ultraHiResCount: Int,
    val losslessCodecCount: Int,
    val likelyExpensiveToDecodeCount: Int,
    val multichannelPlaybackObservations: Int,
    val largeArtworkObservations: Int
)

@Serializable
data class ArtworkSection(
    val embeddedArtSeen: Boolean,
    val maxEmbeddedBytes: Long?,
    val maxDecodedWidth: Int?,
    val maxDecodedHeight: Int?,
    val cacheHits: Long,
    val cacheMisses: Long,
    val freshExtractions: Long,
    val extractionTiming: ReportTiming?,
    val decodeTiming: ReportTiming?
)

@Serializable
data class PlaybackSection(
    val currentMime: String?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val channelCount: Int?,
    val pcmEncoding: Int?,
    val decoderName: String?,
    val decoderHardware: Boolean?,
    val audioOffloadEnabled: Boolean,
    val offloadFallbackCount: Long,
    val audioOutputMode: String,
    val crossfadeEnabled: Boolean,
    val crossfadeDurationMs: Int,
    val equalizerEnabled: Boolean,
    val replayGainEnabled: Boolean,
    val replayGainAlbumMode: Boolean
)

@Serializable
data class ControllerSection(
    val widgetActive: Boolean,
    val wearActive: Boolean,
    val connectedControllers: List<ConnectedController>
)

@Serializable
data class ConnectedController(
    val packageName: String,
    val isWear: Boolean
)

@Serializable
data class ReportTiming(
    val count: Long,
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val lastMs: Double
) {
    fun format(): String =
        "n=$count min=${ms(minMs)} avg=${ms(avgMs)} max=${ms(maxMs)} last=${ms(lastMs)}"

    private fun ms(value: Double): String = "%.1f".format(java.util.Locale.ROOT, value)
}

@Serializable
data class OffloadEventEntry(
    val elapsedRealtimeMs: Long,
    val reason: String
)

@Serializable
data class AdvancedDiagnosticsSection(
    val enabled: Boolean = false,
    val sessionStartedIso: String? = null,
    val expiresAtIso: String? = null,
    val eventCount: Int = 0,
    val droppedEventCount: Long = 0,
    val events: List<AdvancedDiagnosticEventEntry> = emptyList()
)

@Serializable
data class AdvancedDiagnosticEventEntry(
    val elapsedRealtimeMs: Long,
    val type: String,
    val name: String,
    val details: Map<String, String> = emptyMap()
)
