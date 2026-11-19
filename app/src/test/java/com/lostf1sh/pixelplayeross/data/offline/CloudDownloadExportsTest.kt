package com.lostf1sh.pixelplayeross.data.offline

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CloudDownloadExportsTest {
    @Test
    fun `export names sanitize path characters without losing the audio extension`() {
        assertThat(exportFileName("../Artist\\Track:\u0000", "1234567890abcdef", "flac"))
            .isEqualTo("_Artist_Track__ - 1234567890ab.flac")
    }

    @Test
    fun `same title from different tracks has a different export name`() {
        val first = exportFileName("Same title", "1234567890abcdef", "m4a")
        val second = exportFileName("Same title", "fedcba0987654321", "m4a")
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `blank and long titles still produce valid export names`() {
        assertThat(exportFileName(" ... ", "1234567890abcdef", "mp3"))
            .isEqualTo("Track - 1234567890ab.mp3")
        assertThat(exportFileName("歌".repeat(100), "1234567890abcdef", "mp3"))
            .isEqualTo("${"歌".repeat(60)} - 1234567890ab.mp3")
    }
}
