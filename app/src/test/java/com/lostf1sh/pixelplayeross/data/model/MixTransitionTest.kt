package com.lostf1sh.pixelplayeross.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MixTransitionTest {

    @Test
    fun `json round trip keeps every field`() {
        val mix = MixTransition(
            outPointMs = 150_000,
            inStartMs = 12_000,
            windowMs = 30_000,
            bars = 16,
            outVolume = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 1f), VolumeKeyframe(1f, 0f)),
            inVolume = listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(0.3f, 1f), VolumeKeyframe(1f, 1f)),
            preset = MixPreset.CUSTOM,
        )
        assertEquals(mix, MixTransition.fromJson(MixTransition.toJson(mix)))
    }

    @Test
    fun `blank or corrupt json yields null`() {
        assertNull(MixTransition.fromJson(null))
        assertNull(MixTransition.fromJson(""))
        assertNull(MixTransition.fromJson("{not json"))
    }

    @Test
    fun `fields missing from stored json fall back to defaults`() {
        val parsed = MixTransition.fromJson("""{"outPointMs":1000,"windowMs":4000}""")
        assertNotNull(parsed)
        assertEquals(MixTransition.DEFAULT_OUT_VOLUME, parsed!!.outVolume)
        assertEquals(MixTransition.DEFAULT_IN_VOLUME, parsed.inVolume)
        assertEquals(MixPreset.CUSTOM, parsed.preset)
    }

    @Test
    fun `sanitized clamps times sorts and clamps keyframes`() {
        val dirty = MixTransition(
            outPointMs = -5,
            inStartMs = -1,
            windowMs = 10,
            bars = 0,
            outVolume = listOf(VolumeKeyframe(1.5f, 2f), VolumeKeyframe(-1f, -3f)),
            inVolume = listOf(VolumeKeyframe(0.5f, 0.5f)),
        ).sanitized()

        assertEquals(0L, dirty.outPointMs)
        assertEquals(0L, dirty.inStartMs)
        assertEquals(MixTransition.MIN_WINDOW_MS, dirty.windowMs)
        assertNull(dirty.bars)
        // Sorted, clamped, then the ends pinned to the window edges: out 1 -> 0.
        assertEquals(listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(1f, 0f)), dirty.outVolume)
        // A line with fewer than two points cannot be played; it falls back to the default.
        assertEquals(MixTransition.DEFAULT_IN_VOLUME, dirty.inVolume)
    }

    @Test
    fun `non finite keyframes are dropped`() {
        val mix = MixTransition(
            outPointMs = 0,
            windowMs = 1000,
            outVolume = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(Float.NaN, 0.5f), VolumeKeyframe(1f, 0f)),
        ).sanitized()
        assertEquals(listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(1f, 0f)), mix.outVolume)
    }

    @Test
    fun `end points are pinned so volume cannot step when the window opens or closes`() {
        val mix = MixTransition(
            outPointMs = 0,
            windowMs = 4000,
            // Outgoing starts quiet and ends audible; incoming starts audible and ends quiet.
            outVolume = listOf(VolumeKeyframe(0f, 0.4f), VolumeKeyframe(0.5f, 0.6f), VolumeKeyframe(1f, 0.3f)),
            inVolume = listOf(VolumeKeyframe(0.1f, 0.5f), VolumeKeyframe(0.5f, 0.2f), VolumeKeyframe(0.9f, 0.78f)),
        ).sanitized()

        assertEquals(listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 0.6f), VolumeKeyframe(1f, 0f)), mix.outVolume)
        assertEquals(listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(0.5f, 0.2f), VolumeKeyframe(1f, 1f)), mix.inVolume)
    }

    @Test
    fun `stored json with an unpinned end is pinned on load`() {
        val json = """{"outPointMs":1000,"windowMs":8000,"inVolume":[{"t":0,"v":0},{"t":1,"v":0.78}]}"""
        assertEquals(1f, MixTransition.fromJson(json)!!.inVolume.last().v, 1e-6f)
    }
}
