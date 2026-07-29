package com.lostf1sh.pixelplayeross.data.listenbrainz

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrobbleManagerTest {

    @Test
    fun `long track requires four minutes`() {
        val tenMinutes = 600_000L
        assertFalse(ScrobbleManager.meetsListenThreshold(listenedMs = 239_999L, trackDurationMs = tenMinutes))
        assertTrue(ScrobbleManager.meetsListenThreshold(listenedMs = 240_000L, trackDurationMs = tenMinutes))
    }

    @Test
    fun `short track requires half its duration`() {
        val threeMinutes = 180_000L
        assertFalse(ScrobbleManager.meetsListenThreshold(listenedMs = 89_999L, trackDurationMs = threeMinutes))
        assertTrue(ScrobbleManager.meetsListenThreshold(listenedMs = 90_000L, trackDurationMs = threeMinutes))
    }

    @Test
    fun `eight minute track still needs only four minutes`() {
        val eightMinutes = 480_000L
        assertTrue(ScrobbleManager.meetsListenThreshold(listenedMs = 240_000L, trackDurationMs = eightMinutes))
    }

    @Test
    fun `unknown duration falls back to four minute rule`() {
        assertFalse(ScrobbleManager.meetsListenThreshold(listenedMs = 239_999L, trackDurationMs = 0L))
        assertTrue(ScrobbleManager.meetsListenThreshold(listenedMs = 240_000L, trackDurationMs = 0L))
        assertFalse(ScrobbleManager.meetsListenThreshold(listenedMs = 200_000L, trackDurationMs = -1L))
    }

    @Test
    fun `zero listening never scrobbles`() {
        assertFalse(ScrobbleManager.meetsListenThreshold(listenedMs = 0L, trackDurationMs = 180_000L))
    }
}
