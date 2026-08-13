package com.lostf1sh.pixelplayeross.data.musicbrainz

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class MusicBrainzApiServiceTest {
    @Test
    fun `recording query escapes lucene syntax`() {
        val query = MusicBrainzApiService.buildRecordingQuery(
            title = "Love (Live): Part II",
            artist = "AC/DC",
            album = "Hits + More"
        )

        assertThat(query).contains("recording:\"Love \\(Live\\)\\: Part II\"")
        assertThat(query).contains("artist:\"AC\\/DC\"")
        assertThat(query).contains("release:\"Hits \\+ More\"")
    }

    @Test
    fun `parser returns canonical ids and prioritizes matching official release`() {
        val response = JSONObject(
            """
            {
              "recordings": [{
                "id": "recording-id",
                "score": 82,
                "title": "Track Name",
                "length": 201000,
                "first-release-date": "2020-04-03",
                "artist-credit": [{
                  "name": "Main Artist",
                  "artist": {"id": "artist-id", "name": "Main Artist"}
                }],
                "releases": [
                  {"id": "bootleg-id", "title": "Other Album", "status": "Bootleg"},
                  {"id": "release-id", "title": "Target Album", "status": "Official", "date": "2020-04-03"}
                ]
              }]
            }
            """.trimIndent()
        )

        val result = MusicBrainzApiService.parseSearchResponse(
            json = response,
            expectedTitle = "Track Name",
            expectedArtist = "Main Artist",
            expectedAlbum = "Target Album",
            expectedDurationMs = 200500
        ).single()

        assertThat(result.recordingId).isEqualTo("recording-id")
        assertThat(result.releaseId).isEqualTo("release-id")
        assertThat(result.artistId).isEqualTo("artist-id")
        assertThat(result.year).isEqualTo(2020)
        assertThat(result.score).isEqualTo(100)
    }

    @Test
    fun `duration mismatch lowers otherwise similar result`() {
        val response = JSONObject(
            """
            {"recordings": [{
              "id": "recording-id",
              "score": 75,
              "title": "Track",
              "length": 280000,
              "artist-credit": [{"name": "Artist", "artist": {"id": "artist-id"}}]
            }]}
            """.trimIndent()
        )

        val result = MusicBrainzApiService.parseSearchResponse(
            json = response,
            expectedTitle = "Track",
            expectedArtist = "Artist",
            expectedAlbum = null,
            expectedDurationMs = 180000
        ).single()

        assertThat(result.score).isEqualTo(79)
    }
}
