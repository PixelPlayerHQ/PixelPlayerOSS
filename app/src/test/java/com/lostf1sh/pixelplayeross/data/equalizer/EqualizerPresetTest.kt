package com.lostf1sh.pixelplayeross.data.equalizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualizerPresetTest {

    private val saved = EqualizerPreset("Mine", "Mine", listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), true)

    @Test
    fun `resolve returns the bands of a saved user preset`() {
        assertEquals(saved, EqualizerPreset.resolve("Mine", emptyList(), listOf(saved)))
    }

    @Test
    fun `resolve returns built-in presets and custom bands`() {
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.resolve("rock", emptyList(), listOf(saved)))
        val bands = List(10) { 3 }
        assertEquals(EqualizerPreset.custom(bands), EqualizerPreset.resolve("custom", bands, listOf(saved)))
    }

    @Test
    fun `resolve falls back to flat for unknown names`() {
        assertEquals(EqualizerPreset.FLAT, EqualizerPreset.resolve("gone", emptyList(), emptyList()))
    }
}
