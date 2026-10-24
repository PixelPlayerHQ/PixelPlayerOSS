package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.Player
import com.lostf1sh.pixelplayeross.data.preferences.PreviousButtonBehavior
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * System surfaces (notification, headset keys, widget) call seekToPrevious() on the session
 * player, so the "Previous button" preference has to be applied there too.
 */
class PreviousButtonPlayerTest {

    private fun innerPlayer(hasPrevious: Boolean): Player =
        mockk<Player>(relaxed = true).also { player ->
            every { player.hasPreviousMediaItem() } returns hasPrevious
        }

    @Test
    fun `previous track mode steps back to the previous item`() {
        val inner = innerPlayer(hasPrevious = true)
        val player = PreviousButtonPlayer(inner) { PreviousButtonBehavior.PREVIOUS_TRACK }

        player.seekToPrevious()

        verify(exactly = 1) { inner.seekToPreviousMediaItem() }
        verify(exactly = 0) { inner.seekToPrevious() }
    }

    @Test
    fun `previous track mode on the first item keeps the default restart`() {
        val inner = innerPlayer(hasPrevious = false)
        val player = PreviousButtonPlayer(inner) { PreviousButtonBehavior.PREVIOUS_TRACK }

        player.seekToPrevious()

        verify(exactly = 1) { inner.seekToPrevious() }
        verify(exactly = 0) { inner.seekToPreviousMediaItem() }
    }

    @Test
    fun `restart track mode keeps Media3's seekToPrevious`() {
        val inner = innerPlayer(hasPrevious = true)
        val player = PreviousButtonPlayer(inner) { PreviousButtonBehavior.RESTART_TRACK }

        player.seekToPrevious()

        verify(exactly = 1) { inner.seekToPrevious() }
        verify(exactly = 0) { inner.seekToPreviousMediaItem() }
    }

    @Test
    fun `behavior is read on every press`() {
        val inner = innerPlayer(hasPrevious = true)
        var behavior = PreviousButtonBehavior.RESTART_TRACK
        val player = PreviousButtonPlayer(inner) { behavior }

        player.seekToPrevious()
        behavior = PreviousButtonBehavior.PREVIOUS_TRACK
        player.seekToPrevious()

        verify(exactly = 1) { inner.seekToPrevious() }
        verify(exactly = 1) { inner.seekToPreviousMediaItem() }
    }

    @Test
    fun `explicit item navigation passes through untouched`() {
        val inner = innerPlayer(hasPrevious = true)
        val player = PreviousButtonPlayer(inner) { PreviousButtonBehavior.RESTART_TRACK }

        player.seekToPreviousMediaItem()
        player.seekTo(0L)

        verify(exactly = 1) { inner.seekToPreviousMediaItem() }
        verify(exactly = 1) { inner.seekTo(0L) }
        verify(exactly = 0) { inner.seekToPrevious() }
    }
}
