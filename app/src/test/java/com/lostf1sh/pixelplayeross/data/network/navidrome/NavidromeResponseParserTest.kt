package com.lostf1sh.pixelplayeross.data.network.navidrome

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
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

    @Test
    fun `song disc and track numbers are parsed`() {
        val parsed = NavidromeResponseParser.parseSong(
            song(null).apply {
                put("track", 1)
                put("discNumber", 2)
            }
        )

        assertThat(parsed.discNumber).isEqualTo(2)
        assertThat(parsed.trackNumber).isEqualTo(1)
    }

    @Test
    fun `OpenSubsonic genres lists win over the single legacy genre`() {
        val json = song(null).apply {
            put("genre", "House")
            put(
                "genres",
                JSONArray(listOf(JSONObject().put("name", "House"), JSONObject().put("name", "Electronic")))
            )
        }

        assertThat(NavidromeResponseParser.parseSong(json).genre).isEqualTo("House; Electronic")
        assertThat(NavidromeResponseParser.parseAlbum(json).genre).isEqualTo("House; Electronic")
    }

    @Test
    fun `servers without a genres list fall back to the legacy genre`() {
        val legacy = song(null).put("genre", "Jazz")
        val emptyList = song(null).put("genre", "Jazz").put("genres", JSONArray())

        assertThat(NavidromeResponseParser.parseSong(legacy).genre).isEqualTo("Jazz")
        assertThat(NavidromeResponseParser.parseSong(emptyList).genre).isEqualTo("Jazz")
        assertThat(NavidromeResponseParser.parseSong(song(null)).genre).isNull()
    }
}
