package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniPlayerGestureOutcomeTest {

    @Test
    fun `small drag below both intent thresholds does nothing`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 20f,
            velocityX = 300f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.None)
    }

    @Test
    fun `short intentional swipe right plays previous`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 64f,
            velocityX = 200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.Previous)
    }

    @Test
    fun `short intentional swipe left plays next`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = -64f,
            velocityX = -200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.Next)
    }

    @Test
    fun `fast compact fling crosses velocity intent threshold`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = -28f,
            velocityX = -1_100f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.Next)
    }

    @Test
    fun `long drag keeps playlist dismiss behavior`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 450f,
            velocityX = 0f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.DismissRight)
    }

    @Test
    fun `cancelled long drag below dismiss threshold does not skip`() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 180f,
            velocityX = 0f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr
        )

        assertThat(outcome).isEqualTo(MiniPlayerGestureOutcome.None)
    }

    @Test
    fun `transport swipe direction stays physical in rtl`() {
        val rightSwipe = resolveMiniPlayerGestureOutcome(
            displacementX = 64f,
            velocityX = 200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Rtl
        )
        val leftSwipe = resolveMiniPlayerGestureOutcome(
            displacementX = -64f,
            velocityX = -200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Rtl
        )

        assertThat(rightSwipe).isEqualTo(MiniPlayerGestureOutcome.Previous)
        assertThat(leftSwipe).isEqualTo(MiniPlayerGestureOutcome.Next)
    }
}
