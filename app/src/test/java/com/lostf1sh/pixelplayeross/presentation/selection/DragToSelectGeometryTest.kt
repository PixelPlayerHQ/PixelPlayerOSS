package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DragToSelectGeometryTest {

    // Rows 100px tall with 10px gaps, starting with row 4 scrolled 30px out of view.
    private val visibleItems = listOf(
        item(index = 4, offset = -30, size = 100),
        item(index = 5, offset = 80, size = 100),
        item(index = 6, offset = 190, size = 100),
    )

    @Test
    fun `itemIndexAtY finds the row under the finger`() {
        assertEquals(4, itemIndexAtY(y = 0f, viewportStartOffset = 0, visibleItems = visibleItems))
        assertEquals(5, itemIndexAtY(y = 80f, viewportStartOffset = 0, visibleItems = visibleItems))
        assertEquals(6, itemIndexAtY(y = 289f, viewportStartOffset = 0, visibleItems = visibleItems))
    }

    @Test
    fun `itemIndexAtY accounts for the list's top content padding`() {
        // With 120px of top padding the viewport starts at -120, so y = 120 is the content start.
        assertEquals(4, itemIndexAtY(y = 160f, viewportStartOffset = -120, visibleItems = visibleItems))
        assertEquals(5, itemIndexAtY(y = 200f, viewportStartOffset = -120, visibleItems = visibleItems))
    }

    @Test
    fun `itemIndexAtY returns null in the gap between rows`() {
        assertNull(itemIndexAtY(y = 75f, viewportStartOffset = 0, visibleItems = visibleItems))
    }

    @Test
    fun `itemIndexAtY clamps above the first and below the last visible row`() {
        assertEquals(4, itemIndexAtY(y = -500f, viewportStartOffset = 0, visibleItems = visibleItems))
        assertEquals(6, itemIndexAtY(y = 2_000f, viewportStartOffset = 0, visibleItems = visibleItems))
        assertNull(itemIndexAtY(y = 10f, viewportStartOffset = 0, visibleItems = emptyList()))
    }

    @Test
    fun `auto-scroll is still in the middle and scrolls towards the edge the finger is near`() {
        val velocity = { y: Float ->
            dragSelectAutoScrollVelocity(y, bandTop = 100f, bandBottom = 1_100f, edgeZone = 100f, maxVelocity = 1_000f)
        }

        assertEquals(0f, velocity(600f))
        assertEquals(0f, velocity(200f))
        assertTrue(velocity(150f) < 0f)
        assertTrue(velocity(1_050f) > 0f)
    }

    @Test
    fun `auto-scroll speeds up deeper in the zone and is capped past the edge`() {
        val velocity = { y: Float ->
            dragSelectAutoScrollVelocity(y, bandTop = 100f, bandBottom = 1_100f, edgeZone = 100f, maxVelocity = 1_000f)
        }

        assertTrue(velocity(1_090f) > velocity(1_050f))
        assertEquals(1_000f, velocity(1_100f))
        // Over the mini player, below the visible band.
        assertEquals(1_000f, velocity(1_300f))
        assertEquals(-1_000f, velocity(0f))
        // Just inside a zone it already moves, at the minimum speed.
        assertEquals(150f, velocity(1_001f), 0.001f)
    }

    @Test
    fun `a short list keeps a still middle band`() {
        val velocity = { y: Float ->
            dragSelectAutoScrollVelocity(y, bandTop = 0f, bandBottom = 200f, edgeZone = 100f, maxVelocity = 1_000f)
        }

        assertEquals(0f, velocity(100f))
        assertTrue(velocity(10f) < 0f)
        assertTrue(velocity(190f) > 0f)
        assertEquals(0f, dragSelectAutoScrollVelocity(50f, bandTop = 100f, bandBottom = 100f, edgeZone = 100f, maxVelocity = 1_000f))
    }

    @Test
    fun `an edge zone the drag starts in waits until the finger leaves it or heads for the edge`() {
        val gate = AutoScrollEdgeGate(startY = 150f, touchSlop = 10f)

        // The long-pressed row sits in the top zone: holding still or jittering does not scroll.
        assertEquals(0f, gate.filter(velocity = -300f, y = 150f))
        assertEquals(0f, gate.filter(velocity = -300f, y = 145f))
        // Moving up past the touch slop opens the zone.
        assertEquals(-400f, gate.filter(velocity = -400f, y = 130f))
    }

    @Test
    fun `an edge zone opens once the finger has been outside it`() {
        val gate = AutoScrollEdgeGate(startY = 150f, touchSlop = 10f)

        assertEquals(0f, gate.filter(velocity = -300f, y = 155f))
        assertEquals(0f, gate.filter(velocity = 0f, y = 400f))
        assertEquals(-300f, gate.filter(velocity = -300f, y = 155f))
    }

    @Test
    fun `a drag started outside the zones scrolls as soon as the finger reaches one`() {
        val gate = AutoScrollEdgeGate(startY = 600f, touchSlop = 10f)

        assertEquals(0f, gate.filter(velocity = 0f, y = 600f))
        assertEquals(500f, gate.filter(velocity = 500f, y = 1_050f))
        assertEquals(-500f, gate.filter(velocity = -500f, y = 120f))
    }

    @Test
    fun `the drag starts from the long-pressed row even when the list moved another row under the finger`() {
        val itemAt = { index: Int -> "song-$index" }

        // The finger is over row 6 now, but row 5 was long-pressed.
        assertEquals(5, anchorIndexOf("song-5", y = 250f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
        assertEquals(4, anchorIndexOf("song-4", y = 250f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
    }

    @Test
    fun `a long-pressed item shown twice anchors on the copy nearest the finger`() {
        val itemAt = { index: Int -> if (index == 4 || index == 6) "twice" else "song-$index" }

        assertEquals(4, anchorIndexOf("twice", y = 60f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
        assertEquals(6, anchorIndexOf("twice", y = 200f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
        // With 120px of top padding, y = 200 is 80px into the content: on row 5, nearer row 4.
        assertEquals(4, anchorIndexOf("twice", y = 200f, viewportStartOffset = -120, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
    }

    @Test
    fun `a long-pressed item replaced by an updated copy still anchors on its row`() {
        // The song's details loaded after the long press: same id, new item.
        val itemAt = { index: Int -> "song-$index#loaded" }
        val itemKey = { item: String -> item.substringBefore('#') }

        assertEquals(
            5,
            anchorIndexOf("song-5", y = 100f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = itemKey),
        )
    }

    @Test
    fun `no anchor when the long-pressed item is not on screen or not selectable`() {
        val itemAt = { index: Int -> if (index == 5) null else "song-$index" }

        assertNull(anchorIndexOf("song-9", y = 100f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
        assertNull(anchorIndexOf("song-5", y = 100f, viewportStartOffset = 0, visibleItems = visibleItems, itemAt = itemAt, itemKey = { it }))
        assertNull(anchorIndexOf("song-5", y = 100f, viewportStartOffset = 0, visibleItems = emptyList(), itemAt = itemAt, itemKey = { it }))
    }

    @Test
    fun `the drag range waits for the finger to move past the touch slop`() {
        val gate = DragRangeGate(startY = 1_000f, touchSlop = 20f)

        // A list that slid under the finger leaves it where it was on screen; a wiggle stays shut.
        assertFalse(gate.onPointerMoved(1_000f))
        assertFalse(gate.onPointerMoved(1_002f))
        assertFalse(gate.onPointerMoved(980f))
        assertTrue(gate.onPointerMoved(1_021f))
        // Once the finger has moved, the range follows it back over the long-pressed row too.
        assertTrue(gate.onPointerMoved(1_000f))
    }

    @Test
    fun `the drag range opens upwards and for auto-scroll`() {
        val upwards = DragRangeGate(startY = 1_000f, touchSlop = 20f)
        assertTrue(upwards.onPointerMoved(979f))

        val autoScrolled = DragRangeGate(startY = 1_000f, touchSlop = 20f)
        autoScrolled.open()
        assertTrue(autoScrolled.isOpen)
        assertTrue(autoScrolled.onPointerMoved(1_000f))
    }

    private fun item(index: Int, offset: Int, size: Int): LazyListItemInfo = object : LazyListItemInfo {
        override val index = index
        override val key: Any = index
        override val offset = offset
        override val size = size
    }
}
