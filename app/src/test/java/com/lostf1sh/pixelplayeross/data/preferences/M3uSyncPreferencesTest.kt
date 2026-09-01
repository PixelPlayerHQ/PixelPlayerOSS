package com.lostf1sh.pixelplayeross.data.preferences

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.playlist.M3uSyncCheckpoint
import org.junit.jupiter.api.Test

class M3uSyncPreferencesTest {

    @Test
    fun `link replacement is rejected after the selected tree changes`() {
        val oldLink = link("playlist", "content://old/document")
        val newLink = link("playlist", "content://new/document")
        val config = M3uSyncConfig(
            treeUri = "content://tree/new",
            revision = 2,
            links = mapOf(oldLink.playlistId to oldLink),
        )

        val updated = config.withLinksIfSelectionMatches(
            expectedTreeUri = "content://tree/old",
            expectedRevision = 1,
            replacementLinks = mapOf(newLink.playlistId to newLink),
        )

        assertThat(updated).isNull()
    }

    @Test
    fun `link replacement commits for the same selected tree`() {
        val newLink = link("playlist", "content://tree/document")
        val config = M3uSyncConfig(treeUri = "content://tree", revision = 7)

        val updated = config.withLinksIfSelectionMatches(
            expectedTreeUri = "content://tree",
            expectedRevision = 7,
            replacementLinks = mapOf(newLink.playlistId to newLink),
        )

        assertThat(updated?.links).containsExactly(newLink.playlistId, newLink)
    }

    @Test
    fun `link replacement is rejected when the same tree was reselected`() {
        val config = M3uSyncConfig(treeUri = "content://tree", revision = 8)

        val updated = config.withLinksIfSelectionMatches(
            expectedTreeUri = "content://tree",
            expectedRevision = 7,
            replacementLinks = mapOf("playlist" to link("playlist", "content://tree/document")),
        )

        assertThat(updated).isNull()
    }

    private fun link(playlistId: String, documentUri: String) = M3uSyncLink(
        playlistId = playlistId,
        documentUri = documentUri,
        fileName = "$playlistId.m3u8",
        checkpoint = M3uSyncCheckpoint("app", "file"),
    )
}
