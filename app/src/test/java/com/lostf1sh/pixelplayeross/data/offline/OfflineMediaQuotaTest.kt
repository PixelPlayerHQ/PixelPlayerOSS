package com.lostf1sh.pixelplayeross.data.offline

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineMediaQuotaTest {
    @Test
    fun `unlimited cache reports effectively unlimited capacity`() {
        assertEquals(Long.MAX_VALUE, availableForOfflineCache(limitBytes = 0L, usedBytes = 9_000L))
    }

    @Test
    fun `bounded cache never reports negative capacity`() {
        assertEquals(2_000L, availableForOfflineCache(limitBytes = 5_000L, usedBytes = 3_000L))
        assertEquals(0L, availableForOfflineCache(limitBytes = 5_000L, usedBytes = 8_000L))
    }

    @Test
    fun `estimate uses bitrate duration and safety margin`() {
        val song = testSong(durationMs = 60_000L, bitrate = 320_000)

        val estimate = estimateOfflineBytes(listOf(song))

        assertEquals(2_640_000L, estimate)
    }

    @Test
    fun `estimate has conservative fallbacks for missing metadata`() {
        val estimate = estimateOfflineBytes(listOf(testSong(durationMs = 0L, bitrate = null)))

        assertTrue(estimate >= 2_640_000L)
    }

    private fun testSong(durationMs: Long, bitrate: Int?): Song = Song(
        id = "song",
        title = "Song",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 2L,
        path = "",
        contentUriString = "navidrome://song",
        albumArtUriString = null,
        duration = durationMs,
        mimeType = "audio/mpeg",
        bitrate = bitrate,
        sampleRate = 44_100,
    )
}
