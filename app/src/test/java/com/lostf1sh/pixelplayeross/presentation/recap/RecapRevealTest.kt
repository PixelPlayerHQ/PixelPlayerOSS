package com.lostf1sh.pixelplayeross.presentation.recap

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecapRevealTest {

    @Test
    fun `an element stays hidden until its slot and is fully shown once the slide has revealed`() {
        assertThat(revealFraction(progress = 0f, order = 0f)).isEqualTo(0f)
        assertThat(revealFraction(progress = 0.2f, order = 3f)).isEqualTo(0f)
        assertThat(revealFraction(progress = 1f, order = 0f)).isEqualTo(1f)
    }

    @Test
    fun `the latest stagger slot used by the slides still lands within one reveal`() {
        // Genre rows go up to slot 7 and the month bars up to 6.85.
        assertThat(revealFraction(progress = 1f, order = 7f)).isEqualTo(1f)
        assertThat(revealFraction(progress = 1f, order = 3f + 11 * 0.35f)).isEqualTo(1f)
    }

    @Test
    fun `later slots appear after earlier ones`() {
        val progress = 0.3f

        assertThat(revealFraction(progress, order = 1f)).isGreaterThan(revealFraction(progress, order = 2f))
    }

    @Test
    fun `revealSpan maps its window onto zero to one`() {
        assertThat(revealSpan(progress = 0.05f, start = 0.1f, length = 0.5f)).isEqualTo(0f)
        assertThat(revealSpan(progress = 0.6f, start = 0.1f, length = 0.5f)).isEqualTo(1f)
        val midway = revealSpan(progress = 0.35f, start = 0.1f, length = 0.5f)
        assertThat(midway).isGreaterThan(0f)
        assertThat(midway).isLessThan(1f)
    }
}
