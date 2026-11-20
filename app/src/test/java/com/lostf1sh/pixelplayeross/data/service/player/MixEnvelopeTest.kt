package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixEnvelopeTest {

    private val ramp = listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(1f, 1f))

    @Test
    fun `interpolates linearly between keyframes`() {
        assertEquals(0f, MixEnvelope.volumeAt(ramp, 0f), 1e-6f)
        assertEquals(0.25f, MixEnvelope.volumeAt(ramp, 0.25f), 1e-6f)
        assertEquals(1f, MixEnvelope.volumeAt(ramp, 1f), 1e-6f)
    }

    @Test
    fun `progress outside zero to one is clamped`() {
        assertEquals(0f, MixEnvelope.volumeAt(ramp, -3f), 1e-6f)
        assertEquals(1f, MixEnvelope.volumeAt(ramp, 7f), 1e-6f)
    }

    @Test
    fun `holds the first and last value outside the keyframe range`() {
        val line = listOf(VolumeKeyframe(0.2f, 0.4f), VolumeKeyframe(0.8f, 0.9f))
        assertEquals(0.4f, MixEnvelope.volumeAt(line, 0.1f), 1e-6f)
        assertEquals(0.9f, MixEnvelope.volumeAt(line, 0.95f), 1e-6f)
    }

    @Test
    fun `follows a multi point line`() {
        val line = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 1f), VolumeKeyframe(1f, 0f))
        assertEquals(1f, MixEnvelope.volumeAt(line, 0.25f), 1e-6f)
        assertEquals(0.5f, MixEnvelope.volumeAt(line, 0.75f), 1e-6f)
    }

    @Test
    fun `equal times make a hard step`() {
        val step = listOf(
            VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 1f),
            VolumeKeyframe(0.5f, 0f), VolumeKeyframe(1f, 0f),
        )
        assertEquals(1f, MixEnvelope.volumeAt(step, 0.49f), 1e-6f)
        assertEquals(0f, MixEnvelope.volumeAt(step, 0.5f), 1e-6f)
        assertEquals(0f, MixEnvelope.volumeAt(step, 0.9f), 1e-6f)
    }

    @Test
    fun `an empty line is full volume`() {
        assertEquals(1f, MixEnvelope.volumeAt(emptyList(), 0.5f), 1e-6f)
    }
}
