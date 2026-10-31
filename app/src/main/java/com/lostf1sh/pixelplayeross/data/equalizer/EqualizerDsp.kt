package com.lostf1sh.pixelplayeross.data.equalizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the software effect chain should do. Units match the UI and stored preferences:
 * band levels in dB (-15..15), strengths 0..1000, loudness gain in millibels (0..1000).
 */
data class AudioEffectsSettings(
    val equalizerEnabled: Boolean = false,
    val bandLevelsDb: List<Int> = List(EqualizerDsp.BAND_COUNT) { 0 },
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val loudnessEnabled: Boolean = false,
    val loudnessGainMb: Int = 0,
) {
    /** Any effect switched on; playback then has to stay on the PCM path (no offload). */
    val anyEnabled: Boolean
        get() = equalizerEnabled || bassBoostEnabled || virtualizerEnabled || loudnessEnabled
}

/**
 * Software effect chain run on decoded PCM by [EqualizerAudioSink]:
 * 10 peaking bands → bass low-shelf → mid/side widener → headroom + loudness gain → peak limiter.
 *
 * - Gains glide to new values, so slider drags and toggles don't click.
 * - Boosts are paid for with automatic headroom (pre-gain = peak of the response curve), so the
 *   EQ never clips; the limiter only catches what loudness gain and widening add on top.
 * - Filters at 0 dB are skipped, and a neutral chain reports [isBypassed] so the sink can hand the
 *   decoder's buffer through untouched.
 *
 * Runs in double precision: low bands at high sample rates are too noisy in float biquads.
 * Not thread-safe; owned by one playback thread.
 */
class EqualizerDsp {

    companion object {
        const val BAND_COUNT = 10
        const val MAX_BAND_DB = 15
        const val MAX_BASS_BOOST_DB = 12.0
        private val BAND_CENTER_HZ =
            doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)
        private const val BAND_Q = 1.41 // one octave per band
        private const val BASS_SHELF_HZ = 100.0
        private const val MAX_EXTRA_SIDE_GAIN = 1.0 // full virtualizer doubles the side signal
        private const val LIMITER_CEILING = 1.0 // full scale: only true overs are limited
        private const val LIMITER_RELEASE_SECONDS = 0.08
        private const val GLIDE_SECONDS = 0.025
        private const val BLOCK_FRAMES = 32
        private const val RESPONSE_POINTS = 96
        private const val SNAP = 1e-3

