package com.lostf1sh.pixelplayeross.presentation.screens

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibrarySongsTabTest {

    @Test
    fun isItemFullyVisibleInViewport_itemInMiddle_returnsTrue() {
        val result = isItemFullyVisibleInViewport(
            itemOffset = 200,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        // visible range: [100, 800], item: [200, 280]
        assertTrue(result)
    }

    @Test
    fun isItemFullyVisibleInViewport_itemExactlyAtVisibleBounds_returnsTrue() {
        val atTop = isItemFullyVisibleInViewport(
            itemOffset = 100,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertTrue(atTop)

        val atBottom = isItemFullyVisibleInViewport(
            itemOffset = 720,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertTrue(atBottom)
    }

    @Test
    fun isItemFullyVisibleInViewport_itemUnderBottomContentPadding_returnsFalse() {
        // Item starts at 750, extends to 830 (crosses into bottom 200px padding starting at 800)
        val partiallyUnderBottom = isItemFullyVisibleInViewport(
            itemOffset = 750,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(partiallyUnderBottom)

        // Item completely inside bottom padding: [850, 930]
        val fullyUnderBottom = isItemFullyVisibleInViewport(
            itemOffset = 850,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(fullyUnderBottom)
    }

    @Test
    fun isItemFullyVisibleInViewport_itemUnderTopContentPadding_returnsFalse() {
        // Item starts at 50, extends to 130 (crosses into top 100px padding ending at 100)
        val partiallyUnderTop = isItemFullyVisibleInViewport(
            itemOffset = 50,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(partiallyUnderTop)

        // Item completely inside top padding: [10, 90]
        val fullyUnderTop = isItemFullyVisibleInViewport(
            itemOffset = 10,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(fullyUnderTop)
    }

    @Test
    fun isItemFullyVisibleInViewport_itemOutsideViewport_returnsFalse() {
        val aboveViewport = isItemFullyVisibleInViewport(
            itemOffset = -150,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(aboveViewport)

        val belowViewport = isItemFullyVisibleInViewport(
            itemOffset = 1100,
            itemSize = 80,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            beforeContentPadding = 100,
            afterContentPadding = 200
        )
        assertFalse(belowViewport)
    }

    @Test
    fun isItemFullyVisibleInViewport_invalidSizesOrPaddingExceedingViewport_returnsFalse() {
        // Invalid item size
        assertFalse(
            isItemFullyVisibleInViewport(
                itemOffset = 200,
                itemSize = 0,
                viewportStartOffset = 0,
                viewportEndOffset = 1000
            )
        )

        // Padding exceeds viewport (visibleEnd <= visibleStart)
        assertFalse(
            isItemFullyVisibleInViewport(
                itemOffset = 500,
                itemSize = 80,
                viewportStartOffset = 0,
                viewportEndOffset = 500,
                beforeContentPadding = 300,
                afterContentPadding = 300
            )
        )
    }
}
