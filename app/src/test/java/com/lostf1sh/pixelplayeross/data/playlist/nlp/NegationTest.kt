package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test
import kotlin.random.Random

class NegationTest {

    private fun song(
        id: String,
        title: String = "",
        artist: String = "",
        genre: String? = null,
    ): Song = Song.emptySong().copy(id = id, title = title, artist = artist, genre = genre)

    @Test
    fun `without routes the following term to negatedStems and out of contentStems`() {
        val index = LibraryIndex.build(listOf(song(id = "x", title = "T", artist = "A")))
        val parsed = PlaylistIntentEngine.parse("rock without metal", index)

        assertThat(parsed.negatedStems).contains(NlpText.stem("metal"))
        assertThat(parsed.contentStems).contains(NlpText.stem("rock"))
        assertThat(parsed.contentStems).doesNotContain(NlpText.stem("metal"))
    }

    @Test
    fun `metal genre song scores lower than non-metal rock song for rock without metal`() {
        val songs = listOf(
            song(id = "metal_rock", title = "Iron Riff", artist = "Steel", genre = "Metal"),
            song(id = "plain_rock", title = "Sunny Road", artist = "Cruise", genre = "Rock"),
        )

        val result = PlaylistIntentEngine.generate(
            query = "rock without metal",
            minLength = 1,
            maxLength = 2,
            index = LibraryIndex.build(songs),
            engagements = emptyMap(),
            random = Random(0),
        )

        assertThat(result).contains("plain_rock")
        assertThat(result).doesNotContain("metal_rock")
    }

    @Test
    fun `workout mood is not triggered when its only cue is negated`() {
        val index = LibraryIndex.build(listOf(song(id = "x", title = "T", artist = "A")))
        val parsed = PlaylistIntentEngine.parse("rock without metal", index)

        assertThat(parsed.moods).doesNotContain(MoodProfile.WORKOUT)
    }

    @Test
    fun `sin reggaeton routes reggaeton stem to negatedStems`() {
        val index = LibraryIndex.build(listOf(song(id = "x", title = "T", artist = "A")))
        val parsed = PlaylistIntentEngine.parse("sin reggaeton", index)

        assertThat(parsed.negatedStems).contains(NlpText.stem("reggaeton"))
        assertThat(parsed.contentStems).doesNotContain(NlpText.stem("reggaeton"))
    }
}
