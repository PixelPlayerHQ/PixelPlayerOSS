package com.lostf1sh.pixelplayeross.data.equalizer

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerDspTest {

    private val rate = 48_000

    @Test
    fun neutralOrFlatSettings_bypassProcessing() {
        val dsp = dsp(AudioEffectsSettings())
        assertThat(dsp.isBypassed).isTrue()

        dsp.update(AudioEffectsSettings(equalizerEnabled = true, loudnessEnabled = true))
        assertThat(dsp.isBypassed).isTrue()
    }

    @Test
    fun bandBoost_liftsItsBandByTheSetAmount_withHeadroomKeepingItNearUnity() {
        val settings = AudioEffectsSettings(equalizerEnabled = true, bandLevelsDb = bands(5 to 12))

        val at1k = steadyGainDb(settings, hz = 1_000.0)
        val at100 = steadyGainDb(settings, hz = 100.0)

        assertThat(at1k - at100).isWithin(0.5).of(12.0)
        assertThat(at1k).isWithin(0.5).of(0.0) // auto headroom paid for the boost
    }

    @Test
    fun bassBoost_liftsLowsOverHighs() {
        val settings = AudioEffectsSettings(bassBoostEnabled = true, bassBoostStrength = 1000)

        val difference = steadyGainDb(settings, hz = 30.0) - steadyGainDb(settings, hz = 5_000.0)

        assertThat(difference).isWithin(1.0).of(EqualizerDsp.MAX_BASS_BOOST_DB)
    }

    @Test
    fun everythingMaxedOnFullScaleInput_neverExceedsFullScale() {
        val dsp = dsp(
            AudioEffectsSettings(
                equalizerEnabled = true,
                bandLevelsDb = List(EqualizerDsp.BAND_COUNT) { 15 },
                bassBoostEnabled = true,
                bassBoostStrength = 1000,
                virtualizerEnabled = true,
                virtualizerStrength = 1000,
                loudnessEnabled = true,
                loudnessGainMb = 1000,
            )
        )
        val frames = rate
        val samples = DoubleArray(frames * 2) { i ->
            val t = (i / 2).toDouble() / rate
            val left = sin(2 * PI * 60 * t)
            if (i % 2 == 0) left else -left // anti-phase: worst case for the widener
        }

        dsp.process(samples, frames)

        assertThat(samples.maxOf { abs(it) }).isAtMost(1.0)
    }

    @Test
    fun disablingEffects_returnsToBypassAfterTheGlide() {
        val dsp = dsp(AudioEffectsSettings(equalizerEnabled = true, bandLevelsDb = bands(2 to -9)), channels = 1)
        dsp.process(sine(hz = 440.0, seconds = 0.2), (rate * 0.2).toInt())
        assertThat(dsp.isBypassed).isFalse()

        dsp.update(AudioEffectsSettings(equalizerEnabled = false, bandLevelsDb = bands(2 to -9)))
        dsp.process(sine(hz = 440.0, seconds = 0.5), rate / 2)

        assertThat(dsp.isBypassed).isTrue()
    }

    @Test
    fun virtualizer_keepsMonoIntactAndWidensTheSide() {
        val dsp = dsp(AudioEffectsSettings(virtualizerEnabled = true, virtualizerStrength = 1000))
        dsp.reset() // skip the glide
        val mono = doubleArrayOf(0.3, 0.3, -0.2, -0.2)
        val side = doubleArrayOf(0.25, -0.25)

        dsp.process(mono, 2)
        dsp.process(side, 1)

        assertThat(mono.toList()).containsExactly(0.3, 0.3, -0.2, -0.2).inOrder()
        assertThat(side[0]).isWithin(1e-9).of(0.5)
        assertThat(side[1]).isWithin(1e-9).of(-0.5)
    }

    private fun dsp(settings: AudioEffectsSettings, channels: Int = 2) = EqualizerDsp().apply {
        configure(rate, channels)
        update(settings)
    }

    private fun bands(vararg levels: Pair<Int, Int>): List<Int> =
        List(EqualizerDsp.BAND_COUNT) { band -> levels.firstOrNull { it.first == band }?.second ?: 0 }

    /** Output/input level of a mono sine once the glide has settled, in dB. */
    private fun steadyGainDb(settings: AudioEffectsSettings, hz: Double): Double {
        val dsp = dsp(settings, channels = 1)
        val input = sine(hz, seconds = 1.0, amplitude = 0.1)
        val output = input.copyOf()
        dsp.process(output, input.size)
        val settled = input.size / 2 until input.size
        return 20 * log10(rms(output, settled) / rms(input, settled))
    }

    private fun sine(hz: Double, seconds: Double, amplitude: Double = 0.5): DoubleArray =
        DoubleArray((rate * seconds).toInt()) { amplitude * sin(2 * PI * hz * it / rate) }

    private fun rms(samples: DoubleArray, range: IntRange): Double =
        sqrt(range.sumOf { samples[it] * samples[it] } / range.count())
}
