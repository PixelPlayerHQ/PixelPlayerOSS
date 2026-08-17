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
    fun fromStorageKey_fallsBackToSystemDefaultForUnknownValue() {
        assertThat(AudioOutputMode.fromStorageKey("unknown")).isEqualTo(AudioOutputMode.SYSTEM_DEFAULT)
    }
}
