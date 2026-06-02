package com.lostf1sh.pixelplayeross.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartPlaylistRuleTest {

    @Test
    fun `every rule round-trips through its playlist source`() {
        for (rule in SmartPlaylistRule.entries) {
            val source = rule.toPlaylistSource()
            assertTrue(source.startsWith(SMART_PLAYLIST_SOURCE_PREFIX), "source should carry the SMART prefix: $source")
            assertEquals(rule, SmartPlaylistRule.fromPlaylistSource(source), "round-trip failed for $rule")
            assertTrue(isSmartPlaylistSource(source), "$source should be recognised as smart")
        }
    }

    @Test
    fun `fromStorageKey resolves known keys and rejects unknown or blank`() {
        assertEquals(SmartPlaylistRule.TOP_PLAYED, SmartPlaylistRule.fromStorageKey("top_played"))
        assertEquals(SmartPlaylistRule.NEW_GEMS, SmartPlaylistRule.fromStorageKey("new_gems"))
        assertNull(SmartPlaylistRule.fromStorageKey("does_not_exist"))
        assertNull(SmartPlaylistRule.fromStorageKey(""))
        assertNull(SmartPlaylistRule.fromStorageKey(null))
    }

    @Test
    fun `legacy bare SMART source is detected but resolves to no specific rule`() {
        // The bare "SMART" marker (no rule key) predates per-rule keys: it must still be
        // recognised as a smart playlist, but cannot resolve to a concrete rule.
        assertTrue(isSmartPlaylistSource(SMART_PLAYLIST_SOURCE_LEGACY))
        assertNull(SmartPlaylistRule.fromPlaylistSource(SMART_PLAYLIST_SOURCE_LEGACY))
    }

    @Test
    fun `non-smart sources are not misclassified`() {
        assertFalse(isSmartPlaylistSource("LOCAL"))
        assertFalse(isSmartPlaylistSource("SMARTISH"))
        assertNull(SmartPlaylistRule.fromPlaylistSource("LOCAL"))
    }

    @Test
    fun `fromPlaylistSource rejects an unknown rule key`() {
        assertNull(SmartPlaylistRule.fromPlaylistSource("${SMART_PLAYLIST_SOURCE_PREFIX}bogus"))
    }
}
