package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.DailyMixManager.SongEngagementStats
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test
import kotlin.random.Random

class MultiMoodTest {

    private fun song(
        id: String,
        title: String = "",
        artist: String = "",
        genre: String? = null,
        year: Int = 0,
        favorite: Boolean = false,
    ): Song = Song.emptySong().copy(
        id = id, title = title, artist = artist, genre = genre, year = year, isFavorite = favorite,
    )

    @Test
    fun `single strong phrase-triggered mood still scores its songs highly, no regression`() {
        val songs = listOf(
            song(id = "metal", title = "Iron", artist = "Steel", genre = "Metal"),
            song(id = "ambient", title = "Drift", artist = "Cloud", genre = "Ambient"),
        )
        val index = LibraryIndex.build(songs)
        val parsed = PlaylistIntentEngine.parse("para entrenar", index)
        assertThat(parsed.moodStrengths[MoodProfile.WORKOUT]).isEqualTo(1.0)

        val result = PlaylistIntentEngine.generate(
            query = "para entrenar",
            minLength = 1,
            maxLength = 2,
            index = index,
            engagements = emptyMap(),
            random = Random(0),
        )

        assertThat(result).contains("metal")
        assertThat(result).doesNotContain("ambient")
    }

    @Test
    fun `phrase hit yields higher mood strength than a lone synonym`() {
        val phraseStrength = MoodProfile.HAPPY.matchStrength(
            queryTokenStems = emptySet(),
            normalizedQuery = NlpText.normalize("feel good"),
        )
        val synonymStrength = MoodProfile.HAPPY.matchStrength(
            queryTokenStems = setOf(NlpText.stem(NlpText.normalize("happy"))),
            normalizedQuery = NlpText.normalize("happy"),
        )

        assertThat(phraseStrength).isGreaterThan(synonymStrength)
        assertThat(phraseStrength).isEqualTo(1.0)
        assertThat(synonymStrength).isEqualTo(0.5)
    }

    @Test
    fun `several matched moods cannot out-score a strong structural match past the mood bonus cap`() {
        // "my favorites" + "surprise me" both trigger at full phrase strength (1.0 each); their
        // *uncapped* sum (35 + 35 = 70) would outscore the structural song's fixed bonuses below,
        // but the cap (45) must keep it from doing so. Both songs sit in the same genre family
        // (GUITAR) so the "rock" in the query doesn't exclude either — this test isolates the cap.
        val multiMoodSong = song(
            id = "multi_mood", title = "Static Bloom", artist = "Turnover",
            genre = "Alternative", year = 2020, favorite = true,
        )
        val structuralSong = song(
            id = "structural", title = "Night Drive", artist = "Vellum",
            genre = "Rock", year = 1995, favorite = false,
        )
        val engagements = mapOf(
            // Give the structural song plays so it doesn't also pick up the DISCOVERY bonus,
            // which would contaminate the comparison this test relies on.
            "structural" to SongEngagementStats(playCount = 10, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
        )
        val songs = listOf(multiMoodSong, structuralSong)
        val index = LibraryIndex.build(songs)

        val parsed = PlaylistIntentEngine.parse("my favorites surprise me rock 90s", index)
        assertThat(parsed.moodStrengths[MoodProfile.FAVORITES]).isEqualTo(1.0)
        assertThat(parsed.moodStrengths[MoodProfile.DISCOVERY]).isEqualTo(1.0)

        val result = PlaylistIntentEngine.generate(
            query = "my favorites surprise me rock 90s",
            minLength = 1,
            maxLength = 2,
            index = index,
            engagements = engagements,
            random = Random(0),
        )

        assertThat(result).containsExactly("structural", "multi_mood").inOrder()
    }
}
