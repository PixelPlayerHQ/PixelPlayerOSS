package com.lostf1sh.pixelplayeross.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPickerSelectionTest {

    @Test
    fun `a single song can be removed from all of its playlists`() {
        val selection = PlaylistPickerSelection(
            initialPlaylistIds = setOf("one", "two"),
            edits = mapOf("one" to false, "two" to false),
            songCount = 1
        )

        assertTrue(selection.canSave)
        assertTrue(selection.selectedPlaylistIds.isEmpty())
    }

    @Test
    fun `playlists outside the current search keep their membership when saving edits`() {
        val selection = PlaylistPickerSelection(
            initialPlaylistIds = setOf("hidden", "visible"),
            edits = mapOf("visible" to false, "new" to true),
            songCount = 1
        )

        assertTrue(selection.canSave)
        assertEquals(setOf("hidden", "new"), selection.selectedPlaylistIds)
    }

    @Test
    fun `a single song with unchanged membership cannot be saved`() {
        val selection = PlaylistPickerSelection(setOf("one"), emptyMap(), songCount = 1)

        assertFalse(selection.canSave)
    }

    @Test
    fun `a playlist arriving after the picker opens keeps its membership`() {
        val edits = mapOf("chosen" to true)
        val beforeLoad = PlaylistPickerSelection(emptySet(), edits, songCount = 1)
        val afterLoad = PlaylistPickerSelection(setOf("arrived"), edits, songCount = 1)

        assertEquals(setOf("chosen"), beforeLoad.selectedPlaylistIds)
        assertEquals(setOf("arrived", "chosen"), afterLoad.selectedPlaylistIds)
    }

    @Test
    fun `adding multiple songs requires at least one selected playlist`() {
        assertFalse(PlaylistPickerSelection(emptySet(), emptyMap(), songCount = 2).canSave)
        assertTrue(PlaylistPickerSelection(emptySet(), mapOf("one" to true), songCount = 2).canSave)
    }
}
