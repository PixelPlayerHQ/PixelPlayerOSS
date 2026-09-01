package com.lostf1sh.pixelplayeross.data.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistParsingUtilsTest {

    @Test
    fun `choosePreferredArtistName prefers media store when it contains more artists`() {
        val result =
            choosePreferredArtistName(
                localArtistName = "Calvin Harris",
                mediaStoreArtistName = "Calvin Harris, Pharrell Williams, Katy Perry, Big Sean, Funk Wav",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = emptyList()
            )

        assertEquals(
            "Calvin Harris, Pharrell Williams, Katy Perry, Big Sean, Funk Wav",
            result
        )
    }

    @Test
    fun `choosePreferredArtistName preserves richer local metadata when media store is reduced to primary`() {
        val result =
            choosePreferredArtistName(
                localArtistName = "Calvin Harris, Pharrell Williams, Katy Perry",
                mediaStoreArtistName = "Calvin Harris",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = emptyList()
            )

        assertEquals("Calvin Harris, Pharrell Williams, Katy Perry", result)
    }

    @Test
    fun `collectArtistNames merges title features without duplicating existing artists`() {
        val result =
            collectArtistNames(
                rawArtistName = "Calvin Harris, Pharrell Williams",
                title = "Feels (feat. Katy Perry & Big Sean)",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = listOf("feat."),
                extractFromTitle = true
            )

        assertEquals(
            listOf("Calvin Harris", "Pharrell Williams", "Katy Perry", "Big Sean"),
            result
        )
    }

    @Test
    fun `collectArtistNames splits the reporter artist string with configured delimiters`() {
        val result =
            collectArtistNames(
                rawArtistName = "-M-, Toumani Diabaté, Sidiki Diabate, Fatoumata Diawara & Oxmo Puccino",
                title = "Bal de Bamako",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = emptyList(),
                extractFromTitle = false
            )

        assertEquals(
            listOf(
                "-M-",
                "Toumani Diabaté",
                "Sidiki Diabate",
                "Fatoumata Diawara",
                "Oxmo Puccino"
            ),
            result
        )
    }

    @Test
    fun `collectArtistNames preserves and splits every repeated artist tag value`() {
        val result =
            collectArtistNames(
                rawArtistNames = listOf(
                    "Primary Artist",
                    "Guest Artist & Another Guest",
                    "primary artist"
                ),
                title = "Track",
                artistDelimiters = listOf("&"),
                wordDelimiters = emptyList(),
                extractFromTitle = false
            )

        assertEquals(
            listOf("Primary Artist", "Guest Artist", "Another Guest"),
            result
        )
    }
}
