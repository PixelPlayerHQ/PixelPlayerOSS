package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.presentation.library.LibraryTabId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReorderTabsSheetLogicTest {

    @Test
    fun `hidden tabs are the known tabs missing from the visible list, in default order`() {
        val hidden = hiddenLibraryTabs(listOf("LIKED", "SONGS"))

        assertEquals(
            listOf(
                LibraryTabId.Albums,
                LibraryTabId.Artists,
                LibraryTabId.Playlists,
                LibraryTabId.Folders,
                LibraryTabId.Genres
            ),
            hidden
        )
    }

    @Test
    fun `an order saved before the Genres tab existed lists Genres as hidden`() {
        val hidden = hiddenLibraryTabs(listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED"))

        assertEquals(listOf(LibraryTabId.Genres), hidden)
    }

    @Test
    fun `no tab is hidden when every tab is visible`() {
        val hidden = hiddenLibraryTabs(LibraryTabId.entries.map { it.stableKey })

        assertTrue(hidden.isEmpty())
    }

    @Test
    fun `unknown visible keys do not count as known tabs`() {
        val hidden = hiddenLibraryTabs(listOf("SONGS", "UNKNOWN"))

        assertEquals(LibraryTabId.entries - LibraryTabId.Songs, hidden)
    }

    @Test
    fun `moving by key reorders visible tabs in both directions`() {
        val tabs = listOf("SONGS", "ALBUMS", "ARTIST")

        assertEquals(listOf("ARTIST", "SONGS", "ALBUMS"), moveTabByKey(tabs, "ARTIST", "SONGS"))
        assertEquals(listOf("ALBUMS", "ARTIST", "SONGS"), moveTabByKey(tabs, "SONGS", "ARTIST"))
    }

    @Test
    fun `moving onto a section header or hidden row is ignored`() {
        val tabs = listOf("SONGS", "ALBUMS")

        assertNull(moveTabByKey(tabs, "SONGS", "visible_header"))
        assertNull(moveTabByKey(tabs, "ALBUMS", "hidden_header"))
        assertNull(moveTabByKey(tabs, "SONGS", "hidden_FOLDERS"))
        assertNull(moveTabByKey(tabs, "hidden_FOLDERS", "SONGS"))
    }

    @Test
    fun `moving a tab onto itself or with a non-string key is ignored`() {
        val tabs = listOf("SONGS", "ALBUMS")

        assertNull(moveTabByKey(tabs, "SONGS", "SONGS"))
        assertNull(moveTabByKey(tabs, 1, "ALBUMS"))
        assertNull(moveTabByKey(tabs, "SONGS", null))
    }
}
