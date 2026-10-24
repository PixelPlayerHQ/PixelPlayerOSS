package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStateHolderOwnerTest {

    private val musicRepository = mockk<MusicRepository>(relaxed = true)

    @Test
    fun `stale owner clearing keeps the newer binding`() = runTest {
        val holder = holder()
        val staleOwner = ownerScope()
        val currentOwner = ownerScope()
        holder.initialize(staleOwner)
        holder.initialize(currentOwner)
        advanceUntilIdle()

        holder.onCleared(staleOwner)
        holder.sortSongs(SortOption.SongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.SongTitleZA, holder.currentSongSortOption.value)
        staleOwner.cancel()
        currentOwner.cancel()
    }

    @Test
    fun `current owner clearing unbinds the holder`() = runTest {
        val holder = holder()
        val owner = ownerScope()
        holder.initialize(owner)
        advanceUntilIdle()
        assertEquals(SortOption.SongTitleAZ, holder.currentSongSortOption.value)

        holder.onCleared(owner)
        holder.sortSongs(SortOption.SongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.SongTitleAZ, holder.currentSongSortOption.value)
        owner.cancel()
    }

    @Test
    fun `current owner clearing hands sorting back to the surviving owner`() = runTest {
        val holder = holder()
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        holder.initialize(mainActivityOwner)
        holder.initialize(externalPlayerOwner)
        advanceUntilIdle()

        clear(holder, externalPlayerOwner)
        holder.sortSongs(SortOption.SongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.SongTitleZA, holder.currentSongSortOption.value)
        mainActivityOwner.cancel()
    }

    @Test
    fun `library collectors started by the clearing owner move to the surviving owner`() = runTest {
        val audioFiles = MutableStateFlow(emptyList<Song>())
        every { musicRepository.getAudioFiles() } returns audioFiles
        val holder = holder()
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        holder.initialize(mainActivityOwner)
        holder.initialize(externalPlayerOwner)
        // Started while ExternalPlayerActivity's view model is current, so in its scope.
        holder.startObservingLibraryData()
        advanceUntilIdle()

        clear(holder, externalPlayerOwner)
        audioFiles.value = listOf(song("added-later"))

        // The collector maps songs on Dispatchers.Default, so wait for the result instead of
        // only draining the test dispatcher.
        val songs = holder.allSongs.first { it.isNotEmpty() }
        assertEquals(listOf("added-later"), songs.map { it.id })
        mainActivityOwner.cancel()
    }

    @Test
    fun `library collectors started by a stale owner move to the current owner`() = runTest {
        val audioFiles = MutableStateFlow(emptyList<Song>())
        every { musicRepository.getAudioFiles() } returns audioFiles
        val holder = holder()
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        holder.initialize(mainActivityOwner)
        holder.startObservingLibraryData()
        holder.initialize(externalPlayerOwner)
        advanceUntilIdle()

        clear(holder, mainActivityOwner)
        audioFiles.value = listOf(song("added-later"))

        // The collector maps songs on Dispatchers.Default, so wait for the result instead of
        // only draining the test dispatcher.
        val songs = holder.allSongs.first { it.isNotEmpty() }
        assertEquals(listOf("added-later"), songs.map { it.id })
        externalPlayerOwner.cancel()
    }

    /** Clears [owner] the way ViewModel.clear() does: the scope is cancelled before onCleared. */
    private fun clear(holder: LibraryStateHolder, owner: CoroutineScope) {
        owner.cancel()
        holder.onCleared(owner)
    }

    // Separate Job so each owner has its own identity, while still running on the test
    // dispatcher that advanceUntilIdle drives.
    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    private fun holder(): LibraryStateHolder {
        val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
            every { songsSortOptionFlow } returns flowOf(SortOption.SongTitleAZ.storageKey)
            every { albumsSortOptionFlow } returns flowOf(SortOption.AlbumTitleAZ.storageKey)
            every { artistsSortOptionFlow } returns flowOf(SortOption.ArtistNameAZ.storageKey)
            every { foldersSortOptionFlow } returns flowOf(SortOption.FolderNameAZ.storageKey)
            every { folderSongsSortOptionFlow } returns flowOf(SortOption.FolderSongTitleAZ.storageKey)
            every { likedSongsSortOptionFlow } returns flowOf(SortOption.LikedSongDateLiked.storageKey)
            every { genresSortOptionFlow } returns flowOf(SortOption.GenreNameAZ.storageKey)
            every { lastStorageFilterFlow } returns flowOf(StorageFilter.ALL)
        }
        return LibraryStateHolder(musicRepository, preferences)
    }

    private fun song(id: String) = Song(
        id = id,
        title = id,
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
