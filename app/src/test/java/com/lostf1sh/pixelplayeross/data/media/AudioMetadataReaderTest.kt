package com.lostf1sh.pixelplayeross.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioMetadataReaderTest {

    @Test
    fun `normalizeArtistMetadataValues preserves repeated fields in source order`() {
        val values = normalizeArtistMetadataValues(
            listOf("Primary Artist", " Guest Artist ", "primary artist", ""),
            listOf("Another Guest")
        )

        assertEquals(
            listOf("Primary Artist", "Guest Artist", "Another Guest"),
            values
        )
    }
}
