package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.lazy.LazyListItemInfo
import kotlin.math.abs

/**
 * The index of the visible item under [y], a position relative to the top of the list's bounds.
 *
 * Item offsets are measured from the end of the list's top content padding, which
 * [viewportStartOffset] (minus that padding) accounts for. A position in the gap between two
 * items is not on any item and returns null; one above the first or below the last visible item
 * returns that item, so a finger past the end of a short list still reaches its last row.
 */
internal fun itemIndexAtY(
    y: Float,
    viewportStartOffset: Int,
    visibleItems: List<LazyListItemInfo>,
): Int? {
    if (visibleItems.isEmpty()) return null
    val contentY = y + viewportStartOffset
    val first = visibleItems.first()
    if (contentY < first.offset) return first.index
    val last = visibleItems.last()
    if (contentY >= last.offset + last.size) return last.index
    return visibleItems.firstOrNull { contentY >= it.offset && contentY < it.offset + it.size }?.index
}

/**
 * The index of the visible row showing [item], the long-pressed row a drag-select starts from.
 *
 * The row is found by its item rather than by the finger's position: entering selection mode can
 * move the list under a finger that has not moved (a header changing height), leaving the finger
 * over another row. Items are matched by [itemKey], so a row whose item has since been replaced by
 * an updated copy (a song whose details have loaded) is still found. If [item] shows more than
 * once, the copy nearest [y] (relative to the top of the list's bounds, see [itemIndexAtY]) wins.
 * Returns null when no visible row shows [item].
 */
internal fun <T : Any> anchorIndexOf(
    item: T,
    y: Float,
    viewportStartOffset: Int,
    visibleItems: List<LazyListItemInfo>,
    itemAt: (index: Int) -> T?,
    itemKey: (T) -> Any,
): Int? {
    val contentY = y + viewportStartOffset
    val key = itemKey(item)
    return visibleItems
        .filter { row -> itemAt(row.index)?.let(itemKey) == key }
        .minByOrNull { row ->
            when {
                contentY < row.offset -> row.offset - contentY
                contentY >= row.offset + row.size -> contentY - (row.offset + row.size)
                else -> 0f
            }
        }
        ?.index
}

/**
 * Holds a drag-select on its long-pressed row until the finger has really moved: by more than
 * [touchSlop] from [startY], where it was when the long press armed the drag, in coordinates that
 * do not move with the list (the window's). Auto-scroll can also [open] it, since it moves rows
 * under the finger on purpose.
 *
 * Without it, a list that shifts under a still finger as selection mode starts would put the next
 * row under the finger, and the slightest wiggle would select that row too.
 */
internal class DragRangeGate(
    private val startY: Float,
    private val touchSlop: Float,
) {
    var isOpen = false
        private set

    /** Notes the finger at [y]; returns whether the drag range may follow it now. */
    fun onPointerMoved(y: Float): Boolean {
        if (abs(y - startY) > touchSlop) isOpen = true
        return isOpen
    }

    fun open() {
        isOpen = true
    }
}

/**
 * Auto-scroll speed in pixels per second for a finger at [y] while drag-selecting: negative inside
 * the top edge zone of the visible band [bandTop, bandBottom], positive inside the bottom one, and
 * zero in between. The speed grows with how deep the finger is in the zone and reaches
 * [maxVelocity] at the band's edge and beyond it, e.g. over the mini player.
 */
internal fun dragSelectAutoScrollVelocity(
    y: Float,
    bandTop: Float,
    bandBottom: Float,
    edgeZone: Float,
    maxVelocity: Float,
): Float {
    val bandHeight = bandBottom - bandTop
    if (bandHeight <= 0f || edgeZone <= 0f) return 0f
    // A short list keeps a usable middle band rather than being all edge.
    val zone = minOf(edgeZone, bandHeight / 4f)
    val depth = when {
        y < bandTop + zone -> -(bandTop + zone - y) / zone
        y > bandBottom - zone -> (y - (bandBottom - zone)) / zone
        else -> return 0f
    }
    val speed = depth.coerceIn(-1f, 1f)
    return maxVelocity * if (speed < 0f) minOf(speed, -MinAutoScrollFraction) else maxOf(speed, MinAutoScrollFraction)
}

private const val MinAutoScrollFraction = 0.15f

/**
 * Keeps an edge zone from auto-scrolling just because the long-pressed row sits inside it. A zone
 * only starts scrolling once the finger has been outside it, or has moved towards its edge by
 * more than [touchSlop] since the drag started at [startY]. Finger positions are in coordinates
 * that do not move with the list (the window's), so a list shifting under a still finger does
 * not count as the finger moving.
 */
internal class AutoScrollEdgeGate(
    private val startY: Float,
    private val touchSlop: Float,
) {
    private var topOpen = false
    private var bottomOpen = false

    /** Passes [velocity] (from [dragSelectAutoScrollVelocity] at [y]) through once its zone is open. */
    fun filter(velocity: Float, y: Float): Float {
        if (velocity >= 0f || y < startY - touchSlop) topOpen = true
        if (velocity <= 0f || y > startY + touchSlop) bottomOpen = true
        return when {
            velocity < 0f && topOpen -> velocity
            velocity > 0f && bottomOpen -> velocity
            else -> 0f
        }
    }
}
