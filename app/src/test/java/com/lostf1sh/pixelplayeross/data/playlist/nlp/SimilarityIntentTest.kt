package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test
import kotlin.random.Random

class SimilarityIntentTest {

    private fun song(
        id: String,
        title: String = "",
        artist: String = "",
        genre: String? = null,
    ): Song = Song.emptySong().copy(id = id, title = title, artist = artist, genre = genre)

    private fun generate(query: String, songs: List<Song>): List<String> = PlaylistIntentEngine.generate(
        query = query,
        minLength = 1,
        maxLength = songs.size,
        index = LibraryIndex.build(songs),
        engagements = emptyMap(),
        random = Random(0),
    )

    @Test
    fun `something like Radiohead resolves seed artist and ranks the genre neighborhood, not the seed itself`() {
        val songs = listOf(
            song(id = "radiohead_song", title = "Karma Police", artist = "Radiohead", genre = "Alternative Rock"),
            song(id = "alt_other", title = "Static Bloom", artist = "Turnover", genre = "Alternative"),
            song(id = "pop_unrelated", title = "Sunshine", artist = "Dua Lipa", genre = "Pop"),
        )

        val index = LibraryIndex.build(songs)
        val parsed = PlaylistIntentEngine.parse("something like Radiohead", index)
        assertThat(parsed.similarToArtist).isEqualTo("radiohead")

        val result = generate("something like Radiohead", songs)

        assertThat(result).contains("alt_other")
        assertThat(result).doesNotContain("radiohead_song")
        assertThat(result).doesNotContain("pop_unrelated")
    }

    @Test
    fun `play Radiohead without a similarity cue keeps the literal artist filter and ranks it first`() {
        val songs = listOf(
            song(id = "radiohead_song", title = "Karma Police", artist = "Radiohead", genre = "Alternative Rock"),
            song(id = "alt_other", title = "Static Bloom", artist = "Turnover", genre = "Alternative"),
            song(id = "pop_unrelated", title = "Sunshine", artist = "Dua Lipa", genre = "Pop"),
        )

        val index = LibraryIndex.build(songs)
        val parsed = PlaylistIntentEngine.parse("play Radiohead", index)
        assertThat(parsed.similarToArtist).isNull()

        val result = generate("play Radiohead", songs)

        assertThat(result).isNotEmpty()
        assertThat(result.first()).isEqualTo("radiohead_song")
    }

    @Test
    fun `I like happy songs has no resolvable seed artist and behaves as a normal HAPPY mood query`() {
        val songs = listOf(
            song(id = "happy_pop", title = "Sunny Days", artist = "Joyband", genre = "Pop"),
            song(id = "sad_ambient", title = "Grey Rain", artist = "Mistband", genre = "Ambient"),
        )

        val index = LibraryIndex.build(songs)
        val parsed = PlaylistIntentEngine.parse("I like happy songs", index)
        assertThat(parsed.similarToArtist).isNull()
        assertThat(parsed.moods).contains(MoodProfile.HAPPY)

        val result = generate("I like happy songs", songs)

        assertThat(result).contains("happy_pop")
        assertThat(result).doesNotContain("sad_ambient")
    }
}
