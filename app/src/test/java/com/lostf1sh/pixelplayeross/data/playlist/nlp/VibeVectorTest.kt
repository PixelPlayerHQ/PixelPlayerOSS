package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test
import kotlin.random.Random

class VibeVectorTest {

    private fun song(
        id: String,
        title: String = "",
        artist: String = "",
        genre: String? = null,
    ): Song = Song.emptySong().copy(id = id, title = title, artist = artist, genre = genre)

    @Test
    fun `chill query ranks a wrongly-tagged song with a chill vibe above an untagged high-energy song`() {
        // Neither song has a usable genre tag, so the genre-string heuristic sees nothing
        // to go on for either one. Only the measured vibe vector should distinguish them.
        val hiddenChill = song(id = "hidden_chill", title = "Untitled Session", artist = "Unknown", genre = null)
        val loudUntagged = song(id = "loud_untagged", title = "Track Two", artist = "Nobody", genre = "")

        val songs = listOf(hiddenChill, loudUntagged)
        val vibeBySongId = mapOf(
            // Exactly CHILL's target vector.
            "hidden_chill" to SongVibe(energy = 0.2f, brightness = 0.3f, dynamics = 0.5f, percussiveness = 0.2f),
            // Exactly WORKOUT's target vector — as far from "chill" as this space gets.
            "loud_untagged" to SongVibe(energy = 0.9f, brightness = 0.7f, dynamics = 0.4f, percussiveness = 0.8f),
        )

        val result = PlaylistIntentEngine.generate(
            query = "chill",
            minLength = 1,
            maxLength = 2,
            index = LibraryIndex.build(songs),
            engagements = emptyMap(),
            random = Random(0),
            vibeBySongId = vibeBySongId,
        )

        assertThat(result).isNotEmpty()
        assertThat(result.first()).isEqualTo("hidden_chill")
    }

    @Test
    fun `a measured vibe opposite the mood is excluded, not merely unrewarded`() {
        // A song whose only distinguishing data is a vibe vector far from the mood target must be
        // pushed negative (excluded), not left at ~0. Both songs are untagged so the vibe is the
        // sole signal; the loud one carries WORKOUT's vector against a chill query.
        val songs = listOf(
            song(id = "calm", title = "A", artist = "X", genre = null),
            song(id = "loud", title = "B", artist = "Y", genre = null),
        )
        val vibeBySongId = mapOf(
            "calm" to SongVibe(energy = 0.2f, brightness = 0.3f, dynamics = 0.5f, percussiveness = 0.2f),
            "loud" to SongVibe(energy = 0.95f, brightness = 0.85f, dynamics = 0.3f, percussiveness = 0.9f),
        )

        val result = PlaylistIntentEngine.generate(
            query = "chillout",
            minLength = 1,
            maxLength = 2,
            index = LibraryIndex.build(songs),
            engagements = emptyMap(),
            random = Random(0),
            vibeBySongId = vibeBySongId,
        )

        assertThat(result).contains("calm")
        assertThat(result).doesNotContain("loud")
    }

    @Test
    fun `without vibe data the genre-energy fallback behaves exactly as before`() {
        // Metal genre must still be excluded from a chillout query when vibe data is absent
        // (no vibeBySongId argument below).
        val songs = listOf(
            song(id = "metal", title = "Iron", artist = "Steel", genre = "Metal"),
            song(id = "ambient", title = "Drift", artist = "Cloud", genre = "Ambient"),
        )

        val result = PlaylistIntentEngine.generate(
            query = "chillout",
            minLength = 1,
            maxLength = 2,
            index = LibraryIndex.build(songs),
            engagements = emptyMap(),
            random = Random(0),
            // vibeBySongId omitted entirely -> defaults to emptyMap(), exercising the fallback.
        )

        assertThat(result).contains("ambient")
        assertThat(result).doesNotContain("metal")
    }
}
