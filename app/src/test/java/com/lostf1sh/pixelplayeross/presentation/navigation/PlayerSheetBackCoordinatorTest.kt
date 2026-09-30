package com.lostf1sh.pixelplayeross.presentation.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetBackCoordinatorTest {
    @Test
    fun `back immediately after player collapse navigates up a folder`() {
        val events = mutableListOf<String>()
        val dispatcher = OnBackPressedDispatcher { events += "leave library" }
        val coordinator = PlayerSheetBackCoordinator()
        val player = callback { events += "collapse player" }
        val folder = callback { events += "parent folder" }
        dispatcher.addCallback(player)
        // The library can enter composition after the player and register last.
        dispatcher.addCallback(folder)
        coordinator.updatePlayerSheetHandlingBack(true)
        coordinator.updateCallback(folder, enabled = true)

        dispatcher.onBackPressed()
        // Apply the collapsed state, then dispatch again without a collector or another frame.
        player.isEnabled = false
        coordinator.updatePlayerSheetHandlingBack(false)
        dispatcher.onBackPressed()

        assertEquals(listOf("collapse player", "parent folder"), events)
    }

    @Test
    fun `player takes priority when expanding over an already registered folder handler`() {
        val events = mutableListOf<String>()
        val dispatcher = OnBackPressedDispatcher { events += "leave library" }
        val coordinator = PlayerSheetBackCoordinator()
        val player = callback { events += "collapse player" }
        val folder = callback { events += "parent folder" }
        player.isEnabled = false
        dispatcher.addCallback(player)
        dispatcher.addCallback(folder)
        coordinator.updateCallback(folder, enabled = true)

        player.isEnabled = true
        coordinator.updatePlayerSheetHandlingBack(true)
        dispatcher.onBackPressed()

        assertEquals(listOf("collapse player"), events)
    }

    @Test
    fun `releasing the sheet respects the latest folder or selection eligibility`() {
        val coordinator = PlayerSheetBackCoordinator()
        val folder = callback {}
        coordinator.updateCallback(folder, enabled = true)
        coordinator.updatePlayerSheetHandlingBack(true)
        coordinator.updateCallback(folder, enabled = false)

        coordinator.updatePlayerSheetHandlingBack(false)

        assertFalse(folder.isEnabled)
        coordinator.updateCallback(folder, enabled = true)
        assertTrue(folder.isEnabled)
    }

    @Test
    fun `back closes queue then player before navigating up a folder`() {
        val events = mutableListOf<String>()
        val dispatcher = OnBackPressedDispatcher { events += "leave library" }
        val coordinator = PlayerSheetBackCoordinator()
        val player = callback { events += "collapse player" }
        val queue = callback { events += "close queue" }
        val folder = callback { events += "parent folder" }
        player.isEnabled = false
        dispatcher.addCallback(player)
        dispatcher.addCallback(queue)
        dispatcher.addCallback(folder)
        coordinator.updatePlayerSheetHandlingBack(true)
        coordinator.updateCallback(folder, enabled = true)

        dispatcher.onBackPressed()
        queue.isEnabled = false
        player.isEnabled = true
        coordinator.updatePlayerSheetHandlingBack(true)
        dispatcher.onBackPressed()
        player.isEnabled = false
        coordinator.updatePlayerSheetHandlingBack(false)
        dispatcher.onBackPressed()

        assertEquals(listOf("close queue", "collapse player", "parent folder"), events)
    }

    @Test
    fun `disposed callbacks are no longer updated by the sheet`() {
        val coordinator = PlayerSheetBackCoordinator()
        val folder = callback {}
        coordinator.updateCallback(folder, enabled = true)
        coordinator.updatePlayerSheetHandlingBack(true)
        coordinator.removeCallback(folder)

        coordinator.updatePlayerSheetHandlingBack(false)

        assertFalse(folder.isEnabled)
    }

    private fun callback(onBack: () -> Unit) = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBack()
    }
}
