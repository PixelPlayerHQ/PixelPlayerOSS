package com.lostf1sh.pixelplayeross.data.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * A song's loudness envelope: one peak per [bucketMs] of audio, each 0..255
 * (255 = full-scale). Small enough to cache per song (about 20 bytes per second of audio).
 *
 * A waveform of only part of a song starts at [startMs] into it; [encode] keeps only whole-song
 * waveforms' data, so a partial one is not meant for the disk cache.
 */
class WaveformData(
    val bucketMs: Int,
    val peaks: ByteArray,
    val startMs: Long = 0L,
    /** True when only part of the song was decoded: times outside it are unknown, not silent. */
    val isPartial: Boolean = false,
) {

    /** Length of the audio covered, from [startMs]. */
    val durationMs: Long get() = peaks.size.toLong() * bucketMs

    /** Peak of bucket [index] (counted from [startMs]) as 0..1; 0 outside the waveform. */
    fun peakAt(index: Int): Float =
        if (index in peaks.indices) (peaks[index].toInt() and 0xFF) / 255f else 0f

    /** Whether [positionMs] falls inside the part of the song this waveform was decoded from. */
    fun covers(positionMs: Long): Boolean = positionMs >= startMs && positionMs < startMs + durationMs

    /** Peak at a time in the song, 0..1; 0 outside the part covered. */
    fun peakAtMs(positionMs: Long): Float =
        if (positionMs < startMs) 0f else peakAt(((positionMs - startMs) / bucketMs).toInt())

    override fun equals(other: Any?): Boolean =
        other is WaveformData && bucketMs == other.bucketMs && startMs == other.startMs &&
            isPartial == other.isPartial && peaks.contentEquals(other.peaks)

    override fun hashCode(): Int =
        31 * (31 * (31 * bucketMs + startMs.hashCode()) + isPartial.hashCode()) + peaks.contentHashCode()

    /** Compact binary form for the disk cache. */
    fun encode(): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE + peaks.size)
            .putInt(MAGIC)
            .putInt(bucketMs)
            .putInt(peaks.size)
            .put(peaks)
            .array()

    companion object {
        private const val MAGIC = 0x57465631 // "WFV1"
        private const val HEADER_SIZE = 12

        /** Parses [encode]'s output; null for truncated, foreign or corrupt data. */
        fun decode(bytes: ByteArray): WaveformData? {
            if (bytes.size < HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            if (buf.getInt() != MAGIC) return null
            val bucketMs = buf.getInt()
            val count = buf.getInt()
            if (bucketMs <= 0 || count < 0 || bytes.size - HEADER_SIZE != count) return null
            return WaveformData(bucketMs, bytes.copyOfRange(HEADER_SIZE, bytes.size))
        }
    }
}

/**
 * Turns decoded 16-bit PCM into peaks: the loudest sample of any channel in each [bucketMs]
 * of audio. Feed interleaved samples through [add] in any chunk size, then call [finish].
 */
class PeakAccumulator(
    sampleRate: Int,
    private val channels: Int,
    private val bucketMs: Int = WaveformRepository.BUCKET_MS,
) {
    private val framesPerBucket = (sampleRate.toLong() * bucketMs / 1000).toInt().coerceAtLeast(1)
    private val peaks = ByteArrayOutputStream()
    private var bucketMax = 0
    private var framesInBucket = 0

    init {
        require(channels > 0) { "channels must be positive" }
    }

    fun add(samples: ShortArray, length: Int = samples.size) {
        var i = 0
        while (i + channels <= length) {
            var frameMax = 0
            for (c in 0 until channels) {
                val a = abs(samples[i + c].toInt())
                if (a > frameMax) frameMax = a
            }
            if (frameMax > bucketMax) bucketMax = frameMax
            i += channels
            if (++framesInBucket == framesPerBucket) flushBucket()
        }
    }

    /** Ends the song; a final partial bucket still counts. [startMs] is where the first sample sits in the song. */
    fun finish(startMs: Long = 0L, isPartial: Boolean = false): WaveformData {
        if (framesInBucket > 0) flushBucket()
        return WaveformData(bucketMs, peaks.toByteArray(), startMs, isPartial)
    }

    private fun flushBucket() {
        peaks.write(((bucketMax * 255L) / 32768L).toInt().coerceIn(0, 255))
        bucketMax = 0
        framesInBucket = 0
    }
}