        private const val SHELF = BAND_COUNT
        private const val FILTER_COUNT = BAND_COUNT + 1
        private const val GAIN = FILTER_COUNT // headroom + loudness, dB
        private const val SIDE = FILTER_COUNT + 1 // extra side gain, linear
        private const val PARAM_COUNT = FILTER_COUNT + 2
    }

    private var sampleRate = 0
    private var channelCount = 0
    private val target = DoubleArray(PARAM_COUNT)
    private val current = DoubleArray(PARAM_COUNT)
    private val coefficients = DoubleArray(FILTER_COUNT * 5)
    private val responseCoefficients = DoubleArray(FILTER_COUNT * 5)
    private var state = DoubleArray(0) // [filter][channel][s1, s2]
    private var glideAlpha = 0.0
    private var releaseAlpha = 0.0
    private var linearGain = 1.0
    private var limiterGain = 1.0
    private var appliedSettings: AudioEffectsSettings? = null

    /** True when processing would not change the signal. */
    val isBypassed: Boolean
        get() {
            if (limiterGain != 1.0) return false
            for (p in 0 until PARAM_COUNT) {
                if (current[p] != 0.0 || target[p] != 0.0) return false
            }
            return true
        }

    fun configure(sampleRate: Int, channelCount: Int) {
        if (sampleRate == this.sampleRate && channelCount == this.channelCount) return
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        state = DoubleArray(FILTER_COUNT * channelCount * 2)
        glideAlpha = 1 - exp(-BLOCK_FRAMES / (GLIDE_SECONDS * sampleRate))
        releaseAlpha = 1 - exp(-1 / (LIMITER_RELEASE_SECONDS * sampleRate))
        // Usable bands and headroom depend on the rate.
        appliedSettings?.let(::computeTargets)
        // Jump, don't glide: a band above the new Nyquist limit is unstable at any non-zero gain.
        reset()
    }

    fun update(settings: AudioEffectsSettings) {
        if (settings === appliedSettings) return
        appliedSettings = settings
        if (sampleRate > 0) computeTargets(settings)
    }

    /** Drops filter history (seek/flush) and jumps straight to the target settings. */
    fun reset() {
        state.fill(0.0)
        limiterGain = 1.0
        target.copyInto(current)
        for (f in 0 until FILTER_COUNT) design(f, current[f], coefficients, f * 5)
        linearGain = dbToLinear(current[GAIN])
    }

    /** Processes [frameCount] interleaved frames in place; samples are full scale ±1. */
    fun process(samples: DoubleArray, frameCount: Int) {
        var start = 0
        while (start < frameCount) {
            val frames = min(BLOCK_FRAMES, frameCount - start)
            glide()
            runFilters(samples, start, frames)
            runOutputStage(samples, start, frames)
            start += frames
        }
    }

    private fun computeTargets(settings: AudioEffectsSettings) {
        for (b in 0 until BAND_COUNT) {
            val usable = settings.equalizerEnabled && BAND_CENTER_HZ[b] < 0.45 * sampleRate
            target[b] = if (usable) {
                settings.bandLevelsDb.getOrElse(b) { 0 }.coerceIn(-MAX_BAND_DB, MAX_BAND_DB).toDouble()
            } else {
                0.0
            }
        }
        target[SHELF] = if (settings.bassBoostEnabled) {
            settings.bassBoostStrength.coerceIn(0, 1000) / 1000.0 * MAX_BASS_BOOST_DB
        } else {
            0.0
        }
        val loudnessDb = if (settings.loudnessEnabled) settings.loudnessGainMb.coerceIn(0, 1000) / 100.0 else 0.0
        target[GAIN] = loudnessDb - peakResponseDb()
        target[SIDE] = if (settings.virtualizerEnabled && channelCount == 2) {
            settings.virtualizerStrength.coerceIn(0, 1000) / 1000.0 * MAX_EXTRA_SIDE_GAIN
        } else {
            0.0
        }
    }

    /** Highest boost of the target filter cascade between 20 Hz and 20 kHz, in dB (>= 0). */
    private fun peakResponseDb(): Double {
        if ((0 until FILTER_COUNT).none { target[it] > 0.0 }) return 0.0 // cuts never exceed 0 dB
        for (f in 0 until FILTER_COUNT) design(f, target[f], responseCoefficients, f * 5)
        val maxHz = min(20_000.0, 0.45 * sampleRate)
        var peakDb = 0.0
        for (i in 0 until RESPONSE_POINTS) {
            val hz = 20.0 * (maxHz / 20.0).pow(i / (RESPONSE_POINTS - 1.0))
            val w = 2 * PI * hz / sampleRate
            val cos1 = cos(w)
            val sin1 = sin(w)
            val cos2 = cos(2 * w)
            val sin2 = sin(2 * w)
            var db = 0.0
            for (f in 0 until FILTER_COUNT) {
                if (target[f] == 0.0) continue
                val o = f * 5
                val b0 = responseCoefficients[o]
                val b1 = responseCoefficients[o + 1]
                val b2 = responseCoefficients[o + 2]
                val a1 = responseCoefficients[o + 3]
                val a2 = responseCoefficients[o + 4]
                val numRe = b0 + b1 * cos1 + b2 * cos2
                val numIm = b1 * sin1 + b2 * sin2
                val denRe = 1 + a1 * cos1 + a2 * cos2
                val denIm = a1 * sin1 + a2 * sin2
                db += 10 * log10((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
            }
            peakDb = max(peakDb, db)
        }
        return peakDb
    }

    private fun glide() {
        for (p in 0 until PARAM_COUNT) {
            val t = target[p]
            val c = current[p]
            if (c == t) continue
            val next = if (abs(t - c) < SNAP) t else c + (t - c) * glideAlpha
            current[p] = next
            when {
                p < FILTER_COUNT -> {
                    design(p, next, coefficients, p * 5)
                    // A 0 dB filter is skipped; restart it from silence when it comes back.
                    if (next == 0.0) state.fill(0.0, p * channelCount * 2, (p + 1) * channelCount * 2)
                }
                p == GAIN -> linearGain = dbToLinear(next)
            }
        }
    }

    private fun runFilters(samples: DoubleArray, start: Int, frames: Int) {
        val channels = channelCount
        for (f in 0 until FILTER_COUNT) {
            if (current[f] == 0.0) continue
            val o = f * 5
            val b0 = coefficients[o]
            val b1 = coefficients[o + 1]
            val b2 = coefficients[o + 2]
            val a1 = coefficients[o + 3]
            val a2 = coefficients[o + 4]
            for (ch in 0 until channels) {
                val si = (f * channels + ch) * 2
                var s1 = state[si]
                var s2 = state[si + 1]
                var i = start * channels + ch
                repeat(frames) {
                    // Transposed direct form II.
                    val x = samples[i]
                    val y = b0 * x + s1
                    s1 = b1 * x - a1 * y + s2
                    s2 = b2 * x - a2 * y
                    samples[i] = y
                    i += channels
                }
                state[si] = s1
                state[si + 1] = s2
            }
        }
    }

    private fun runOutputStage(samples: DoubleArray, start: Int, frames: Int) {
        val channels = channelCount
        val gain = linearGain
        val sideGain = 1 + current[SIDE]
        val widen = channels == 2 && sideGain != 1.0
        var i = start * channels
        repeat(frames) {
            if (widen) {
                val mid = (samples[i] + samples[i + 1]) * 0.5
                val side = (samples[i] - samples[i + 1]) * 0.5 * sideGain
                samples[i] = mid + side
                samples[i + 1] = mid - side
            }
            var peak = 0.0
            for (ch in i until i + channels) {
                val v = samples[ch] * gain
                samples[ch] = v
                peak = max(peak, abs(v))
            }
            // Instant attack keeps every frame under the ceiling; release glides back to unity.
            val required = if (peak > LIMITER_CEILING) LIMITER_CEILING / peak else 1.0
            limiterGain = if (required < limiterGain) {
                required
            } else {
                val released = limiterGain + (required - limiterGain) * releaseAlpha
                if (released > 0.9999) 1.0 else released
            }
            if (limiterGain != 1.0) {
                for (ch in i until i + channels) samples[ch] *= limiterGain
            }
            i += channels
        }
    }

    /** RBJ cookbook biquad for filter [f] at [gainDb], normalised by a0: b0, b1, b2, a1, a2. */
    private fun design(f: Int, gainDb: Double, out: DoubleArray, o: Int) {
        if (gainDb == 0.0 || sampleRate == 0) {
            out[o] = 1.0; out[o + 1] = 0.0; out[o + 2] = 0.0; out[o + 3] = 0.0; out[o + 4] = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40)
        if (f == SHELF) {
            val w0 = 2 * PI * BASS_SHELF_HZ / sampleRate
            val cosW = cos(w0)
            val twoSqrtAAlpha = 2 * sqrt(a) * sin(w0) / sqrt(2.0) // shelf slope S = 1
            val a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
            out[o] = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha) / a0
            out[o + 1] = 2 * a * ((a - 1) - (a + 1) * cosW) / a0
            out[o + 2] = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha) / a0
            out[o + 3] = -2 * ((a - 1) + (a + 1) * cosW) / a0
            out[o + 4] = ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha) / a0
        } else {
            val w0 = 2 * PI * BAND_CENTER_HZ[f] / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2 * BAND_Q)
            val a0 = 1 + alpha / a
            out[o] = (1 + alpha * a) / a0
            out[o + 1] = -2 * cosW / a0
            out[o + 2] = (1 - alpha * a) / a0
            out[o + 3] = -2 * cosW / a0
            out[o + 4] = (1 - alpha / a) / a0
        }
    }

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20)
}
