package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.media.WaveformData
import kotlin.math.min

/**
 * What a song's start and end sound like, as far as choosing a transition cares. All times are in
 * milliseconds; "full loudness" means the loudest passages measured at the song's start and end.
 */
data class MixProfile(
    val durationMs: Long,
    /** Silence before the music starts. */
    val leadSilenceMs: Long,
    /** Silence after the music ends. */
    val trailSilenceMs: Long,
    /** From the first sound until the song reaches full loudness (0 = it starts at full tilt). */
    val introMs: Long,
    /** Length of the natural fade-out at the end (0 = it stops abruptly at full loudness). */
    val outroMs: Long,
) {
    /** Where the music ends, i.e. the song's length without its trailing silence. */
    val musicEndMs: Long get() = (durationMs - trailSilenceMs).coerceAtLeast(0L)
}

/** Reads a [MixProfile] off a song's loudness envelope. Pure; the decoding happens elsewhere. */
object MixProfileBuilder {

    /** A peak below this (0..1) is silence. */
    const val SILENCE = 0.03f

    /** Smoothed loudness at or above this fraction of the reference counts as full loudness. */
    const val LOUD_FRACTION = 0.6f

    /** Too quiet overall to judge (a silent or near-silent file). */
    private const val MIN_REFERENCE = 0.05f

    private const val SMOOTH_WINDOW_MS = 1_000
    private const val REFERENCE_PERCENTILE = 0.9f

    /**
     * [head] is the start of the song (from 0), [tail] its end. Null when the audio is too quiet
     * to judge or has no sound in the part examined.
     */
    fun build(durationMs: Long, head: WaveformData, tail: WaveformData): MixProfile? {
        val headPeaks = head.asFloats()
        val tailPeaks = tail.asFloats()
        if (headPeaks.isEmpty() || tailPeaks.isEmpty()) return null

        val reference = percentile(headPeaks + tailPeaks, REFERENCE_PERCENTILE)
        if (reference < MIN_REFERENCE) return null
        val loud = reference * LOUD_FRACTION

        // Start: where sound begins, and how long until it gets going.
        val firstSound = headPeaks.indexOfFirst { it >= SILENCE }
        if (firstSound < 0) return null
        val headSmooth = smooth(headPeaks, head.bucketMs)
        val reachesLoud = (firstSound until headSmooth.size).firstOrNull { headSmooth[it] >= loud }
        val introMs = ((reachesLoud ?: headSmooth.size) - firstSound).toLong() * head.bucketMs

        // End: where sound stops, and how long the fade-out before it is.
        val lastSound = tailPeaks.indexOfLast { it >= SILENCE }
        if (lastSound < 0) return null
        val musicEnd = min(durationMs, tail.startMs + (lastSound + 1).toLong() * tail.bucketMs)
        val tailSmooth = smooth(tailPeaks, tail.bucketMs)
        val lastLoud = (0..lastSound).lastOrNull { tailSmooth[it] >= loud }
        val outroMs = if (lastLoud != null) {
            (musicEnd - (tail.startMs + lastLoud.toLong() * tail.bucketMs)).coerceAtLeast(0L)
        } else {
            tail.durationMs // never loud in the part examined: all of it is a fade
        }

        return MixProfile(
            durationMs = durationMs,
            leadSilenceMs = head.startMs + firstSound.toLong() * head.bucketMs,
            trailSilenceMs = (durationMs - musicEnd).coerceAtLeast(0L),
            introMs = introMs,
            outroMs = outroMs,
        )
    }

    private fun WaveformData.asFloats(): FloatArray = FloatArray(peaks.size) { peakAt(it) }

    private fun percentile(values: FloatArray, fraction: Float): Float {
        val sorted = values.sortedArray()
        return sorted[((sorted.size - 1) * fraction).toInt()]
    }

    /** Centered moving average over about [SMOOTH_WINDOW_MS], so a single beat doesn't read as silence. */
    private fun smooth(values: FloatArray, bucketMs: Int): FloatArray {
        val half = (SMOOTH_WINDOW_MS / bucketMs / 2).coerceAtLeast(1)
        val prefix = FloatArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return FloatArray(values.size) { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half + 1).coerceAtMost(values.size)
            (prefix[to] - prefix[from]) / (to - from)
        }
    }
}
