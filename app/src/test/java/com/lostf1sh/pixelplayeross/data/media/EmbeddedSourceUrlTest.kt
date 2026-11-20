package com.lostf1sh.pixelplayeross.data.media

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EmbeddedSourceUrlTest {
    @Test
    fun `first usable SOURCE value keeps escaping and normalizes the scheme`() {
        val tags = mapOf("Source" to arrayOf("not a URL", " HTTPS://example.org/music?a=2%2B3#track "))

        assertThat(findEmbeddedSourceUrl(tags)).isEqualTo("https://example.org/music?a=2%2B3#track")
    }

    @Test
    fun `ID3 user URL frame labeled SOURCE is accepted`() {
        val tags = mapOf("URL:SOURCE" to arrayOf("http://example.org/original"))

        assertThat(findEmbeddedSourceUrl(tags)).isEqualTo("http://example.org/original")
    }

    @Test
    fun `missing or unsafe sources do not produce an action`() {
        val tags = mapOf(
            "SOURCE" to arrayOf(
                "javascript:alert(1)",
                "file:///sdcard/music",
                "content://media/song",
                "intent://example.org",
                "https:example.org",
                "https:///no-host",
                "https://user@example.org",
                "https://bad host"
            ),
            "URL" to arrayOf("https://example.org/unrelated")
        )

        assertThat(findEmbeddedSourceUrl(tags)).isNull()
    }
}
