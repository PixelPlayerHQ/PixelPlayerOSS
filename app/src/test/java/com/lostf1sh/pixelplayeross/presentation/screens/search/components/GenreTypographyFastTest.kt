package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GenreTypographyFastTest {
    @Test
    fun `long grid title gets deterministic balanced lines`() {
        val first = GenreTypography.resolveTitlePresentationFast(
            genreId = "rhythm-and-blues",
            genreName = "Rhythm and Blues",
            isGridView = true,
        )
        val second = GenreTypography.resolveTitlePresentationFast(
            genreId = "rhythm-and-blues",
            genreName = "Rhythm   and Blues",
            isGridView = true,
        )

        assertThat(first.firstLine).isEqualTo("Rhythm and")
        assertThat(first.secondLine).isEqualTo("Blues")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `list view keeps title on one line for compose ellipsis`() {
        val result = GenreTypography.resolveTitlePresentationFast(
            genreId = "progressive-metal",
            genreName = "Progressive Metal",
            isGridView = false,
        )

        assertThat(result.firstLine).isEqualTo("Progressive Metal")
        assertThat(result.secondLine).isNull()
    }
}
