package com.lostf1sh.pixelplayeross.data.stream

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CloudMusicUtilsTest {

    @Test
    fun `normalizeDiscNumber keeps positive disc numbers`() {
        assertThat(CloudMusicUtils.normalizeDiscNumber(1)).isEqualTo(1)
        assertThat(CloudMusicUtils.normalizeDiscNumber(2)).isEqualTo(2)
    }

    @Test
    fun `normalizeDiscNumber treats zero and negative discs as missing`() {
        assertThat(CloudMusicUtils.normalizeDiscNumber(0)).isNull()
        assertThat(CloudMusicUtils.normalizeDiscNumber(-1)).isNull()
    }
}
