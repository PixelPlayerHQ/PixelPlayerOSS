package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.AudioOutputMode
import org.junit.jupiter.api.Test

class AudioOffloadPolicyTest {

    @Test
    fun outputModePolicy_enablesOffloadOnlyForSystemDefault() {
        assertThat(
            shouldEnableAudioOffloadForMode(
                audioOffloadAvailableForSession = true,
                audioOutputMode = AudioOutputMode.SYSTEM_DEFAULT
            )
        ).isTrue()
        assertThat(
            shouldEnableAudioOffloadForMode(
                audioOffloadAvailableForSession = true,
                audioOutputMode = AudioOutputMode.DIRECT
            )
        ).isFalse()
        assertThat(
            shouldEnableAudioOffloadForMode(
                audioOffloadAvailableForSession = true,
                audioOutputMode = AudioOutputMode.PCM_FLOAT
            )
        ).isFalse()
    }

    @Test
    fun outputModePolicy_keepsOffloadDisabledWhenUnavailableForSession() {
        assertThat(
            shouldEnableAudioOffloadForMode(
                audioOffloadAvailableForSession = false,
                audioOutputMode = AudioOutputMode.SYSTEM_DEFAULT
            )
        ).isFalse()
    }

    @Test
    fun defaultPolicy_disablesOffloadForReportedLavaMtkDeviceOnAndroid15() {
        val disabled = shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = "LAVA",
            brand = "LAVA",
            model = "LXX521",
            hardware = "k69v1_64",
            sdkInt = 35
        )

        assertThat(disabled).isTrue()
    }

    @Test
    fun defaultPolicy_keepsOffloadForPixelOnAndroid15() {
        val disabled = shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            hardware = "tokay",
            sdkInt = 35
        )

        assertThat(disabled).isFalse()
    }

    @Test
    fun defaultPolicy_disablesOffloadForPixelOnSdk37() {
        val disabled = shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            hardware = "tokay",
            sdkInt = 37
        )

        assertThat(disabled).isTrue()
    }

    @Test
    fun defaultPolicy_keepsOffloadForUnrelatedLavaDeviceWithoutMtkSignal() {
        val disabled = shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = "LAVA",
            brand = "LAVA",
            model = "Agni",
            hardware = "generic",
            sdkInt = 35
        )

        assertThat(disabled).isFalse()
    }

    @Test
    fun defaultPolicy_preservesExistingXiaomiAndroid16Disable() {
        val disabled = shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            model = "25010PN30G",
            hardware = "qcom",
            sdkInt = 36
        )

        assertThat(disabled).isTrue()
    }

    @Test
    fun stallFallback_triggersForBufferingPlaybackIntentWithoutAudio() {
        val shouldFallback = shouldTriggerAudioOffloadStallFallback(
            audioOffloadEnabled = true,
            transitionRunning = false,
            isCurrentMasterPlayer = true,
            mediaIdMatches = true,
            playbackState = Player.STATE_BUFFERING,
            isPlaying = false,
            playWhenReady = true,
            playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        )

        assertThat(shouldFallback).isTrue()
    }

    @Test
    fun stallFallback_triggersForReadyPlaybackIntentWithoutAudio() {
        val shouldFallback = shouldTriggerAudioOffloadStallFallback(
            audioOffloadEnabled = true,
            transitionRunning = false,
            isCurrentMasterPlayer = true,
            mediaIdMatches = true,
            playbackState = Player.STATE_READY,
            isPlaying = false,
            playWhenReady = true,
            playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        )

        assertThat(shouldFallback).isTrue()
    }

    @Test
    fun stallFallback_doesNotTriggerForSuppressedPlayback() {
        val shouldFallback = shouldTriggerAudioOffloadStallFallback(
            audioOffloadEnabled = true,
            transitionRunning = false,
            isCurrentMasterPlayer = true,
            mediaIdMatches = true,
            playbackState = Player.STATE_READY,
            isPlaying = false,
            playWhenReady = true,
            playbackSuppressionReason = 1
        )

        assertThat(shouldFallback).isFalse()
    }

    @Test
    fun earlyBuffering_disablesOffloadForGenuineHalReset() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = false,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 120L,
            isPostSeekBuffering = false,
            isPostTransitionBuffering = false,
            isPostMediaItemTransition = false
        )

        assertThat(shouldDisable).isTrue()
    }

    @Test
    fun earlyBuffering_doesNotDisableOffloadRightAfterCrossfade() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = false,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 120L,
            isPostSeekBuffering = false,
            isPostTransitionBuffering = true,
            isPostMediaItemTransition = false
        )

        assertThat(shouldDisable).isFalse()
    }

    @Test
    fun earlyBuffering_doesNotDisableOffloadRightAfterSeek() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = false,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 120L,
            isPostSeekBuffering = true,
            isPostTransitionBuffering = false,
            isPostMediaItemTransition = false
        )

        assertThat(shouldDisable).isFalse()
    }

    @Test
    fun earlyBuffering_doesNotDisableOffloadDuringActiveTransition() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = true,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 120L,
            isPostSeekBuffering = false,
            isPostTransitionBuffering = false,
            isPostMediaItemTransition = false
        )

        assertThat(shouldDisable).isFalse()
    }

    @Test
    fun earlyBuffering_doesNotDisableOffloadAfterLongSteadyPlayback() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = false,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 5_000L,
            isPostSeekBuffering = false,
            isPostTransitionBuffering = false,
            isPostMediaItemTransition = false
        )

        assertThat(shouldDisable).isFalse()
    }

    @Test
    fun earlyBuffering_doesNotDisableOffloadRightAfterMediaItemTransition() {
        val shouldDisable = shouldDisableAudioOffloadOnEarlyBuffering(
            audioOffloadEnabled = true,
            transitionRunning = false,
            lastPlayingAtMs = 1_000L,
            timeSincePlayingMs = 120L,
            isPostSeekBuffering = false,
            isPostTransitionBuffering = false,
            isPostMediaItemTransition = true
        )

        assertThat(shouldDisable).isFalse()
    }
}
