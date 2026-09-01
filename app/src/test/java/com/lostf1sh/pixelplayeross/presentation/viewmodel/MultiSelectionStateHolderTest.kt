package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiSelectionStateHolderTest {

    @Test
    fun `replaceSelection uses the resolved category order and removes duplicate songs`() {
        val holder = MultiSelectionStateHolder()
        val first = Song.emptySong().copy(id = "1", title = "First")
        val shared = Song.emptySong().copy(id = "2", title = "Shared")

        holder.toggleSelection(Song.emptySong().copy(id = "old", title = "Old selection"))
        holder.replaceSelection(listOf(first, shared, shared.copy(title = "Duplicate")))

        assertEquals(listOf(first, shared), holder.selectedSongs.value)
        assertEquals(setOf("1", "2"), holder.selectedSongIds.value)
        assertEquals(2, holder.selectedCount.value)
        assertTrue(holder.isSelectionMode.value)
    }
}
