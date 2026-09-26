package com.lostf1sh.pixelplayeross.data.network.navidrome

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class NavidromeResponseParserTest {

    private fun song(created: String?) = JSONObject().apply {
        put("id", "song-1")
        put("title", "Track")
        put("artist", "Artist")
        put("album", "Album")
        put("duration", 180)
        if (created != null) put("created", created)
    }

    @Test
    fun `song created timestamp is parsed to epoch millis`() {
        val parsed = NavidromeResponseParser.parseSong(song("2024-03-15T10:20:30.123Z"))

        assertThat(parsed.created).isEqualTo(1_710_498_030_123L)
    }

    @Test
    fun `song created timestamp with nanosecond precision is parsed`() {
        val parsed = NavidromeResponseParser.parseSong(song("2024-03-15T10:20:30.123456789Z"))

        assertThat(parsed.created).isEqualTo(1_710_498_030_123L)
    }

    @Test
    fun `song without created timestamp reports zero`() {
        assertThat(NavidromeResponseParser.parseSong(song(null)).created).isEqualTo(0L)
        assertThat(NavidromeResponseParser.parseSong(song("not-a-date")).created).isEqualTo(0L)
    }
}
