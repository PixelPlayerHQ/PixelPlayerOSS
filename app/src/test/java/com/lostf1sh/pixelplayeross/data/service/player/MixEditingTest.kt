package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixEditingTest {

    private fun k(t: Float, v: Float) = VolumeKeyframe(t, v)
    private val line = listOf(k(0f, 1f), k(0.5f, 0.5f), k(1f, 0f))

    @Test
    fun `edge keyframes are fixed`() {
        assertEquals(line, MixEditing.moveKeyframe(line, 0, t = 0.4f, v = 0.2f))
        assertEquals(line, MixEditing.moveKeyframe(line, 2, t = 0.1f, v = 0.7f))
    }

    @Test
    fun `an interior keyframe cannot cross its neighbours`() {
        assertEquals(1f, MixEditing.moveKeyframe(line, 1, t = 5f, v = 0.5f)[1].t, 1e-6f)
        assertEquals(0f, MixEditing.moveKeyframe(line, 1, t = -5f, v = 0.5f)[1].t, 1e-6f)
        assertEquals(0.7f, MixEditing.moveKeyframe(line, 1, t = 0.7f, v = 0.5f)[1].t, 1e-6f)
    }

    @Test
    fun `volume is clamped to zero and one`() {
        assertEquals(0f, MixEditing.moveKeyframe(line, 1, 0.5f, -1f)[1].v, 1e-6f)
        assertEquals(1f, MixEditing.moveKeyframe(line, 1, 0.5f, 9f)[1].v, 1e-6f)
    }

    @Test
    fun `adding keeps the line sorted and ignores a spot that is taken`() {
        val added = MixEditing.addKeyframe(line, 0.25f, 0.9f)
        assertEquals(4, added.size)
        assertEquals(added, added.sortedBy { it.t })
        assertEquals(line, MixEditing.addKeyframe(line, 0.51f, 0.1f))
    }

    @Test
    fun `only interior keyframes can be removed`() {
        assertEquals(line, MixEditing.removeKeyframe(line, 0))
        assertEquals(line, MixEditing.removeKeyframe(line, 2))
        assertEquals(listOf(k(0f, 1f), k(1f, 0f)), MixEditing.removeKeyframe(line, 1))
    }

    @Test
    fun `hit test picks the nearest keyframe in range`() {
        assertEquals(1, MixEditing.hitKeyframe(line, 0.52f, 0.48f, maxDt = 0.1f, maxDv = 0.1f))
        assertEquals(-1, MixEditing.hitKeyframe(line, 0.3f, 0.9f, maxDt = 0.1f, maxDv = 0.1f))
        // The fixed end keyframes are never hit: touching them slides the song instead.
        assertEquals(-1, MixEditing.hitKeyframe(line, 0f, 1f, maxDt = 0.1f, maxDv = 0.1f))
        assertEquals(-1, MixEditing.hitKeyframe(line, 1f, 0f, maxDt = 0.1f, maxDv = 0.1f))
    }

    @Test
    fun `shifting the window stays inside the outgoing song`() {
        val mix = MixTransition(outPointMs = 100_000, windowMs = 20_000)
        assertEquals(0L, MixEditing.shiftOutPoint(mix, -500_000, 200_000).outPointMs)
        val late = MixEditing.shiftOutPoint(mix, 500_000, 200_000)
        assertTrue(late.outPointMs + late.windowMs <= 200_000 - MixPlanner.END_GUARD_MS)
        assertEquals(110_000L, MixEditing.shiftOutPoint(mix, 10_000, 200_000).outPointMs)
    }

    @Test
    fun `shifting the incoming song is bounded`() {
        val mix = MixTransition(outPointMs = 0, inStartMs = 5_000, windowMs = 20_000)
        assertEquals(0L, MixEditing.shiftInStart(mix, -99_000, 180_000).inStartMs)
        assertEquals(180_000L - 20_000L - MixPlanner.END_GUARD_MS, MixEditing.shiftInStart(mix, 999_000, 180_000).inStartMs)
    }

    @Test
    fun `a longer window starts earlier and keeps its end`() {
        val mix = MixTransition(outPointMs = 170_000, windowMs = 10_000)
        val longer = MixEditing.withWindow(mix, MixPlanner.WindowOption(20_000, 8), 200_000)
        assertEquals(20_000L, longer.windowMs)
        assertEquals(8, longer.bars)
        assertEquals(160_000L, longer.outPointMs)
    }

    @Test
    fun `window options follow the tempo and the song length`() {
        // 120 BPM: a bar is 2 s.
        assertEquals(listOf(8_000L, 16_000L, 32_000L, 64_000L), MixPlanner.windowOptions(120f, 300_000).map { it.windowMs })
        assertEquals(listOf(4, 8, 16, 32), MixPlanner.windowOptions(120f, 300_000).map { it.bars })
        // Without a tempo: seconds, and options longer than the song drop out.
        val noBpm = MixPlanner.windowOptions(null, 10_000)
        assertEquals(listOf(4_000L, 8_000L), noBpm.map { it.windowMs })
        assertTrue(noBpm.all { it.bars == null })
    }

    @Test
    fun `custom marks a mix as hand edited`() {
        val mix = MixTransition(outPointMs = 0, windowMs = 1000, preset = MixPreset.FADE)
        assertEquals(MixPreset.CUSTOM, MixEditing.custom(mix).preset)
    }
}
