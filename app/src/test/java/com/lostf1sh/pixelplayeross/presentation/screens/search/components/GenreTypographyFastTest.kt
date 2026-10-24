package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnitType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GenreTypographyFastTest {
    @Test
    fun `same genre resolves to the same title whatever its spacing`() {
        val first = GenreTypography.resolveTitlePresentationFast(
            genreId = "rhythm-and-blues",
            genreName = "Rhythm and Blues",
            isGridView = true,
        )
        val second = GenreTypography.resolveTitlePresentationFast(
            genreId = "rhythm-and-blues",
            genreName = "  Rhythm   and Blues ",
            isGridView = true,
        )

        assertThat(first.text).isEqualTo("Rhythm and Blues")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `grid title may wrap onto a second line and list title keeps one`() {
        val grid = GenreTypography.resolveTitlePresentationFast(
            genreId = "progressive-metal",
            genreName = "Progressive Metal",
            isGridView = true,
        )
        val list = GenreTypography.resolveTitlePresentationFast(
            genreId = "progressive-metal",
            genreName = "Progressive Metal",
            isGridView = false,
        )

        assertThat(grid.text).isEqualTo("Progressive Metal")
        assertThat(grid.maxLines).isEqualTo(2)
        assertThat(list.text).isEqualTo("Progressive Metal")
        assertThat(list.maxLines).isEqualTo(1)
    }

    @Test
    fun `grid title wraps only when the name has somewhere to break`() {
        fun gridLines(name: String) = GenreTypography.resolveTitlePresentationFast(
            genreId = name.lowercase(),
            genreName = name,
            isGridView = true,
        ).maxLines

        assertThat(gridLines("Indie Rock")).isEqualTo(2)
        assertThat(gridLines("Hip-Hop")).isEqualTo(2)
        // One unbreakable word ellipsizes on one line rather than splitting mid-word.
        assertThat(gridLines("Alternative")).isEqualTo(1)
        assertThat(gridLines("Supercalifragilisticexpialidocious")).isEqualTo(1)
    }

    @Test
    fun `names that used to be cut off get room to shrink from the display size`() {
        listOf("Alternative", "Hip-Hop", "Indie Rock", "Unknown", "Electronic/House").forEach { name ->
            listOf(true, false).forEach { isGridView ->
                val title = GenreTypography.resolveTitlePresentationFast(
                    genreId = name.lowercase(),
                    genreName = name,
                    isGridView = isGridView,
                )
                val autoSize = title.autoSize

                assertThat(autoSize.maxFontSizeSp).isEqualTo(title.style.fontSize.value)
                assertThat(autoSize.singleLineMinFontSizeSp).isLessThan(autoSize.maxFontSizeSp)
                assertThat(autoSize.minFontSizeSp).isAtMost(autoSize.singleLineMinFontSizeSp)
                assertThat(autoSize.minFontSizeSp).isAtLeast(14f)
                assertThat(autoSize.minFontSizeSp).isAtMost(autoSize.maxFontSizeSp * 0.75f)
            }
        }
    }

    @Test
    fun `title spacing follows the auto-sized font and never hyphenates`() {
        val style = GenreTypography.resolveTitlePresentationFast(
            genreId = "alternative",
            genreName = "Alternative",
            isGridView = true,
        ).style

        assertThat(style.lineHeight.type).isEqualTo(TextUnitType.Em)
        assertThat(style.letterSpacing.type).isEqualTo(TextUnitType.Em)
        assertThat(style.hyphens).isEqualTo(Hyphens.None)
        assertThat(style.lineBreak).isEqualTo(LineBreak.Simple)
    }

    @Test
    fun `album styles keep their fixed line height`() {
        val style = GenreTypography.getGenreStyle(genreId = "42", genreName = "Double Disc")

        assertThat(style.lineHeight.type).isEqualTo(TextUnitType.Sp)
    }
}
