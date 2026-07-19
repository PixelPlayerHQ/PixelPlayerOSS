package com.lostf1sh.pixelplayeross.data.navidrome

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.NavidromePlaylistEntity
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeMusicFolder
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

    @Test
    fun `selected music folders defaults to all available folders when saved selection is empty`() {
        val folders = listOf(
            NavidromeMusicFolder(id = "flac", name = "FLAC"),
            NavidromeMusicFolder(id = "mp3", name = "MP3")
        )

        val selectedIds = selectedNavidromeMusicFolderIds(
            availableFolders = folders,
            savedFolderIds = emptySet()
        )

        assertThat(selectedIds).containsExactly("flac", "mp3")
    }

    @Test
    fun `selected music folders keeps valid saved subset`() {
        val folders = listOf(
            NavidromeMusicFolder(id = "flac", name = "FLAC"),
            NavidromeMusicFolder(id = "mp3", name = "MP3")
        )

        val selectedIds = selectedNavidromeMusicFolderIds(
            availableFolders = folders,
            savedFolderIds = setOf("flac")
        )

        assertThat(selectedIds).containsExactly("flac")
    }

    @Test
    fun `selected music folders falls back to all when saved ids are stale`() {
        val folders = listOf(
            NavidromeMusicFolder(id = "flac", name = "FLAC"),
            NavidromeMusicFolder(id = "mp3", name = "MP3")
        )

        val selectedIds = selectedNavidromeMusicFolderIds(
            availableFolders = folders,
            savedFolderIds = setOf("old")
        )

        assertThat(selectedIds).containsExactly("flac", "mp3")
    }

    @Test
    fun `selected music folders returns empty when server exposes no folders`() {
        val selectedIds = selectedNavidromeMusicFolderIds(
            availableFolders = emptyList(),
            savedFolderIds = setOf("flac")
        )

        assertThat(selectedIds).isEmpty()
    }

    @Test
    fun `forced favorites refresh always allowed`() {
        assertThat(shouldRefreshCloudFavorites(force = true, nowMs = 1_000L, lastRefreshMs = 999L, minIntervalMs = 30_000L)).isTrue()
    }

    @Test
    fun `favorites refresh blocked when interval has not elapsed since epoch zero`() {
        assertThat(shouldRefreshCloudFavorites(force = false, nowMs = 5L, lastRefreshMs = 0L, minIntervalMs = 30_000L)).isFalse()
    }

    @Test
    fun `favorites refresh blocked within cooldown`() {
        assertThat(shouldRefreshCloudFavorites(force = false, nowMs = 40_000L, lastRefreshMs = 20_000L, minIntervalMs = 30_000L)).isFalse()
    }

    @Test
    fun `favorites refresh allowed after cooldown`() {
        assertThat(shouldRefreshCloudFavorites(force = false, nowMs = 60_000L, lastRefreshMs = 20_000L, minIntervalMs = 30_000L)).isTrue()
    }
}
