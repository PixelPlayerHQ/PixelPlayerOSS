package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.SearchFilterType
import com.lostf1sh.pixelplayeross.data.model.SearchResultItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search results open at the top when another filter is picked or their sections change, and keep
 * their scroll position when the same sections come back under the same filter: after a library
 * update, or when returning from an album or artist opened from the results.
 */
@RunWith(AndroidJUnit4::class)
class SearchResultsListScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val songsHeader = context.getString(R.string.library_tab_songs)
    private val albumsHeader = context.getString(R.string.library_tab_albums)

    private val playerViewModel = mockk<PlayerViewModel>(relaxed = true) {
        every { stablePlayerState } returns MutableStateFlow(StablePlayerState())
    }

    private val songItems: List<SearchResultItem> = (1..SONG_COUNT).map { index ->
        SearchResultItem.SongItem(
            Song.emptySong().copy(id = "song-$index", title = songTitle(index), artist = ARTIST)
        )
    }
    private val albumItems: List<SearchResultItem> = (1..ALBUM_COUNT).map(::albumItem)

    /** The All filter: a long Songs section with the Albums section below it. */
    private val allResults: ImmutableList<SearchResultItem> = (songItems + albumItems).toImmutableList()

    /** The Albums filter: the same albums with nothing above them. */
    private val albumResults: ImmutableList<SearchResultItem> = albumItems.toImmutableList()

    private var filter by mutableStateOf(SearchFilterType.ALL)
    private var results by mutableStateOf(allResults)
    private var showResults by mutableStateOf(true)

    private fun setResultsList() {
        composeRule.setContent {
            // Mirrors NavHost, which keeps the Search entry's saved state in a holder while an
            // album or artist opened from the results covers it and disposes its composition.
            val stateHolder = rememberSaveableStateHolder()
            val navController = rememberNavController()
            MaterialTheme {
                if (showResults) {
                    stateHolder.SaveableStateProvider(SEARCH_ENTRY_KEY) {
                        SearchResultsList(
                            results = results,
                            searchQuery = QUERY,
                            selectedFilter = filter,
                            playerViewModel = playerViewModel,
                            onItemSelected = {},
                            currentPlayingSongId = null,
                            isPlaying = false,
                            onSongMoreOptionsClick = {},
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    /** Scrolls the list until the album with [index] is the first visible row. */
    private fun scrollToAlbum(index: Int) {
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("album_$index")
        composeRule.onNodeWithText(albumTitle(index)).assertIsDisplayed()
        composeRule.onNodeWithText(songsHeader).assertIsNotDisplayed()
        composeRule.onNodeWithText(albumsHeader).assertIsNotDisplayed()
    }

    /** Scrolls the list until the song with [index] is the first visible row. */
    private fun scrollToSong(index: Int) {
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("song_song-$index")
        composeRule.onNodeWithText(songTitle(index)).assertIsDisplayed()
        composeRule.onNodeWithText(songsHeader).assertIsNotDisplayed()
    }

    /**
     * Picks the [newFilter] chip the way the screen sees it: the selected filter changes first,
     * and the query re-run with it delivers [newResults] afterwards.
     */
    private fun selectFilter(newFilter: SearchFilterType, newResults: ImmutableList<SearchResultItem>) {
        composeRule.runOnIdle { filter = newFilter }
        composeRule.runOnIdle { results = newResults }
    }

    @Test
    fun switchingFromAlbumsBackToAllOpensAtTheSongsHeader() {
        filter = SearchFilterType.ALBUMS
        results = albumResults
        setResultsList()
        composeRule.onNodeWithText(albumsHeader).assertIsDisplayed()

        selectFilter(SearchFilterType.ALL, allResults)

        composeRule.onNodeWithText(songsHeader).assertIsDisplayed()
        composeRule.onNodeWithText(songTitle(1)).assertIsDisplayed()
        composeRule.onNodeWithText(albumsHeader).assertIsNotDisplayed()
    }

    @Test
    fun switchingFromScrolledAllToAlbumsOpensAtTheAlbumsHeader() {
        setResultsList()
        scrollToAlbum(5)

        selectFilter(SearchFilterType.ALBUMS, albumResults)

        composeRule.onNodeWithText(albumsHeader).assertIsDisplayed()
        composeRule.onNodeWithText(albumTitle(1)).assertIsDisplayed()
    }

    @Test
    fun switchingFiltersWithTheSameSectionsOpensAtTheTop() {
        // A query that only matches song titles returns just the Songs section under Songs and All.
        filter = SearchFilterType.SONGS
        results = songItems.toImmutableList()
        setResultsList()
        scrollToSong(SONG_COUNT / 2)

        selectFilter(SearchFilterType.ALL, songItems.toImmutableList())

        composeRule.onNodeWithText(songsHeader).assertIsDisplayed()
        composeRule.onNodeWithText(songTitle(1)).assertIsDisplayed()
    }

    @Test
    fun libraryUpdateWithTheSameSectionsKeepsTheScrollPosition() {
        setResultsList()
        scrollToAlbum(5)

        composeRule.runOnIdle {
            results = (songItems + albumItems + albumItem(ALBUM_COUNT + 1)).toImmutableList()
        }

        composeRule.onNodeWithText(albumTitle(5)).assertIsDisplayed()
        composeRule.onNodeWithText(songsHeader).assertIsNotDisplayed()
    }

    @Test
    fun returningFromAnOpenedResultKeepsTheScrollPosition() {
        setResultsList()
        scrollToAlbum(5)

        // Open an album from the results, then come back.
        composeRule.runOnIdle { showResults = false }
        composeRule.onNodeWithText(albumTitle(5)).assertDoesNotExist()
        composeRule.runOnIdle { showResults = true }

        composeRule.onNodeWithText(albumTitle(5)).assertIsDisplayed()
        composeRule.onNodeWithText(songsHeader).assertIsNotDisplayed()
    }

    private fun albumItem(index: Int): SearchResultItem = SearchResultItem.AlbumItem(
        Album.empty().copy(
            id = index.toLong(),
            title = albumTitle(index),
            artist = ARTIST,
            songCount = 3
        )
    )

    private fun albumTitle(index: Int) = "Record $index"

    private fun songTitle(index: Int) = "Track $index"

    private companion object {
        const val QUERY = "test"
        const val ARTIST = "Test Artist"
        const val SEARCH_ENTRY_KEY = "search"

        // Both sections are taller than the screen, so the list can scroll under either filter.
        const val SONG_COUNT = 30
        const val ALBUM_COUNT = 20
    }
}
