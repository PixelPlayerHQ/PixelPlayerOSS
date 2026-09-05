package com.lostf1sh.pixelplayeross.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScreenAwakePolicyTest {

    @Test
    fun `screen stays awake only while enabled playback is active`() {
        assertThat(shouldKeepScreenAwake(preferenceEnabled = true, isPlaying = true)).isTrue()
        assertThat(shouldKeepScreenAwake(preferenceEnabled = true, isPlaying = false)).isFalse()
        assertThat(shouldKeepScreenAwake(preferenceEnabled = false, isPlaying = true)).isFalse()
        assertThat(shouldKeepScreenAwake(preferenceEnabled = false, isPlaying = false)).isFalse()
    }
}
