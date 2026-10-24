package com.lostf1sh.pixelplayeross.presentation.selection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DragSelectionTrackerTest {

    /**
     * A list of rows named "row0".."rowN" with a mutable selection, like the selection holder.
     * An updated copy of a row ("row3#loaded") keeps its key, like a song whose details loaded.
     */
    private class FakeList(size: Int, initiallySelected: Set<Int> = emptySet()) {
        val rows: MutableList<String?> = MutableList(size) { "row$it" }
        val selected = initiallySelected.mapTo(linkedSetOf()) { "row$it" }

        fun itemAt(index: Int): String? = rows.getOrNull(index)
        fun isSelected(item: String) = item in selected

        fun apply(change: DragSelectionChange<String>) {
            selected.removeAll(change.deselect.toSet())
            selected.addAll(change.select)
        }

        fun trackerAt(anchorIndex: Int, selecting: Boolean) = DragSelectionTracker(
            anchorIndex = anchorIndex,
            anchorKey = rowKey(rows[anchorIndex]!!),
            selecting = selecting,
            itemKey = ::rowKey,
        )
    }

    private fun DragSelectionTracker<String>.dragTo(list: FakeList, index: Int) =
        moveTo(index, list::itemAt, list::isSelected).also(list::apply)

    @Test
    fun `dragging down from the anchor selects the rows passed over in drag order`() {
        // The long press has already selected the anchor.
        val list = FakeList(size = 10, initiallySelected = setOf(3))
        val tracker = list.trackerAt(anchorIndex = 3, selecting = true)

        val change = tracker.dragTo(list, 6)

        assertEquals(listOf("row4", "row5", "row6"), change.select)
        assertTrue(change.deselect.isEmpty())
        assertEquals(listOf("row3", "row4", "row5", "row6"), list.selected.toList())
    }

    @Test
    fun `dragging back restores rows the range leaves to their state before the drag`() {
        val list = FakeList(size = 10, initiallySelected = setOf(3, 5))
        val tracker = list.trackerAt(anchorIndex = 3, selecting = true)
        tracker.dragTo(list, 6)

        val change = tracker.dragTo(list, 4)

        // Row 5 was selected before the drag and stays selected; row 6 was not.
        assertEquals(listOf("row6"), change.deselect)
        assertTrue(change.select.isEmpty())
        assertEquals(setOf("row3", "row4", "row5"), list.selected)
    }

    @Test
    fun `crossing the anchor restores one side and selects the other walking outwards`() {
        val list = FakeList(size = 10, initiallySelected = setOf(3))
        val tracker = list.trackerAt(anchorIndex = 3, selecting = true)
        tracker.dragTo(list, 6)

        val change = tracker.dragTo(list, 1)

        assertEquals(listOf("row4", "row5", "row6"), change.deselect)
        assertEquals(listOf("row2", "row1"), change.select)
        assertEquals(setOf("row3", "row2", "row1"), list.selected)
    }

    @Test
    fun `a deselecting drag clears rows and gives back the ones it leaves`() {
        // The long press deselected the anchor, so the drag deselects.
        val list = FakeList(size = 10, initiallySelected = setOf(1, 2, 4, 5))
        val tracker = list.trackerAt(anchorIndex = 3, selecting = false)

        val down = tracker.dragTo(list, 6)
        assertEquals(listOf("row4", "row5"), down.deselect)
        assertTrue(down.select.isEmpty())
        assertEquals(setOf("row1", "row2"), list.selected)

        val back = tracker.dragTo(list, 3)
        assertEquals(listOf("row4", "row5"), back.select)
        assertTrue(back.deselect.isEmpty())
        assertEquals(setOf("row1", "row2", "row4", "row5"), list.selected)
    }

    @Test
    fun `rows without an item are skipped without breaking the range`() {
        val list = FakeList(size = 10, initiallySelected = setOf(0))
        list.rows[2] = null // e.g. a folder or header row
        val tracker = list.trackerAt(anchorIndex = 0, selecting = true)

        val change = tracker.dragTo(list, 4)

        assertEquals(listOf("row1", "row3", "row4"), change.select)
        assertEquals(listOf("row0", "row1", "row3", "row4"), list.selected.toList())
    }

    @Test
    fun `a row whose item loads later is selected while it is still in range`() {
        val list = FakeList(size = 10, initiallySelected = setOf(0))
        list.rows[2] = null // a paging placeholder
        val tracker = list.trackerAt(anchorIndex = 0, selecting = true)
        tracker.dragTo(list, 3)

        list.rows[2] = "row2"
        val change = tracker.dragTo(list, 3)

        assertEquals(listOf("row2"), change.select)
        assertTrue("row2" in list.selected)
    }

    @Test
    fun `rows are restored from the cached item after their page is dropped`() {
        val list = FakeList(size = 10, initiallySelected = setOf(0))
        val tracker = list.trackerAt(anchorIndex = 0, selecting = true)
        tracker.dragTo(list, 5)

        // Paging drops the far rows while the list scrolls elsewhere.
        list.rows[4] = null
        list.rows[5] = null
        val change = tracker.dragTo(list, 2)

        assertEquals(listOf("row3", "row4", "row5"), change.deselect)
        assertEquals(setOf("row0", "row1", "row2"), list.selected)
    }

    @Test
    fun `a jump over several rows in one move selects every row skipped`() {
        val list = FakeList(size = 20, initiallySelected = setOf(10))
        val tracker = list.trackerAt(anchorIndex = 10, selecting = true)

        val change = tracker.dragTo(list, 5)

        assertEquals(listOf("row9", "row8", "row7", "row6", "row5"), change.select)
    }

    @Test
    fun `already selected rows produce no change and the anchor is never touched`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2, 3, 4))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)

        assertTrue(tracker.dragTo(list, 4).isEmpty)
        assertTrue(tracker.dragTo(list, 2).isEmpty)
        assertEquals(setOf("row2", "row3", "row4"), list.selected)
    }

    @Test
    fun `re-entering a row uses the state it had before the drag first reached it`() {
        val list = FakeList(size = 10, initiallySelected = setOf(0))
        val tracker = list.trackerAt(anchorIndex = 0, selecting = true)
        tracker.dragTo(list, 3)
        tracker.dragTo(list, 1)

        val change = tracker.dragTo(list, 3)

        assertEquals(listOf("row2", "row3"), change.select)
        tracker.dragTo(list, 0)
        assertEquals(setOf("row0"), list.selected)
    }

    @Test
    fun `rows that change order under the drag are no longer in place`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)
        tracker.dragTo(list, 5)
        assertTrue(tracker.isInPlace(list::itemAt))

        // Row 7's details load and it sorts in among the rows the drag has reached.
        list.rows.add(4, list.rows.removeAt(7))

        assertFalse(tracker.isInPlace(list::itemAt))
    }

    @Test
    fun `a row added above the drag shifts it out of place`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)
        tracker.dragTo(list, 4)

        list.rows.add(0, "new")

        assertFalse(tracker.isInPlace(list::itemAt))
    }

    @Test
    fun `the anchor moving away alone puts the drag out of place`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)

        // The range has not reached any row yet; only the anchor tells.
        list.rows[2] = "row7"
        list.rows[7] = "row2"

        assertFalse(tracker.isInPlace(list::itemAt))
    }

    @Test
    fun `rows replaced by updated copies or dropped from memory stay in place`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)
        tracker.dragTo(list, 6)

        (2..5).forEach { list.rows[it] = "row$it#loaded" }
        list.rows[6] = null

        assertTrue(tracker.isInPlace(list::itemAt))
    }

    @Test
    fun `rows past the drag that move do not put it out of place`() {
        val list = FakeList(size = 10, initiallySelected = setOf(2))
        val tracker = list.trackerAt(anchorIndex = 2, selecting = true)
        tracker.dragTo(list, 4)

        list.rows.add(6, list.rows.removeAt(9))

        assertTrue(tracker.isInPlace(list::itemAt))
    }
}

private fun rowKey(row: String): String = row.substringBefore('#')
