package com.lostf1sh.pixelplayeross.presentation.components.scoped

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SheetOverlayStateTest {

    private val hiddenOffsetPx = 1200f

    private fun fraction(
        isQueueShown: Boolean = true,
        offsetPx: Float,
        hiddenPx: Float = hiddenOffsetPx,
        backProgress: Float = 0f
    ): Float = computeQueueVisualOpenFraction(
        isQueueShown = isQueueShown,
        queueSheetOffsetPx = offsetPx,
        queueHiddenOffsetPx = hiddenPx,
        predictiveBackProgress = backProgress
    )

    @Test
    fun queueAtHiddenOffset_doesNotCoverThePlayer() {
        assertEquals(0f, fraction(offsetPx = hiddenOffsetPx))
    }

    @Test
    fun fullyOpenQueue_coversThePlayerCompletely() {
        assertEquals(1f, fraction(offsetPx = 0f))
    }

    @Test
    fun queueDraggedHalfway_coversHalfThePlayer() {
        assertEquals(0.5f, fraction(offsetPx = hiddenOffsetPx / 2f), 0.0001f)
    }

    @Test
    fun predictiveBackHalfway_halvesTheOpenFraction() {
        assertEquals(0.5f, fraction(offsetPx = 0f, backProgress = 0.5f), 0.0001f)
        assertEquals(0.25f, fraction(offsetPx = hiddenOffsetPx / 2f, backProgress = 0.5f), 0.0001f)
    }

    @Test
    fun completedBackGesture_removesTheCoverage() {
        assertEquals(0f, fraction(offsetPx = 0f, backProgress = 1f))
    }

    @Test
    fun heldBackProgress_letsTheCoverageOnlyFallAsTheQueueSlidesOut() {
        val releaseProgress = 0.9f
        var previous = fraction(offsetPx = 0f, backProgress = releaseProgress)
        for (step in 1..20) {
            val current = fraction(
                offsetPx = hiddenOffsetPx * step / 20f,
                backProgress = releaseProgress
            )
            assertTrue(current <= previous, "Coverage rose from $previous to $current at step $step")
            previous = current
        }
        assertEquals(0f, previous)
    }

    @Test
    fun outOfRangeBackProgress_isClamped() {
        assertEquals(0f, fraction(offsetPx = 0f, backProgress = 1.4f))
        assertEquals(1f, fraction(offsetPx = 0f, backProgress = -0.3f))
    }

    @Test
    fun unmeasuredQueueSheet_reportsNoCoverage() {
        assertEquals(0f, fraction(offsetPx = 0f, hiddenPx = 0f))
        assertEquals(0f, fraction(offsetPx = 0f, hiddenPx = -10f))
    }

    @Test
    fun hiddenQueue_reportsNoCoverageWhateverItsOffset() {
        assertEquals(0f, fraction(isQueueShown = false, offsetPx = 0f))
        assertEquals(0f, fraction(isQueueShown = false, offsetPx = hiddenOffsetPx / 2f))
    }

    @Test
    fun offsetBeyondHiddenOffset_isClampedToNoCoverage() {
        assertEquals(0f, fraction(offsetPx = hiddenOffsetPx * 1.5f))
    }

    @Test
    fun overscrolledOffset_isClampedToFullCoverage() {
        assertEquals(1f, fraction(offsetPx = -40f))
    }

    @Test
    fun keyboardOpenedFromTheOpenQueue_keepsTheQueueHostComposed() {
        // Regression for #215: the custom speed field lives inside the queue's speed sheet.
        assertTrue(
            computeShouldRenderQueueHost(
                isKeyboardVisible = true,
                isQueueShown = true,
                isSongInfoShown = false
            )
        )
    }

    @Test
    fun keyboardWithQueueClosed_dropsTheQueueHost() {
        assertFalse(
            computeShouldRenderQueueHost(
                isKeyboardVisible = true,
                isQueueShown = false,
                isSongInfoShown = false
            )
        )
    }

    @Test
    fun keyboardWithSongInfoShown_keepsTheQueueHostComposed() {
        assertTrue(
            computeShouldRenderQueueHost(
                isKeyboardVisible = true,
                isQueueShown = false,
                isSongInfoShown = true
            )
        )
    }

    @Test
    fun noKeyboard_keepsTheQueueHostComposed() {
        assertTrue(
            computeShouldRenderQueueHost(
                isKeyboardVisible = false,
                isQueueShown = false,
                isSongInfoShown = false
            )
        )
    }
}
