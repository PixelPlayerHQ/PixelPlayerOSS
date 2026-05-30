package com.lostf1sh.pixelplayeross.data.service.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AudioFocusResumePolicyTest {

    @Test
    fun transientFocusLoss_doesNotResumeWhenPlaybackWasAlreadyPaused() {
        val shouldResume = shouldResumeAfterTransientAudioFocusLoss(
            masterPlayWhenReady = false,
            masterIsPlaying = false,
            transitionRunning = false,
            auxiliaryPlayWhenReady = false,
            auxiliaryIsPlaying = false
        )

        assertThat(shouldResume).isFalse()
    }

    @Test
    fun transientFocusLoss_resumesWhenMasterWasPlaying() {
        val shouldResume = shouldResumeAfterTransientAudioFocusLoss(
            masterPlayWhenReady = true,
            masterIsPlaying = false,
            transitionRunning = false,
            auxiliaryPlayWhenReady = false,
            auxiliaryIsPlaying = false
        )

        assertThat(shouldResume).isTrue()
    }

    @Test
    fun transientFocusLoss_resumesWhenAuxiliaryTransitionWasPlaying() {
        val shouldResume = shouldResumeAfterTransientAudioFocusLoss(
            masterPlayWhenReady = false,
            masterIsPlaying = false,
            transitionRunning = true,
            auxiliaryPlayWhenReady = false,
            auxiliaryIsPlaying = true
        )

        assertThat(shouldResume).isTrue()
    }

    @Test
    fun transientFocusLoss_ignoresPausedAuxiliaryOutsideTransition() {
        val shouldResume = shouldResumeAfterTransientAudioFocusLoss(
            masterPlayWhenReady = false,
            masterIsPlaying = false,
            transitionRunning = false,
            auxiliaryPlayWhenReady = true,
            auxiliaryIsPlaying = true
        )

        assertThat(shouldResume).isFalse()
    }
}
