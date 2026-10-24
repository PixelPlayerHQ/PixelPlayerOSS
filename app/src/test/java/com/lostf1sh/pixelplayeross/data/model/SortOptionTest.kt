package com.lostf1sh.pixelplayeross.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortOptionTest {

    @Test
    fun `method option keeps a single representative per sort method`() {
        assertEquals(SortOption.SongTitleAZ, SortOption.SongTitleZA.methodOption())
        assertEquals(SortOption.SongArtist, SortOption.SongArtistDesc.methodOption())
        assertEquals(SortOption.SongDateAdded, SortOption.SongDateAddedAsc.methodOption())
        assertEquals(SortOption.SongDefaultOrder, SortOption.SongDefaultOrder.methodOption())
    }

    @Test
    fun `resolve for direction keeps method while switching order`() {
        assertEquals(
            SortOption.SongArtistDesc,
            SortOption.SongArtist.resolveForDirection(SortDirection.Descending)
        )
        assertEquals(
            SortOption.SongArtist,
            SortOption.SongArtistDesc.resolveForDirection(SortDirection.Ascending)
        )
        assertEquals(
            SortOption.SongDateAddedAsc,
            SortOption.SongDateAdded.resolveForDirection(SortDirection.Ascending)
        )
        assertEquals(
            SortOption.PlaylistDateCreated,
            SortOption.PlaylistDateCreatedAsc.resolveForDirection(SortDirection.Descending)
        )
    }

    @Test
    fun `flip direction swaps paired sort options`() {
        assertEquals(SortOption.SongTitleZA, SortOption.SongTitleAZ.flipDirection())
        assertEquals(SortOption.SongTitleAZ, SortOption.SongTitleZA.flipDirection())
        assertEquals(SortOption.LikedSongDateLikedAsc, SortOption.LikedSongDateLiked.flipDirection())
        assertEquals(SortOption.FolderSongCountAsc, SortOption.FolderSongCountDesc.flipDirection())
        assertEquals(SortOption.SongDefaultOrder, SortOption.SongDefaultOrder.flipDirection())
    }

    @Test
    fun `from storage key still resolves legacy display names`() {
        val resolved = SortOption.fromStorageKey(
            rawValue = SortOption.SongArtist.displayName,
            allowed = SortOption.SONGS,
            fallback = SortOption.SongTitleAZ
        )

        assertEquals(SortOption.SongArtist, resolved)
    }

    @Test
    fun `from storage key still resolves the legacy english display name after localization`() {
        val resolved = SortOption.fromStorageKey(
            rawValue = "Title (Z-A)",
            allowed = SortOption.SONGS,
            fallback = SortOption.SongTitleAZ
        )

        assertEquals(SortOption.SongTitleZA, resolved)
    }

    @Test
    fun `every sort option exposes a method label resource`() {
        allOptions.forEach { option ->
            assertNotEquals(0, option.methodLabelRes, "${option.storageKey} has no method label resource")
        }
    }

    @Test
    fun `options sharing a sort method share the same method label resource`() {
        allOptions.groupBy { it.methodKey }.forEach { (methodKey, options) ->
            val labels = options.map { it.methodLabelRes }.distinct()
            assertEquals(1, labels.size, "$methodKey rows resolve to different labels")
        }
    }

    @Test
    fun `flipping direction keeps the method label resource`() {
        allOptions.forEach { option ->
            assertEquals(
                option.methodLabelRes,
                option.flipDirection().methodLabelRes,
                "${option.storageKey} changes label when its direction flips"
            )
        }
    }

    @Test
    fun `folder song options flip and resolve within their own method`() {
        assertEquals(SortOption.FolderSongTitleZA, SortOption.FolderSongTitleAZ.flipDirection())
        assertEquals(SortOption.FolderSongArtistDesc, SortOption.FolderSongArtist.flipDirection())
        assertEquals(SortOption.FolderSongAlbum, SortOption.FolderSongAlbumDesc.flipDirection())
        assertEquals(SortOption.FolderSongAlbumArtistDesc, SortOption.FolderSongAlbumArtist.flipDirection())
        assertEquals(SortOption.FolderSongDateAddedAsc, SortOption.FolderSongDateAdded.flipDirection())
        assertEquals(SortOption.FolderSongDuration, SortOption.FolderSongDurationAsc.flipDirection())
        assertEquals(SortOption.FolderSongAlbumArtist, SortOption.FolderSongAlbumArtistDesc.methodOption())
        assertEquals(
            SortOption.FolderSongDurationAsc,
            SortOption.FolderSongDuration.resolveForDirection(SortDirection.Ascending)
        )
    }

    @Test
    fun `folder song methods default to the same direction as the songs tab`() {
        val defaultDirections = SortOption.FOLDER_SONGS
            .map { it.methodOption() }
            .distinct()
            .associate { it.methodLabelRes to it.direction }

        assertEquals(6, defaultDirections.size)
        SortOption.SONGS.map { it.methodOption() }.distinct()
            .filter { it.methodLabelRes in defaultDirections }
            .forEach { songsTabMethod ->
                assertEquals(
                    songsTabMethod.direction,
                    defaultDirections[songsTabMethod.methodLabelRes],
                    "${songsTabMethod.storageKey} starts in a different direction for folder songs"
                )
            }
    }

    @Test
    fun `folder song methods never share a method key with another list`() {
        val folderSongMethods = SortOption.FOLDER_SONGS.map { it.methodKey }.toSet()
        val otherMethods = (allOptions - SortOption.FOLDER_SONGS.toSet()).map { it.methodKey }.toSet()

        assertTrue(folderSongMethods.intersect(otherMethods).isEmpty())
    }

    @Test
    fun `storage keys are unique across every sort list`() {
        val duplicates = allOptions.groupBy { it.storageKey }.filterValues { it.size > 1 }.keys

        assertTrue(duplicates.isEmpty(), "Duplicate storage keys: $duplicates")
    }

    @Test
    fun `folder song storage keys resolve only within the folder song list`() {
        assertEquals(
            SortOption.FolderSongAlbumArtistDesc,
            SortOption.fromStorageKey(
                rawValue = SortOption.FolderSongAlbumArtistDesc.storageKey,
                allowed = SortOption.FOLDER_SONGS,
                fallback = SortOption.FolderSongTitleAZ
            )
        )
        assertEquals(
            SortOption.FolderSongTitleAZ,
            SortOption.fromStorageKey(
                rawValue = SortOption.FolderNameZA.storageKey,
                allowed = SortOption.FOLDER_SONGS,
                fallback = SortOption.FolderSongTitleAZ
            )
        )
    }

    private val allOptions: List<SortOption>
        get() = SortOption.SONGS + SortOption.ALBUMS + SortOption.ARTISTS +
            SortOption.PLAYLISTS + SortOption.FOLDERS + SortOption.FOLDER_SONGS + SortOption.LIKED
}
