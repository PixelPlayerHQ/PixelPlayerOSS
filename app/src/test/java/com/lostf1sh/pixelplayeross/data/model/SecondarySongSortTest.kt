package com.lostf1sh.pixelplayeross.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecondarySongSortTest {
    @Test
    fun `an enabled secondary sort keeps its chosen direction`() {
        assertEquals(
            SortOption.SongAlbumDesc,
            SortOption.SongArtist.resolveSecondarySongSort(SortOption.SongAlbumDesc)
        )
    }

    @Test
    fun `choosing a primary that duplicates the secondary uses a different method`() {
        assertEquals(
            SortOption.SongTitleAZ,
            SortOption.SongAlbum.resolveSecondarySongSort(SortOption.SongAlbumDesc)
        )
    }

    @Test
    fun `disabled or unrelated options do not change ordering`() {
        assertNull(SortOption.SongArtist.resolveSecondarySongSort(null))
        assertNull(SortOption.SongArtist.resolveSecondarySongSort(SortOption.FolderSongAlbum))
    }
}
