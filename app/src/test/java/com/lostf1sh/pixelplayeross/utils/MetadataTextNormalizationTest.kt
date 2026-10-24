package com.lostf1sh.pixelplayeross.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MetadataTextNormalizationTest {

    @Test
    fun keepsCorrectFrenchAndPortugueseTitles() {
        // These used to be "repaired" into U+FFFD because they contain â / Ã.
        listOf(
            "Château de ma mère",
            "Ça va à la fête, où êtes-vous ?",
            "CORAÇÃO",
            "São Paulo",
            "Ãrvore",
            "Gonçalves — Canção",
            "Ðorđe Balašević",
        ).forEach { title ->
            assertThat(title.normalizeMetadataText()).isEqualTo(title)
            assertThat(title.normalizeMetadataText()).doesNotContain("�")
        }
    }

    @Test
    fun keepsVietnameseLyricsIntact() {
        // Issue #131: builds before 0.3.0-alpha.19 re-encoded any text containing "â" through
        // Windows-1252, which turned letters like ẫ, ớ and đ into '?' and showed "<?>" on screen.
        val lyrics = """
            [00:01.00]Anh vẫn nhớ thân em ngày ấy
            [00:06.00]Những đêm dài thức trắng bên nhau
            [00:11.00]Gió đưa hương lúa về đồng quê, ước mơ
        """.trimIndent()

        val normalized = lyrics.normalizeMetadataText()

        assertThat(normalized).isEqualTo(lyrics)
        assertThat(normalized).doesNotContain("?")
        assertThat(normalized).doesNotContain("\uFFFD")
    }

    @Test
    fun composesDecomposedVietnamese() {
        val composed = "Người về em vẫn khóc thầm"
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)

        assertThat(decomposed.normalizeMetadataText()).isEqualTo(composed)
    }

    @Test
    fun repairsUtf8TextThatWasDecodedAsWindows1252() {
        // "à" is C3 A0 in UTF-8; A0 decodes as a non-breaking space in Windows-1252.
        assertThat("CafÃ© Ã\u00A0 la franÃ§aise".normalizeMetadataText()).isEqualTo("Café à la française")
        assertThat("Donâ€™t Stop".normalizeMetadataText()).isEqualTo("Don’t Stop")
        assertThat("SÃ£o JoÃ£o".normalizeMetadataText()).isEqualTo("São João")
    }

    @Test
    fun cannotRecoverTitlesAlreadyDamagedByTheOldNormalizer() {
        // The lost byte is gone, so stored titles only heal by re-reading the source tags.
        val damaged = "Ch\uFFFDteau"

        assertThat(damaged.normalizeMetadataText()).isEqualTo(damaged)
        assertThat(damaged.normalizeMetadataText()).isNotEqualTo("Château".normalizeMetadataText())
    }

    @Test
    fun leavesAsciiAndReplacementCharactersAlone() {
        assertThat("Plain Title".normalizeMetadataText()).isEqualTo("Plain Title")
        assertThat("Broken � tag".normalizeMetadataText()).isEqualTo("Broken � tag")
    }
}
