package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBottomAnchoringTest {

    @Test
    fun sanitizeNavigationBarBottomInset_clampsImpossibleFloatingWindowInsets() {
        assertEquals(0.dp, sanitizeNavigationBarBottomInset(Dp.Unspecified))
        assertEquals(0.dp, sanitizeNavigationBarBottomInset((-24).dp))
        assertEquals(48.dp, sanitizeNavigationBarBottomInset(48.dp))
        assertEquals(MaxNavigationBarBottomInset, sanitizeNavigationBarBottomInset(420.dp))
    }

    @Test
    fun calculatePlayerSheetCollapsedTargetY_usesMeasuredContainerHeight() {
        val targetY = calculatePlayerSheetCollapsedTargetY(
            containerHeightPx = 2_000f,
            collapsedContentHeightPx = 160f,
            bottomMarginPx = 280f,
            bottomSpacerPx = 20f
        )

        assertEquals(1_540f, targetY, 0.001f)
    }

    @Test
    fun calculatePlayerSheetCollapsedTargetY_neverPlacesSheetOutsideTopEdge() {
        val targetY = calculatePlayerSheetCollapsedTargetY(
            containerHeightPx = 700f,
            collapsedContentHeightPx = 160f,
            bottomMarginPx = 1_200f,
            bottomSpacerPx = 20f
        )

        assertEquals(0f, targetY, 0.001f)
    }
}
