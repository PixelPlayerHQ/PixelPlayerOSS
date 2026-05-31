package com.lostf1sh.pixelplayeross.data.navidrome

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.NavidromePlaylistEntity
import org.junit.jupiter.api.Test

class NavidromeRepositoryTest {

    @Test
    fun `library fallback is shown when server has no playlists but library songs are cached`() {
        val playlists = navidromePlaylistsWithLibraryFallback(
            playlists = emptyList(),
            librarySongCount = 42,
            libraryName = "Library",
            nowMs = 123L
        )

        assertThat(playlists).hasSize(1)
        assertThat(playlists.first().id).isEqualTo(NavidromeRepository.LIBRARY_PLAYLIST_ID)
        assertThat(playlists.first().name).isEqualTo("Library")
        assertThat(playlists.first().songCount).isEqualTo(42)
        assertThat(playlists.first().lastSyncTime).isEqualTo(123L)
    }

    @Test
    fun `library fallback does not hide real playlists`() {
        val remotePlaylist = NavidromePlaylistEntity(
            id = "playlist-1",
            name = "Favorites",
            comment = null,
            owner = null,
            coverArtId = null,
            songCount = 3,
            duration = 0L,
            public = false,
            lastSyncTime = 1L
        )

        val playlists = navidromePlaylistsWithLibraryFallback(
            playlists = listOf(remotePlaylist),
            librarySongCount = 42,
            libraryName = "Library",
            nowMs = 123L
        )

        assertThat(playlists).containsExactly(remotePlaylist)
    }
}
