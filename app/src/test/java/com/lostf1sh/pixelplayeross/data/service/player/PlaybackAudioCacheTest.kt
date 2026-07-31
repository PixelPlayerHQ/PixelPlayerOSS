package com.lostf1sh.pixelplayeross.data.service.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackAudioCacheTest {

    @Test
    fun `cache key is stable and does not expose account or track details`() {
        val sourceIdentity = "navidrome|https://music.example.test|listener"
        val mediaUri = "navidrome://secret-track-id"

        val first = buildPlaybackCacheKey(sourceIdentity, mediaUri)
        val second = buildPlaybackCacheKey(sourceIdentity, mediaUri)

        assertEquals(first, second)
        assertTrue(first.startsWith(PLAYBACK_CACHE_KEY_PREFIX))
        assertFalse(first.contains("music.example.test"))
        assertFalse(first.contains("listener"))
        assertFalse(first.contains("secret-track-id"))
    }

    @Test
    fun `cache key is scoped to account and provider`() {
        val mediaUri = "navidrome://track-id"
        val firstAccount = buildPlaybackCacheKey(
            "navidrome|https://music.example.test|first",
            mediaUri,
        )
        val secondAccount = buildPlaybackCacheKey(
            "navidrome|https://music.example.test|second",
            mediaUri,
        )
        val otherProvider = buildPlaybackCacheKey(
            "jellyfin|https://music.example.test|first",
            mediaUri,
        )

        assertNotEquals(firstAccount, secondAccount)
        assertNotEquals(firstAccount, otherProvider)
    }

    @Test
    fun `only app playback cache keys are routed through disk cache`() {
        assertTrue(shouldUsePlaybackCache(buildPlaybackCacheKey("source", "track")))
        assertFalse(shouldUsePlaybackCache(null))
        assertFalse(shouldUsePlaybackCache("https://music.example.test/stream?token=secret"))
    }
}
