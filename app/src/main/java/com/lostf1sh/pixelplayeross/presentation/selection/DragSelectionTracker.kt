package com.lostf1sh.pixelplayeross.presentation.selection

import java.util.TreeSet
import kotlin.math.sign

/** The selection changes produced by one drag step, in the order the finger reached the rows. */
internal data class DragSelectionChange<T>(
    val select: List<T>,
    val deselect: List<T>,
) {
    val isEmpty: Boolean get() = select.isEmpty() && deselect.isEmpty()
}

/**
 * Follows a drag-to-select gesture over a list, Google Photos style.
 *
 * Every row between the long-pressed [anchorIndex] and the finger takes the anchor's new state
 * ([selecting]). A row the range leaves again goes back to the state it had before the drag
 * reached it, so dragging back undoes the overshoot. The anchor itself is never touched: the long
 * press has already toggled it.
 *
 * Items are cached when the range first reaches them, so a row can still be restored after its
 * page has been dropped from memory. Rows without an item yet (paging placeholders) are retried on
 * every later step while they stay in the range; rows that never resolve to an item (headers,
 * folders) are simply skipped.
 *
 * The range is kept by row index, so it only holds while the rows stay where the drag found them;
 * check [isInPlace] before every [moveTo].
 *
 * @param anchorKey [itemKey] of the long-pressed item shown at [anchorIndex].
 * @param itemKey A stable identity for an item, one that an updated copy of it keeps.
 */
internal class DragSelectionTracker<T>(
    val anchorIndex: Int,
    private val anchorKey: Any,
    val selecting: Boolean,
    private val itemKey: (T) -> Any,
) {
    private class Entry<T>(val item: T, val key: Any, val wasSelected: Boolean)

    /** The row the finger is on; the range covers [anchorIndex] up to and including it. */
    var currentIndex: Int = anchorIndex
        private set

    private val entered = HashMap<Int, Entry<T>>()
    private val unresolved = TreeSet<Int>()

    /**
     * Moves the range's far end to [index] and returns what has to change. Calling it again with
     * the same index retries rows whose items were not available on an earlier step.
     */
    fun moveTo(
        index: Int,
        itemAt: (Int) -> T?,
        isSelected: (T) -> Boolean,
    ): DragSelectionChange<T> {
        val previous = currentIndex
        currentIndex = index
        val select = ArrayList<T>()
        val deselect = ArrayList<T>()

        for (leaving in coveredOnlyBy(previous, other = index)) {
            unresolved.remove(leaving)
            val entry = entered[leaving] ?: continue
            if (entry.wasSelected != selecting) {
                if (entry.wasSelected) select += entry.item else deselect += entry.item
            }
        }

        for (entering in coveredOnlyBy(index, other = previous)) {
            unresolved += entering
        }
        // Walk from the anchor outwards so the selection order follows the finger.
        val pending = if (index >= anchorIndex) unresolved.iterator() else unresolved.descendingIterator()
        while (pending.hasNext()) {
            val row = pending.next()
            val entry = entered[row] ?: itemAt(row)?.let { item ->
                Entry<T>(item, itemKey(item), isSelected(item)).also { entered[row] = it }
            } ?: continue
            pending.remove()
            if (entry.wasSelected != selecting) {
                if (selecting) select += entry.item else deselect += entry.item
            }
        }

        return DragSelectionChange(select, deselect)
    }

    /**
     * Whether the anchor and every row the drag has reached still show the items the drag found
     * there. Rows that change order or shift under the drag (a folder's songs re-sorting as their
     * details load, a rescan adding a song above) would leave the range covering, restoring and
     * skipping the wrong items, so the drag cannot go on once this is false.
     *
     * Rows are matched by [itemKey], so an item replaced by an updated copy of itself is still in
     * place. A row that shows no item now (a dropped page) cannot tell and counts as in place.
     */
    fun isInPlace(itemAt: (Int) -> T?): Boolean =
        rowShows(anchorIndex, anchorKey, itemAt) &&
            entered.all { (row, entry) -> rowShows(row, entry.key, itemAt) }

    private fun rowShows(row: Int, key: Any, itemAt: (Int) -> T?): Boolean {
        val item = itemAt(row) ?: return true
        return itemKey(item) == key
    }

    /**
     * The rows the range ending at [end] covers and the one ending at [other] does not, walked from
     * the anchor outwards. Both ranges contain the anchor, so this is always one contiguous run.
     */
    private fun coveredOnlyBy(end: Int, other: Int): IntProgression {
        val direction = (end - anchorIndex).sign
        if (direction == 0) return IntRange.EMPTY
        val start = if ((other - anchorIndex).sign == direction) other + direction else anchorIndex + direction
        return IntProgression.fromClosedRange(start, end, direction)
    }
}
