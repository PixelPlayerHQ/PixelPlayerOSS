package com.lostf1sh.pixelplayeross.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AudioOutputModeTest {

    @Test
    fun fromStorageKey_restoresDirectMode() {
        assertThat(AudioOutputMode.fromStorageKey("direct")).isEqualTo(AudioOutputMode.DIRECT)
    }

    @Test
    fun fromStorageKey_migratesLegacyHiFiModeToPcmFloat() {
        assertThat(
            AudioOutputMode.fromStorageKey(value = null, legacyHiFiEnabled = true)
        ).isEqualTo(AudioOutputMode.PCM_FLOAT)
    }

    @Test
    fun fromStorageKey_prefersExplicitModeOverLegacyHiFiFlag() {
        assertThat(
            AudioOutputMode.fromStorageKey(
                value = AudioOutputMode.DIRECT.storageKey,
                legacyHiFiEnabled = true
            )
        ).isEqualTo(AudioOutputMode.DIRECT)
    }

    @Test
    fun bitPerfectUsb_restoresAndDecodesToFloat() {
        val mode = AudioOutputMode.fromStorageKey("bit_perfect_usb")

        assertThat(mode).isEqualTo(AudioOutputMode.BIT_PERFECT_USB)
        assertThat(mode.usesFloatOutput).isTrue()
        assertThat(mode.usesUnmodifiedMedia3AudioSink).isFalse()
    }

    @Test
    fun bitPerfectUsb_needsAndroid14AndFloatOutput() {
        assertThat(AudioOutputMode.isBitPerfectUsbAvailable(sdkInt = 34, pcmFloatSupported = true)).isTrue()
        assertThat(AudioOutputMode.isBitPerfectUsbAvailable(sdkInt = 33, pcmFloatSupported = true)).isFalse()
        assertThat(AudioOutputMode.isBitPerfectUsbAvailable(sdkInt = 36, pcmFloatSupported = false)).isFalse()
    }

    @Test
    fun fromStorageKey_fallsBackToSystemDefaultForUnknownValue() {
        assertThat(AudioOutputMode.fromStorageKey("unknown")).isEqualTo(AudioOutputMode.SYSTEM_DEFAULT)
    }
}
