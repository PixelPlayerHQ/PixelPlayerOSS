package com.lostf1sh.pixelplayeross.presentation.selection

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailSongSelectionTest {
    @Test
    fun `range follows displayed positions regardless of tap order`() {
        val selection = DetailSongSelection<String>()
        val visible = listOf("before", "first", "middle", "last", "after")
        selection.toggle("last")
        selection.toggle("first")

        val range = checkNotNull(selection.rangeIn(visible))
        selection.selectAll(range)

        assertEquals(listOf("first", "middle", "last"), selection.keys.toList())
    }

    @Test
    fun `select all replaces tap order even when every row was already selected`() {
        val selection = DetailSongSelection<String>()
        selection.toggle("second")
        selection.toggle("first")

        selection.selectAll(listOf("first", "second"))

        assertEquals(listOf("first", "second"), selection.keys.toList())
    }

    @Test
    fun `collapsed sections neither provide an endpoint nor contribute songs`() {
        val selection = DetailSongSelection<String>()
        val visible = listOf("first", "second", "third")
        selection.toggle("collapsed")
        selection.toggle("third")

        assertNull(selection.rangeIn(visible))

        selection.toggle("first")
        selection.keepVisible(visible.toSet())

        assertEquals(listOf("first", "second", "third"), selection.rangeIn(visible))
        assertEquals(listOf("third", "first"), selection.keys.toList())
    }

    @Test
    fun `duplicate playlist tracks use the selected occurrence as the range endpoint`() {
        val selection = DetailSongSelection<Pair<Int, String>>()
        val duplicate = Song.emptySong().copy(id = "duplicate")
        val unrelated = Song.emptySong().copy(id = "unrelated")
        val last = Song.emptySong().copy(id = "last")
        val visible = linkedMapOf(
            (0 to duplicate.id) to duplicate,
            (1 to unrelated.id) to unrelated,
            (2 to duplicate.id) to duplicate,
            (3 to last.id) to last
        )
        selection.toggle(2 to duplicate.id)
        selection.toggle(3 to last.id)

        val range = checkNotNull(selection.rangeIn(visible.keys.toList()))
        selection.selectAll(range)
        assertEquals(listOf(2 to duplicate.id, 3 to last.id), selection.keys.toList())
        assertEquals(listOf(duplicate, last), selection.songsIn(visible))

        selection.selectAll(visible.keys)
        assertEquals(listOf(duplicate, unrelated, duplicate, last), selection.songsIn(visible))
    }

    @Test
    fun `an asynchronous action clears only the selection it started with`() {
        val selection = DetailSongSelection<String>()
        selection.toggle("old")
        val completeOldAction = selection.completionCallback()
        selection.clear()
        selection.toggle("new")

        completeOldAction()

        assertTrue(selection.isActive)
        assertEquals(setOf("new"), selection.keys)
        selection.completionCallback().invoke()
        assertFalse(selection.isActive)
    }
}
