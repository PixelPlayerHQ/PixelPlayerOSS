package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectoryRuleResolverTest {

    @Test
    fun excludeThenIncludePath_pathBecomesVisibleAgain() {
        val targetPath = "/storage/emulated/0/Music"

        val excluded = DirectoryRuleResolver(allowed = emptySet(), blocked = setOf(targetPath))
        assertTrue(excluded.isBlocked(targetPath))

        val includedAgain = DirectoryRuleResolver(allowed = emptySet(), blocked = emptySet())
        assertFalse(includedAgain.isBlocked(targetPath))
    }

    @Test
    fun includeThenExcludePath_pathBecomesHidden() {
        val targetPath = "/storage/emulated/0/Music"

        val included = DirectoryRuleResolver(allowed = emptySet(), blocked = emptySet())
        assertFalse(included.isBlocked(targetPath))

        val excluded = DirectoryRuleResolver(allowed = emptySet(), blocked = setOf(targetPath))
        assertTrue(excluded.isBlocked(targetPath))
    }

    @Test
    fun nestedAllow_insideBlockedParent_isRespected() {
        val resolver = DirectoryRuleResolver(
            allowed = setOf("/storage/emulated/0/Music/Favorites"),
            blocked = setOf("/storage/emulated/0/Music")
        )

        assertTrue(resolver.isBlocked("/storage/emulated/0/Music"))
        assertTrue(resolver.isBlocked("/storage/emulated/0/Music/Albums"))
        assertFalse(resolver.isBlocked("/storage/emulated/0/Music/Favorites"))
        assertFalse(resolver.isBlocked("/storage/emulated/0/Music/Favorites/Chill"))
    }

    @Test
    fun siblingPath_outsideBlockedTree_staysVisible() {
        val resolver = DirectoryRuleResolver(
            allowed = emptySet(),
            blocked = setOf("/storage/emulated/0/Music")
        )

        assertFalse(resolver.isBlocked("/storage/emulated/0/Podcasts"))
    }
}
