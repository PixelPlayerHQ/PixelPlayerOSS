package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.MixTransition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixPlaybackStateTest {

    private fun mix(songId: String, outPointMs: Long = 100_000) = ActiveMix(
        songId = songId,
        outPointMs = outPointMs,
        windowMs = 8_000,
        outVolume = MixTransition.DEFAULT_OUT_VOLUME,
        inVolume = MixTransition.DEFAULT_IN_VOLUME,
    )

    @Test
    fun `holds one mix per song`() {
        val state = MixPlaybackState()
        state.set(mix("a"))
        state.set(mix("b"))
        assertEquals(listOf("a", "b"), state.mixes.value.map { it.songId })
    }

    @Test
    fun `setting a song again replaces its mix`() {
        val state = MixPlaybackState()
        state.set(mix("a", outPointMs = 100_000))
        state.set(mix("a", outPointMs = 120_000))
        assertEquals(listOf(120_000L), state.mixes.value.map { it.outPointMs })
    }

    @Test
    fun `retain only keeps the song now playing and drops the finished one`() {
        val state = MixPlaybackState()
        state.set(mix("outgoing"))
        state.set(mix("incoming"))
        state.retainOnly("incoming")
        assertEquals(listOf("incoming"), state.mixes.value.map { it.songId })
    }

    @Test
    fun `retain only with no entry for the song empties the state`() {
        val state = MixPlaybackState()
        state.set(mix("a"))
        state.retainOnly("other")
        assertTrue(state.mixes.value.isEmpty())
    }

    @Test
    fun `remove and clear`() {
        val state = MixPlaybackState()
        state.set(mix("a"))
        state.set(mix("b"))
        state.remove("a")
        assertEquals(listOf("b"), state.mixes.value.map { it.songId })
        state.clear()
        assertTrue(state.mixes.value.isEmpty())
    }
}
