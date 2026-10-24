package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStateHolderFolderSongSortTest {

    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
        every { songsSortOptionFlow } returns flowOf(SortOption.SongTitleAZ.storageKey)
        every { albumsSortOptionFlow } returns flowOf(SortOption.AlbumTitleAZ.storageKey)
        every { artistsSortOptionFlow } returns flowOf(SortOption.ArtistNameAZ.storageKey)
        every { foldersSortOptionFlow } returns flowOf(SortOption.FolderNameZA.storageKey)
        every { folderSongsSortOptionFlow } returns flowOf(SortOption.FolderSongDateAdded.storageKey)
        every { likedSongsSortOptionFlow } returns flowOf(SortOption.LikedSongDateLiked.storageKey)
        every { genresSortOptionFlow } returns flowOf(SortOption.GenreNameAZ.storageKey)
        every { lastStorageFilterFlow } returns flowOf(StorageFilter.ALL)
    }

    @Test
    fun `initialize restores the stored folder song sort next to the folder sort`() = runTest {
        val owner = ownerScope()
        val holder = LibraryStateHolder(musicRepository, preferences)

        holder.initialize(owner)
        advanceUntilIdle()

        assertEquals(SortOption.FolderSongDateAdded, holder.currentFolderSongSortOption.value)
        assertEquals(SortOption.FolderNameZA, holder.currentFolderSortOption.value)
        owner.cancel()
    }

    @Test
    fun `sorting folder songs stores the choice without changing the folder sort`() = runTest {
        val owner = ownerScope()
        val holder = LibraryStateHolder(musicRepository, preferences)
        holder.initialize(owner)
        advanceUntilIdle()

        holder.sortFolderSongs(SortOption.FolderSongAlbum)
        advanceUntilIdle()

        assertEquals(SortOption.FolderSongAlbum, holder.currentFolderSongSortOption.value)
        assertEquals(SortOption.FolderNameZA, holder.currentFolderSortOption.value)
        coVerify(exactly = 1) { preferences.setFolderSongsSortOption(SortOption.FolderSongAlbum.storageKey) }
        coVerify(exactly = 0) { preferences.setFoldersSortOption(any()) }
        owner.cancel()
    }

    @Test
    fun `choosing the current folder song sort again does not write it`() = runTest {
        val owner = ownerScope()
        val holder = LibraryStateHolder(musicRepository, preferences)
        holder.initialize(owner)
        advanceUntilIdle()

        holder.sortFolderSongs(SortOption.FolderSongDateAdded)
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences.setFolderSongsSortOption(any()) }
        owner.cancel()
    }

    @Test
    fun `applying a folder song sort without persisting skips the write`() = runTest {
        val owner = ownerScope()
        val holder = LibraryStateHolder(musicRepository, preferences)
        holder.initialize(owner)
        advanceUntilIdle()

        holder.sortFolderSongs(SortOption.FolderSongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.FolderSongTitleZA, holder.currentFolderSongSortOption.value)
        coVerify(exactly = 0) { preferences.setFolderSongsSortOption(any()) }
        owner.cancel()
    }

    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())
}
