package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixPlannerTest {

    private val songMs = 200_000L

    @Test
    fun `every preset gives playable lines that start and end where a crossfade should`() {
        MixPreset.values().forEach { preset ->
            val l = MixPresets.lines(preset)
            assertTrue(l.outgoing.size >= 2 && l.incoming.size >= 2, "$preset needs two points per line")
            assertEquals(l.outgoing, l.outgoing.sortedBy { it.t }, "$preset outgoing sorted")
            assertEquals(l.incoming, l.incoming.sortedBy { it.t }, "$preset incoming sorted")
            assertEquals(1f, MixEnvelope.volumeAt(l.outgoing, 0f), 1e-6f, "$preset outgoing starts loud")
            assertEquals(0f, MixEnvelope.volumeAt(l.outgoing, 1f), 1e-6f, "$preset outgoing ends silent")
            assertEquals(0f, MixEnvelope.volumeAt(l.incoming, 0f), 1e-6f, "$preset incoming starts silent")
            assertEquals(1f, MixEnvelope.volumeAt(l.incoming, 1f), 1e-6f, "$preset incoming ends loud")
        }
    }

    @Test
    fun `fade never has both songs loud at once`() {
        val l = MixPresets.lines(MixPreset.FADE)
        for (i in 0..20) {
            val p = i / 20f
            val sum = MixEnvelope.volumeAt(l.outgoing, p) + MixEnvelope.volumeAt(l.incoming, p)
            assertTrue(sum <= 1.0001f, "sum $sum at $p")
        }
    }

    @Test
    fun `apply sets lines and preset`() {
        val base = MixTransition(outPointMs = 1, windowMs = 4000, preset = MixPreset.CUSTOM)
        val applied = MixPresets.apply(base, MixPreset.RISE)
        assertEquals(MixPreset.RISE, applied.preset)
        assertEquals(MixPresets.lines(MixPreset.RISE).incoming, applied.inVolume)
        assertEquals(4000L, applied.windowMs)
    }

    @Test
    fun `auto with matching tempos spans 16 bars at the end of the song`() {
        // 120 BPM: a bar is 2 s, 16 bars = 32 s, capped at 30 s.
        val mix = MixPlanner.auto(songMs, 120f, 121f)
        assertEquals(30_000L, mix.windowMs)
        assertEquals(songMs - 30_000L, mix.outPointMs)
        assertEquals(0L, mix.inStartMs)
        assertEquals(MixPreset.AUTO, mix.preset)
        assertEquals(16, mix.bars)
    }

    @Test
    fun `auto window gets shorter as the tempos drift apart`() {
        val aligned = MixPlanner.auto(songMs, 140f, 140f).windowMs
        val compatible = MixPlanner.auto(songMs, 140f, 150f).windowMs
        val apart = MixPlanner.auto(songMs, 140f, 100f).windowMs
        assertTrue(aligned > compatible && compatible > apart, "$aligned > $compatible > $apart")
        assertEquals(4, MixPlanner.auto(songMs, 140f, 100f).bars)
    }

    @Test
    fun `auto without a tempo uses a fixed window and no bars`() {
        val mix = MixPlanner.auto(songMs, null, 128f)
        assertEquals(8_000L, mix.windowMs)
        assertNull(mix.bars)
    }

    @Test
    fun `auto in a very short song stays inside it`() {
        val mix = MixPlanner.auto(3_000L, 120f, 120f)
        assertNotNull(MixPlanner.fit(mix, 3_000L))
        assertTrue(mix.outPointMs >= 0 && mix.outPointMs + mix.windowMs <= 3_000L)
    }

    @Test
    fun `fit pulls a window that runs past the end back inside the song`() {
        val mix = MixTransition(outPointMs = 195_000, windowMs = 20_000)
        val fitted = MixPlanner.fit(mix, songMs)!!
        assertEquals(20_000L, fitted.windowMs)
        assertEquals(songMs - MixPlanner.END_GUARD_MS - 20_000L, fitted.outPointMs)
    }

    @Test
    fun `fit shrinks a window longer than the song`() {
        val fitted = MixPlanner.fit(MixTransition(outPointMs = 0, windowMs = 99_000), 10_000L)!!
        assertEquals(10_000L - MixPlanner.END_GUARD_MS, fitted.windowMs)
        assertEquals(0L, fitted.outPointMs)
    }

    @Test
    fun `fit leaves a valid transition unchanged`() {
        val mix = MixTransition(outPointMs = 100_000, inStartMs = 5_000, windowMs = 16_000)
        assertEquals(mix, MixPlanner.fit(mix, songMs))
    }

    @Test
    fun `fit returns null when the song is too short for any mix`() {
        assertNull(MixPlanner.fit(MixTransition(outPointMs = 0, windowMs = 4000), 400L))
    }

    @Test
    fun `fit also keeps the incoming song long enough for the whole window`() {
        // Incoming song is 60 s; the window starts it 55 s in and runs 20 s: pulled back so it fits.
        val mix = MixTransition(outPointMs = 100_000, inStartMs = 55_000, windowMs = 20_000)
        val fitted = MixPlanner.fit(mix, songMs, incomingDurationMs = 60_000)!!
        assertEquals(20_000L, fitted.windowMs)
        assertEquals(60_000L - MixPlanner.END_GUARD_MS - 20_000L, fitted.inStartMs)
    }

    @Test
    fun `fit shortens a window longer than the incoming song`() {
        val fitted = MixPlanner.fit(
            MixTransition(outPointMs = 100_000, inStartMs = 3_000, windowMs = 30_000),
            songMs,
            incomingDurationMs = 10_000,
        )!!
        assertEquals(10_000L - MixPlanner.END_GUARD_MS, fitted.windowMs)
        assertEquals(0L, fitted.inStartMs)
    }

    @Test
    fun `fit returns null when the incoming song is too short for any mix`() {
        assertNull(MixPlanner.fit(MixTransition(outPointMs = 0, windowMs = 4000), songMs, incomingDurationMs = 300L))
    }
}
