package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.MusicFolder
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.library.sortFolderSongs
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * The folder tree only holds title-only stubs. When a folder opens, the songs the library already
 * holds replace them, so a metadata sort does not re-sort the list once the stubs are hydrated.
 */
class FolderNavigationStateHolderTest {

    private val holder = FolderNavigationStateHolder()

    private val afterglow = song(id = "1", title = "Afterglow", trackNumber = 3)
    private val blueHour = song(id = "2", title = "Blue Hour", trackNumber = 1)
    private val coda = song(id = "3", title = "Coda", trackNumber = 2)
    private val single = song(id = "4", title = "Single", trackNumber = 1)

    private val albumFolder = MusicFolder(
        path = "$ROOT/Artist/Album",
        name = "Album",
        songs = persistentListOf(afterglow.stub(), blueHour.stub(), coda.stub())
    )
    private val artistFolder = MusicFolder(
        path = "$ROOT/Artist",
        name = "Artist",
        songs = persistentListOf(single.stub()),
        subFolders = persistentListOf(albumFolder)
    )

    private var state = PlayerUiState(
        folderSourceRootPath = ROOT,
        musicFolders = persistentListOf(artistFolder)
    )
    private val changedFolders = mutableListOf<String>()

    private fun navigateTo(path: String, cache: Map<String, Song>) {
        holder.navigateToFolder(
            path = path,
            getUiState = { state },
            updateUiState = { mutation -> state = mutation(state) },
            cachedSongsById = { cache },
            onFolderChanged = { changedFolders += it }
        )
    }

    private fun navigateBack(cache: Map<String, Song>) {
        holder.navigateBackFolder(
            getUiState = { state },
            updateUiState = { mutation -> state = mutation(state) },
            cachedSongsById = { cache },
            onFolderChanged = { changedFolders += it }
        )
    }

    @Test
    fun `opening a folder replaces its stubs with the cached songs in the same order`() {
        navigateTo(albumFolder.path, cacheOf(afterglow, blueHour, coda, single))

        assertEquals(listOf(afterglow, blueHour, coda), state.currentFolder?.songs)
        assertEquals(albumFolder.path, state.currentFolderPath)
        assertEquals(listOf(albumFolder.path), changedFolders)
    }

    @Test
    fun `a folder opened from the cache is already in album order`() {
        navigateTo(albumFolder.path, cacheOf(afterglow, blueHour, coda))

        val songs = state.currentFolder?.songs.orEmpty()
        assertEquals(
            listOf("2", "3", "1"),
            sortFolderSongs(songs, SortOption.FolderSongAlbum).map { it.id }
        )
    }

    @Test
    fun `songs missing from the cache stay stubs so they are hydrated later`() {
        navigateTo(albumFolder.path, cacheOf(blueHour))

        assertEquals(
            listOf(afterglow.stub(), blueHour, coda.stub()),
            state.currentFolder?.songs
        )
    }

    @Test
    fun `an empty cache publishes the folder from the tree unchanged`() {
        navigateTo(albumFolder.path, emptyMap())

        assertSame(albumFolder, state.currentFolder)
    }

    @Test
    fun `going back to the parent folder also uses the cached songs`() {
        val cache = cacheOf(afterglow, blueHour, coda, single)
        navigateTo(albumFolder.path, cache)

        navigateBack(cache)

        assertEquals(artistFolder.path, state.currentFolder?.path)
        assertEquals(listOf(single), state.currentFolder?.songs)
        assertEquals(listOf(albumFolder.path, artistFolder.path), changedFolders)
    }

    private fun cacheOf(vararg songs: Song): Map<String, Song> = songs.associateBy { it.id }

    // Mirrors FolderTreeBuilder's stubs: only the id, title and path are known.
    private fun Song.stub() = copy(
        artist = "",
        artistId = -1L,
        album = "",
        albumId = -1L,
        contentUriString = "",
        duration = 0L,
        trackNumber = 0,
        dateAdded = 0L
    )

    private fun song(id: String, title: String, trackNumber: Int) = Song(
        id = id,
        title = title,
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "$ROOT/Artist/Album/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L + id.toLong(),
        trackNumber = trackNumber,
        dateAdded = 0L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )

    private companion object {
        const val ROOT = "/storage/emulated/0"
    }
}
