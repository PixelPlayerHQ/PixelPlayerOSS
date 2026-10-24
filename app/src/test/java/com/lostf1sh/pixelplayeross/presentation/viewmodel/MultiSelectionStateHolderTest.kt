package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `applyDragSelection appends new songs in drag order and removes deselected ones`() {
        val holder = MultiSelectionStateHolder()
        val anchor = song("anchor")
        val alreadySelected = song("kept")
        val first = song("1")
        val second = song("2")
        holder.toggleSelection(alreadySelected)
        holder.toggleSelection(anchor)

        holder.applyDragSelection(select = listOf(first, alreadySelected, second), deselect = emptyList())

        assertEquals(listOf(alreadySelected, anchor, first, second), holder.selectedSongs.value)
        assertEquals(4, holder.selectedCount.value)

        holder.applyDragSelection(select = emptyList(), deselect = listOf(first, alreadySelected))

        assertEquals(listOf(anchor, second), holder.selectedSongs.value)
        assertEquals(setOf("anchor", "2"), holder.selectedSongIds.value)
        assertEquals(2, holder.selectedCount.value)
        assertTrue(holder.isSelectionMode.value)
        assertEquals(2, holder.getSelectionIndex("2"))
    }

    @Test
    fun `applyDragSelection leaves selection mode when the drag deselects everything`() {
        val holder = MultiSelectionStateHolder()
        val only = song("only")
        holder.toggleSelection(only)

        holder.applyDragSelection(select = emptyList(), deselect = listOf(only))

        assertTrue(holder.selectedSongs.value.isEmpty())
        assertEquals(0, holder.selectedCount.value)
        assertFalse(holder.isSelectionMode.value)
    }

    @Test
    fun `dragging a deselecting drag back restores songs to their old place in the order`() {
        val holder = MultiSelectionStateHolder()
        val (a, b, c, d) = listOf(song("A"), song("B"), song("C"), song("D"))
        listOf(a, b, c, d).forEach(holder::toggleSelection)
        // The long press on A deselects it, so the drag that follows deselects too.
        holder.toggleSelection(a)

        holder.applyDragSelection(select = emptyList(), deselect = listOf(b, c))
        assertEquals(listOf(d), holder.selectedSongs.value)

        holder.applyDragSelection(select = listOf(c), deselect = emptyList())
        assertEquals(listOf(c, d), holder.selectedSongs.value)

        holder.applyDragSelection(select = listOf(b), deselect = emptyList())
        assertEquals(listOf(b, c, d), holder.selectedSongs.value)
        assertEquals(1, holder.getSelectionIndex("B"))
        assertEquals(3, holder.getSelectionIndex("D"))
    }

    @Test
    fun `a selecting drag that is dragged back and forth keeps the songs it adds after the old ones`() {
        val holder = MultiSelectionStateHolder()
        val (kept, anchor, first, second) = listOf(song("kept"), song("anchor"), song("1"), song("2"))
        holder.toggleSelection(kept)
        holder.toggleSelection(anchor)

        holder.applyDragSelection(select = listOf(first, second), deselect = emptyList())
        holder.applyDragSelection(select = emptyList(), deselect = listOf(second))
        holder.applyDragSelection(select = listOf(second), deselect = emptyList())

        assertEquals(listOf(kept, anchor, first, second), holder.selectedSongs.value)
    }

    @Test
    fun `a new drag appends songs an earlier drag deselected`() {
        val holder = MultiSelectionStateHolder()
        val (a, b, c, d) = listOf(song("A"), song("B"), song("C"), song("D"))
        listOf(a, b, c).forEach(holder::toggleSelection)
        holder.applyDragSelection(select = emptyList(), deselect = listOf(a))

        // The next drag starts with a long press on D, which selects it.
        holder.toggleSelection(d)
        holder.applyDragSelection(select = listOf(a), deselect = emptyList())

        assertEquals(listOf(b, c, d, a), holder.selectedSongs.value)
    }

    @Test
    fun `applyDragSelection does not emit when nothing changes`() {
        val holder = MultiSelectionStateHolder()
        val selected = song("selected")
        holder.toggleSelection(selected)
        val before = holder.selectedSongs.value

        holder.applyDragSelection(select = listOf(selected), deselect = listOf(song("not selected")))

        assertSame(before, holder.selectedSongs.value)
    }

    private fun song(id: String) = Song.emptySong().copy(id = id, title = "Song $id")
}
