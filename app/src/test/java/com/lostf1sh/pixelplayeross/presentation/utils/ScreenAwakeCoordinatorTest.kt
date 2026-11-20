package com.lostf1sh.pixelplayeross.presentation.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenAwakeCoordinatorTest {

    @BeforeEach
    fun setUp() {
        ScreenAwakeCoordinator.reset()
    }

    @Test
    fun `single requester keeps screen awake and releases cleanly`() {
        ScreenAwakeCoordinator.request("LyricsSheet", true)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isTrue()

        ScreenAwakeCoordinator.request("LyricsSheet", false)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isFalse()
    }

    @Test
    fun `multiple requesters maintain screen awake until all release`() {
        ScreenAwakeCoordinator.request("EmbeddedLyricsPane", true)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isTrue()

        ScreenAwakeCoordinator.request("LyricsSheet", true)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isTrue()

        // LyricsSheet releases, but EmbeddedLyricsPane is still active
        ScreenAwakeCoordinator.request("LyricsSheet", false)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isTrue()

        // EmbeddedLyricsPane releases
        ScreenAwakeCoordinator.request("EmbeddedLyricsPane", false)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isFalse()
    }

    @Test
    fun `reset clears all active requesters`() {
        ScreenAwakeCoordinator.request("EmbeddedLyricsPane", true)
        ScreenAwakeCoordinator.request("LyricsSheet", true)
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isTrue()

        ScreenAwakeCoordinator.reset()
        assertThat(ScreenAwakeCoordinator.isScreenAwakeRequested()).isFalse()
    }
}
