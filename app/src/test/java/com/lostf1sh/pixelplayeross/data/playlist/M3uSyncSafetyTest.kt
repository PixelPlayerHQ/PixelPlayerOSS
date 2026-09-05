package com.lostf1sh.pixelplayeross.data.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class M3uSyncSafetyTest {

    @Test
    fun `external marker never reuses an occupied playlist id`() {
        val selectedId = M3uSyncSafety.safeRequestedPlaylistId(
            markerId = "navidrome_playlist_remote-id",
            occupiedPlaylistIds = setOf("navidrome_playlist_remote-id"),
        )

        assertThat(selectedId).isNull()
    }

    @Test
    fun `external marker can preserve an unclaimed playlist id`() {
        val selectedId = M3uSyncSafety.safeRequestedPlaylistId(
            markerId = "pixel-player-playlist-id",
            occupiedPlaylistIds = setOf("another-playlist"),
        )

        assertThat(selectedId).isEqualTo("pixel-player-playlist-id")
    }

    @Test
    fun `first import requires every file entry to resolve unambiguously`() {
        val complete = M3uParseResult(
            playlistId = null,
            songIds = listOf("song-1"),
            unresolvedEntries = emptyList(),
            ambiguousEntries = emptyList(),
        )
        val unresolved = complete.copy(unresolvedEntries = listOf("missing.ogg"))
        val ambiguous = complete.copy(ambiguousEntries = listOf("shared.ogg"))

        assertThat(M3uSyncSafety.canImportEntireFile(complete)).isTrue()
        assertThat(M3uSyncSafety.canImportEntireFile(unresolved)).isFalse()
        assertThat(M3uSyncSafety.canImportEntireFile(ambiguous)).isFalse()
    }

    @Test
    fun `export reports every playlist song missing from the library`() {
        val missing = M3uSyncSafety.missingPlaylistSongIds(
            playlistSongIds = listOf("present", "missing", "missing-again", "missing"),
            availableSongIds = setOf("present"),
        )

        assertThat(missing).containsExactly("missing", "missing-again").inOrder()
    }

    @Test
    fun `replacement names keep incomplete writes out of scans and backups recoverable`() {
        val names = m3uReplacementNames(
            desiredName = "Road Trip.m3u8",
            transactionId = "transaction",
        )

        assertThat(names.temporary).endsWith(".tmp")
        assertThat(names.temporary.lowercase().endsWith(".m3u")).isFalse()
        assertThat(names.temporary.lowercase().endsWith(".m3u8")).isFalse()
        assertThat(names.backup).endsWith(".m3u8")
        assertThat(names.temporary).isNotEqualTo(names.backup)
    }
}
