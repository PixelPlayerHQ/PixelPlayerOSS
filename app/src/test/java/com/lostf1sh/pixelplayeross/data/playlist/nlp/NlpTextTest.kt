package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NlpTextTest {

    @Test
    fun `normalize folds diacritics and lowercases`() {
        assertThat(NlpText.normalize("Melancólico")).isEqualTo("melancolico")
        assertThat(NlpText.normalize("NIÑO")).isEqualTo("nino")
        assertThat(NlpText.normalize("Relajación")).isEqualTo("relajacion")
    }

    @Test
    fun `accented and unaccented spellings normalize equal`() {
        assertThat(NlpText.normalize("nostálgico")).isEqualTo(NlpText.normalize("nostalgico"))
    }

    @Test
    fun `tokenize drops single-character tokens and splits on punctuation`() {
        assertThat(NlpText.tokenize("rock, pop & a b cd"))
            .containsExactly("rock", "pop", "cd")
    }

    @Test
    fun `stem converges spanish singular and plural forms`() {
        // Vowel-plural: relajante / relajantes.
        assertThat(NlpText.stem("relajantes")).isEqualTo(NlpText.stem("relajante"))
        // Gender + number variants of the same adjective.
        val stems = listOf("nuevo", "nueva", "nuevos", "nuevas").map { NlpText.stem(it) }.toSet()
        assertThat(stems).hasSize(1)
    }

    @Test
    fun `stem leaves short words and digit tokens untouched`() {
        assertThat(NlpText.stem("pop")).isEqualTo("pop")
        assertThat(NlpText.stem("love")).isEqualTo("love")
        assertThat(NlpText.stem("90s")).isEqualTo("90s")
        assertThat(NlpText.stem("1990")).isEqualTo("1990")
    }

    @Test
    fun `stem bridges english inflections`() {
        assertThat(NlpText.stem("dancing")).isEqualTo(NlpText.stem("dances"))
    }
}
