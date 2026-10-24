package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.SearchFilterType
import com.lostf1sh.pixelplayeross.data.model.SearchResultItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Search screen's filter chips: picking one has to swap the results for that filter's
 * results, for the query already on screen, at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateHolderTest {

    private val nightSongs = listOf(SearchResultItem.SongItem(song("night-drive")))
    private val nightAlbums = listOf(SearchResultItem.AlbumItem(album(id = 7L, title = "Night Signals")))
    private val nightArtists = listOf(
        SearchResultItem.ArtistItem(Artist(id = 9L, name = "Nightfall Choir", songCount = 2))
    )

    private val musicRepository = mockk<MusicRepository>(relaxed = true) {
        every { searchAll(any(), SearchFilterType.ALL) } returns
            flowOf(nightSongs + nightAlbums + nightArtists)
        every { searchAll(any(), SearchFilterType.SONGS) } returns flowOf(nightSongs)
        every { searchAll(any(), SearchFilterType.ALBUMS) } returns flowOf(nightAlbums)
        every { searchAll(any(), SearchFilterType.ARTISTS) } returns flowOf(nightArtists)
        every { searchAll(any(), SearchFilterType.PLAYLISTS) } returns flowOf(emptyList())
    }

    @Test
    fun `changing the filter shows that filter's results without waiting for the typing debounce`() = runTest {
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()
        holder.performSearch("night")
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.ARTISTS)
        runCurrent()

        assertEquals(SearchFilterType.ARTISTS, holder.selectedSearchFilter.value)
        assertEquals(nightArtists, holder.searchResults.value)
        verify(exactly = 1) { musicRepository.searchAll("night", SearchFilterType.ARTISTS) }
        owner.cancel()
    }

    @Test
    fun `the previous filter's results stop reaching the screen after a filter change`() = runTest {
        val allMatches = MutableStateFlow<List<SearchResultItem>>(nightSongs + nightAlbums)
        every { musicRepository.searchAll("night", SearchFilterType.ALL) } returns allMatches
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()
        holder.performSearch("night")
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.SONGS)
        runCurrent()
        // A library change re-emits the All query; it must not replace the Songs results.
        allMatches.value = nightSongs + nightAlbums + nightArtists
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        assertEquals(nightSongs, holder.searchResults.value)
        owner.cancel()
    }

    @Test
    fun `a filter change while the query is still debouncing searches that query with the new filter`() = runTest {
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()
        holder.performSearch("night")
        advanceTimeBy(100L)
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.ALBUMS)
        runCurrent()

        assertEquals(nightAlbums, holder.searchResults.value)
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()
        verify(exactly = 1) { musicRepository.searchAll("night", SearchFilterType.ALBUMS) }
        verify(exactly = 0) { musicRepository.searchAll(any(), SearchFilterType.ALL) }
        owner.cancel()
    }

    @Test
    fun `selecting the filter that is already selected does not search again`() = runTest {
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()
        holder.performSearch("night")
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.ALL)
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        verify(exactly = 1) { musicRepository.searchAll("night", any()) }
        owner.cancel()
    }

    @Test
    fun `changing the filter before typing a query does not search`() = runTest {
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.ALBUMS)
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        verify(exactly = 0) { musicRepository.searchAll(any(), any()) }
        assertTrue(holder.searchResults.value.isEmpty())
        owner.cancel()
    }

    @Test
    fun `a query typed after picking a filter searches with that filter`() = runTest {
        val owner = ownerScope()
        val holder = SearchStateHolder(musicRepository).apply { initialize(owner) }
        runCurrent()

        holder.updateSearchFilter(SearchFilterType.ALBUMS)
        holder.performSearch("night")
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        assertEquals(nightAlbums, holder.searchResults.value)
        verify(exactly = 0) { musicRepository.searchAll(any(), SearchFilterType.ALL) }
        owner.cancel()
    }

    @Test
    fun `the search resumed for the surviving owner keeps its filter`() = runTest {
        val holder = SearchStateHolder(musicRepository)
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        holder.initialize(mainActivityOwner)
        holder.initialize(externalPlayerOwner)
        runCurrent()
        holder.performSearch("night")
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()
        holder.updateSearchFilter(SearchFilterType.ARTISTS)
        runCurrent()

        externalPlayerOwner.cancel()
        holder.onCleared(externalPlayerOwner)
        advanceTimeBy(SEARCH_SETTLE_MS)
        runCurrent()

        assertEquals(nightArtists, holder.searchResults.value)
        // Once for the first owner, once replayed for the surviving one.
        verify(exactly = 2) { musicRepository.searchAll("night", SearchFilterType.ARTISTS) }
        mainActivityOwner.cancel()
    }

    // Separate Job so each owner has its own identity, while still running on the test
    // dispatcher that runCurrent and advanceTimeBy drive.
    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    private fun song(id: String) = Song(
        id = id,
        title = id,
        artist = "Aurora Lane",
        artistId = 1L,
        album = "Coastal Lights",
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )

    private fun album(id: Long, title: String) = Album(
        id = id,
        title = title,
        artist = "Aurora Lane",
        year = 2021,
        dateAdded = 0L,
        albumArtUriString = null,
        songCount = 2
    )

    private companion object {
        /** Past SearchStateHolder's 300 ms typing debounce. */
        const val SEARCH_SETTLE_MS = 500L
    }
}
