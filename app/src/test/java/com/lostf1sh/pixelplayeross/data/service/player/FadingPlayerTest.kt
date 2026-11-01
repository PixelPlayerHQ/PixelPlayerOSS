package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The play/pause fades ramp the engine player's own volume, so a fade cut short by another
 * play or pause must not leave that volume at a mid-ramp level: playback would go on silent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FadingPlayerTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class InnerState(var volume: Float, var isPlaying: Boolean)

    private fun innerPlayer(state: InnerState): Player =
        mockk<Player>(relaxed = true).also { player ->
            every { player.volume } answers { state.volume }
            every { player.setVolume(any()) } answers { state.volume = firstArg() }
            every { player.isPlaying } answers { state.isPlaying }
        }

    @Test
    fun `second play while the player is still buffering keeps the full volume`() =
        runTest(dispatcher) {
            val state = InnerState(volume = 1f, isPlaying = false)
            val player = FadingPlayer(innerPlayer(state), TestScope(dispatcher))

            player.play()
            player.play()
            advanceUntilIdle()

            assertEquals(1f, state.volume)
        }

    @Test
    fun `pause while the play fade is still buffering puts the volume back`() =
        runTest(dispatcher) {
            val state = InnerState(volume = 0.7f, isPlaying = false)
            val player = FadingPlayer(innerPlayer(state), TestScope(dispatcher))

            player.play()
            player.pause()
            advanceUntilIdle()
            assertEquals(0.7f, state.volume)

            player.play()
            advanceUntilIdle()
            assertEquals(0.7f, state.volume)
        }

    @Test
    fun `volume set during the play fade is where the fade ends`() = runTest(dispatcher) {
        val state = InnerState(volume = 1f, isPlaying = false)
        val player = FadingPlayer(innerPlayer(state), TestScope(dispatcher))

        player.play()
        advanceTimeBy(100)
        player.volume = 0.4f
        advanceUntilIdle()

        assertEquals(0.4f, state.volume)
    }

    @Test
    fun `play during the pause fade puts the volume back`() = runTest(dispatcher) {
        val state = InnerState(volume = 1f, isPlaying = true)
        val player = FadingPlayer(innerPlayer(state), TestScope(dispatcher))

        player.pause()
        advanceTimeBy(250)
        player.play()
        advanceUntilIdle()

        assertEquals(1f, state.volume)
    }
}
