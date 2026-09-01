package com.lostf1sh.pixelplayeross.presentation.selection

import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibrarySelectionUtilsTest {

    private data class Item(val id: Long, val label: String)

    @Test
    fun `hidden local media forces online filter for category batch actions`() {
        assertEquals(
            StorageFilter.ONLINE,
            effectiveLibraryStorageFilter(StorageFilter.ALL, hideLocalMedia = true),
        )
        assertEquals(
            StorageFilter.OFFLINE,
            effectiveLibraryStorageFilter(StorageFilter.OFFLINE, hideLocalMedia = false),
        )
    }

    @Test
    fun `toggleOrderedSelection preserves selection order and removes by identity`() {
        val first = Item(1, "First")
        val second = Item(2, "Second")

        val selected = toggleOrderedSelection(emptyList(), first, Item::id)
            .let { toggleOrderedSelection(it, second, Item::id) }

        assertEquals(listOf(first, second), selected)
        assertEquals(listOf(second), toggleOrderedSelection(selected, first.copy(label = "Updated"), Item::id))
    }

    @Test
    fun `appendDistinctSelection appends only new identities`() {
        val first = Item(1, "First")
        val second = Item(2, "Second")
        val third = Item(3, "Third")

        val selected = appendDistinctSelection(
            current = listOf(first, second),
            candidates = listOf(second.copy(label = "Duplicate"), third),
            keyOf = Item::id
        )

        assertEquals(listOf(first, second, third), selected)
        assertEquals(2, selectionIndex(selected, 2L, Item::id))
        assertNull(selectionIndex(selected, 99L, Item::id))
    }

    @Test
    fun `flattenDistinctGroups keeps category and song order while removing overlaps`() {
        val first = Item(1, "First")
        val shared = Item(2, "Shared")
        val last = Item(3, "Last")

        val resolved = flattenDistinctGroups(
            groups = listOf(
                listOf(first, shared),
                listOf(shared.copy(label = "Shared duplicate"), last)
            ),
            keyOf = Item::id
        )

        assertEquals(listOf(first, shared, last), resolved)
    }
}
