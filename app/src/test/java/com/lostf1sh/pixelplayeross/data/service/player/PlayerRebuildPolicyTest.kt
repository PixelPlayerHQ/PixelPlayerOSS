package com.lostf1sh.pixelplayeross.data.service.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PlayerRebuildPolicyTest {

    @Test
    fun activeCrossfade_rebuildsFromPresentedAuxiliaryPlayer() {
        val useAuxiliary = shouldUsePresentedAuxiliaryForPlayerRebuild(
            transitionRunning = true,
            auxiliaryPlayerPresented = true,
            auxiliaryMediaItemCount = 1
        )

        assertThat(useAuxiliary).isTrue()
    }

    @Test
    fun transitionBeforeAuxiliaryIsPresented_keepsMasterPlayer() {
        val useAuxiliary = shouldUsePresentedAuxiliaryForPlayerRebuild(
            transitionRunning = true,
            auxiliaryPlayerPresented = false,
            auxiliaryMediaItemCount = 1
        )

        assertThat(useAuxiliary).isFalse()
    }

    @Test
    fun missingAuxiliaryTimeline_keepsMasterPlayer() {
        val useAuxiliary = shouldUsePresentedAuxiliaryForPlayerRebuild(
            transitionRunning = true,
            auxiliaryPlayerPresented = true,
            auxiliaryMediaItemCount = 0
        )

        assertThat(useAuxiliary).isFalse()
    }

    @Test
    fun auxiliaryRebuild_usesPreparedQueueWindowMapping() {
        val window = selectPlayerRebuildQueueWindow(
            useAuxiliaryPlayer = true,
            activeWindowStartIndex = 0,
            activePlayerUsesWindowedQueue = false,
            preparedWindowStartIndex = 300,
            preparedPlayerUsesWindowedQueue = true
        )

        assertThat(window).isEqualTo(
            PlayerRebuildQueueWindow(startIndex = 300, usesWindowedQueue = true)
        )
    }

    @Test
    fun masterRebuild_keepsActiveQueueWindowMapping() {
        val window = selectPlayerRebuildQueueWindow(
            useAuxiliaryPlayer = false,
            activeWindowStartIndex = 100,
            activePlayerUsesWindowedQueue = true,
            preparedWindowStartIndex = 300,
            preparedPlayerUsesWindowedQueue = true
        )

        assertThat(window).isEqualTo(
            PlayerRebuildQueueWindow(startIndex = 100, usesWindowedQueue = true)
        )
    }
}
