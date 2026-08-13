package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GenreTaxonomyTest {

    /** Mirrors how the engine feeds genres in: everything is normalized (lowercased, de-accented). */
    private fun fam(raw: String): GenreFamily? = GenreTaxonomy.familyOf(NlpText.normalize(raw))

    private fun qfam(raw: String): Set<GenreFamily> =
        GenreTaxonomy.queryFamilies(NlpText.stemTokens(raw).toSet(), NlpText.normalize(raw))

    @Test
    fun `spelling and punctuation variants resolve to the same family`() {
        assertThat(fam("Rock")).isEqualTo(GenreFamily.GUITAR)
        assertThat(fam("Rock & Roll")).isEqualTo(GenreFamily.GUITAR)
        assertThat(fam("Rock'n'Roll")).isEqualTo(GenreFamily.GUITAR)
        assertThat(fam("Alternative")).isEqualTo(GenreFamily.GUITAR)
        assertThat(fam("Nu-Metal")).isEqualTo(GenreFamily.GUITAR)
        assertThat(fam("Alternative Rock")).isEqualTo(GenreFamily.GUITAR)
    }

    @Test
    fun `accented and cross-language tags resolve correctly`() {
        assertThat(fam("Electrónica")).isEqualTo(GenreFamily.ELECTRONIC)
        assertThat(fam("Reggaetón")).isEqualTo(GenreFamily.LATIN)
        assertThat(fam("Música Clásica")).isEqualTo(GenreFamily.CLASSICAL)
        assertThat(fam("Acústico")).isEqualTo(GenreFamily.FOLK_COUNTRY)
    }

    @Test
    fun `ambiguous substrings resolve to the most specific family`() {
        // Each of these textually contains a *different*, broader family that must NOT win.
        assertThat(fam("Reggaeton")).isEqualTo(GenreFamily.LATIN)   // contains "reggae"
        assertThat(fam("Dubstep")).isEqualTo(GenreFamily.ELECTRONIC) // contains "dub"
        assertThat(fam("Rhythm and Blues")).isEqualTo(GenreFamily.RNB_SOUL) // contains "blues"
        assertThat(fam("Lo-Fi Hip Hop")).isEqualTo(GenreFamily.CHILL_AMBIENT) // contains "hip hop"
        assertThat(fam("Trap Latino")).isEqualTo(GenreFamily.LATIN) // contains "trap"
        assertThat(fam("Post-Rock")).isEqualTo(GenreFamily.GUITAR)
    }

    @Test
    fun `phonk and rock are different families`() {
        assertThat(fam("Phonk")).isEqualTo(GenreFamily.URBAN)
        assertThat(fam("Rock")).isEqualTo(GenreFamily.GUITAR)
    }

    @Test
    fun `unknown genres resolve to null so they are never wrongly excluded`() {
        assertThat(fam("")).isNull()
        assertThat(fam("Music")).isNull()
        assertThat(fam("Unknown")).isNull()
        assertThat(fam("My Mixtape 2019")).isNull()
    }

    @Test
    fun `only concrete genre nouns count as query families, not mood words`() {
        assertThat(qfam("rock")).containsExactly(GenreFamily.GUITAR)
        assertThat(qfam("phonk")).containsExactly(GenreFamily.URBAN)
        assertThat(qfam("give me some jazz")).contains(GenreFamily.JAZZ)
        assertThat(qfam("reggaeton para la fiesta")).contains(GenreFamily.LATIN)
        // "chill" is a mood, not a genre trigger — MoodProfile's energy band handles it, so it
        // must NOT collapse the request to a single family (that would exclude jazz/acoustic).
        assertThat(qfam("chill music")).isEmpty()
        assertThat(qfam("something relaxing")).isEmpty()
    }
}
