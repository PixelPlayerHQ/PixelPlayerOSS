package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.media.WaveformData
import com.lostf1sh.pixelplayeross.data.model.MixPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixProfileTest {

    private val bucketMs = 50

    /** [ms] of audio ramping linearly from [from] to [to] (each 0..1). */
    private fun ramp(ms: Int, from: Float, to: Float): List<Byte> =
        (0 until ms / bucketMs).map { i ->
            val v = from + (to - from) * (i / (ms / bucketMs - 1).coerceAtLeast(1).toFloat())
            (v * 255).toInt().coerceIn(0, 255).toByte()
        }

    private fun level(ms: Int, v: Float) = ramp(ms, v, v)

    private fun wave(vararg parts: List<Byte>, startMs: Long = 0L) =
        WaveformData(bucketMs, parts.flatMap { it }.toByteArray(), startMs)

    // --- Reading a profile off a waveform ---

    @Test
    fun `leading silence is measured and a song that starts at full tilt has no intro`() {
        val head = wave(level(2_000, 0f), level(30_000, 0.8f))
        val tail = wave(level(30_000, 0.8f), startMs = 170_000)
        val profile = MixProfileBuilder.build(200_000, head, tail)!!

        assertEquals(2_000L, profile.leadSilenceMs)
        assertTrue(profile.introMs < 1_500L, "intro ${profile.introMs}")
    }

    @Test
    fun `a quiet start is an intro that lasts until the song gets going`() {
        val head = wave(level(1_000, 0f), level(8_000, 0.1f), ramp(2_000, 0.1f, 0.8f), level(30_000, 0.8f))
        val tail = wave(level(30_000, 0.8f), startMs = 170_000)
        val profile = MixProfileBuilder.build(200_000, head, tail)!!

        assertEquals(1_000L, profile.leadSilenceMs)
        assertTrue(profile.introMs in 8_000L..10_500L, "intro ${profile.introMs}")
    }

    @Test
    fun `a song that ends at full volume has no outro and no trailing silence`() {
        val head = wave(level(30_000, 0.8f))
        val tail = wave(level(30_000, 0.8f), startMs = 170_000)
        val profile = MixProfileBuilder.build(200_000, head, tail)!!

        assertTrue(profile.outroMs < 1_500L, "outro ${profile.outroMs}")
        assertEquals(0L, profile.trailSilenceMs)
        assertEquals(200_000L, profile.musicEndMs)
    }

    @Test
    fun `a fade-out is an outro and the silence after it is trailing silence`() {
        val head = wave(level(30_000, 0.8f))
        val tail = wave(level(20_000, 0.8f), ramp(10_000, 0.8f, 0f), level(3_000, 0f), startMs = 167_000)
        val profile = MixProfileBuilder.build(200_000, head, tail)!!

        assertTrue(profile.trailSilenceMs in 2_800L..3_400L, "trail ${profile.trailSilenceMs}")
        assertTrue(profile.outroMs in 5_000L..7_000L, "outro ${profile.outroMs}")
        assertEquals(200_000L - profile.trailSilenceMs, profile.musicEndMs)
    }

    @Test
    fun `audio too quiet or empty to judge has no profile`() {
        val quiet = wave(level(30_000, 0.01f))
        assertNull(MixProfileBuilder.build(200_000, quiet, quiet))
        val none = WaveformData(bucketMs, ByteArray(0))
        assertNull(MixProfileBuilder.build(200_000, none, none))
    }

    // --- Choosing a transition from two profiles ---

    private fun profile(
        durationMs: Long = 200_000,
        leadSilenceMs: Long = 0,
        trailSilenceMs: Long = 0,
        introMs: Long = 500,
        outroMs: Long = 500,
    ) = MixProfile(durationMs, leadSilenceMs, trailSilenceMs, introMs, outroMs)

    @Test
    fun `an outgoing song that already fades out gets a window over its fade`() {
        val plan = MixPlanner.smart(profile(outroMs = 6_000), profile(), null, null)

        assertEquals(MixPlanner.AutoReason.NATURAL_OUTRO, plan.reason)
        assertEquals(6_000L, plan.mix.windowMs)
        assertEquals(194_000L, plan.mix.outPointMs)
        assertEquals(MixPreset.AUTO, plan.mix.preset)
        // The incoming song builds in while the outgoing one is left to fade by itself.
        assertEquals(MixPresets.lines(MixPreset.RISE).incoming, plan.mix.inVolume)
    }

    @Test
    fun `a long soft intro gets a longer smooth crossfade`() {
        val plan = MixPlanner.smart(profile(outroMs = 500), profile(introMs = 9_000), null, null)

        assertEquals(MixPlanner.AutoReason.SOFT_INTRO, plan.reason)
        assertEquals(9_000L, plan.mix.windowMs)
        assertEquals(MixPresets.lines(MixPreset.AUTO).outgoing, plan.mix.outVolume)
    }

    @Test
    fun `two songs that hit hard at the join get a short fade down and up`() {
        val plan = MixPlanner.smart(profile(outroMs = 500), profile(introMs = 500), null, null)

        assertEquals(MixPlanner.AutoReason.BOTH_PUNCHY, plan.reason)
        assertEquals(4_000L, plan.mix.windowMs)
        assertEquals(MixPresets.lines(MixPreset.FADE).outgoing, plan.mix.outVolume)
    }

    @Test
    fun `a moderate join falls back to the standard 8 seconds`() {
        val plan = MixPlanner.smart(profile(outroMs = 2_500), profile(introMs = 3_000), null, null)

        assertEquals(MixPlanner.AutoReason.DEFAULT, plan.reason)
        assertEquals(8_000L, plan.mix.windowMs)
    }

    @Test
    fun `matching tempos win and the window is a whole number of bars`() {
        // 120 BPM: a bar is 2 s; 16 bars is 32 s, capped at 30 s.
        val plan = MixPlanner.smart(profile(outroMs = 6_000), profile(introMs = 9_000), 120f, 121f)

        assertEquals(MixPlanner.AutoReason.TEMPO_MATCHED, plan.reason)
        assertEquals(30_000L, plan.mix.windowMs)
        assertEquals(16, plan.mix.bars)
    }

    @Test
    fun `silence at the ends is skipped`() {
        val plan = MixPlanner.smart(
            profile(trailSilenceMs = 3_000, outroMs = 500),
            profile(leadSilenceMs = 2_000, introMs = 500),
            null, null,
        )
        // The window ends where the outgoing music ends; the incoming song starts where its sound does.
        assertEquals(200_000L - 3_000L, plan.mix.outPointMs + plan.mix.windowMs)
        assertEquals(2_000L, plan.mix.inStartMs)
    }

    @Test
    fun `a very long leading silence is only partly skipped`() {
        val plan = MixPlanner.smart(profile(), profile(leadSilenceMs = 20_000), null, null)
        assertEquals(8_000L, plan.mix.inStartMs)
    }

    @Test
    fun `different pairs get different transitions`() {
        val plans = listOf(
            MixPlanner.smart(profile(outroMs = 6_000), profile(), null, null),
            MixPlanner.smart(profile(), profile(introMs = 9_000), null, null),
            MixPlanner.smart(profile(), profile(), null, null),
            MixPlanner.smart(profile(outroMs = 2_500), profile(introMs = 3_000), null, null),
        )
        assertTrue(plans.map { it.mix.windowMs }.toSet().size >= 3, plans.map { it.mix.windowMs }.toString())
        assertNotEquals(plans[0].mix.inVolume, plans[1].mix.inVolume)
    }

    @Test
    fun `the window stays inside a short outgoing song`() {
        val plan = MixPlanner.smart(profile(durationMs = 5_000, outroMs = 500), profile(), null, null)
        assertTrue(plan.mix.outPointMs >= 0 && plan.mix.outPointMs + plan.mix.windowMs <= 5_000L)
    }
}
