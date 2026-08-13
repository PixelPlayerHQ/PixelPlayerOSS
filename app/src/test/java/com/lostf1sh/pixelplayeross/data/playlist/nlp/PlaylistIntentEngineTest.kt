package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.DailyMixManager.SongEngagementStats
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test
import kotlin.random.Random

class PlaylistIntentEngineTest {

    private fun song(
        id: String,
        title: String = "",
        artist: String = "",
        album: String = "",
        genre: String? = null,
        year: Int = 0,
        durationMs: Long = 180_000,
        favorite: Boolean = false,
        dateAdded: Long = 0,
    ): Song = Song.emptySong().copy(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year,
        duration = durationMs,
        isFavorite = favorite,
        dateAdded = dateAdded,
    )

    private fun generate(
        query: String,
        songs: List<Song>,
        engagements: Map<String, SongEngagementStats> = emptyMap(),
        min: Int = 5,
        max: Int = 10,
    ): List<String> = PlaylistIntentEngine.generate(
        query = query,
        minLength = min,
        maxLength = max,
        index = LibraryIndex.build(songs),
        engagements = engagements,
        random = Random(0),
    )

    @Test
    fun `synonym phrase triggers workout profile without literal keyword`() {
        val songs = listOf(
            song(id = "metal", title = "Power Anthem", artist = "Steel", genre = "Metal"),
            song(id = "ambient", title = "Calm Waters", artist = "Cloud", genre = "Ambient"),
        )

        // Neither "workout" nor "gym" appears — only the phrase "lift weights".
        val result = generate("tunes to lift weights to", songs)

        assertThat(result).contains("metal")
        assertThat(result).doesNotContain("ambient")
    }

    @Test
    fun `accented and unaccented queries produce identical results`() {
        val songs = listOf(
            song(id = "folk", title = "Grey Skies", artist = "Willow", genre = "Folk"),
            song(id = "edm", title = "Neon Rush", artist = "Volt", genre = "Electronic"),
        )

        val accented = generate("melancólico", songs)
        val plain = generate("melancolico", songs)

        assertThat(accented).isEqualTo(plain)
        assertThat(accented).contains("folk")
    }

    @Test
    fun `rare artist term outranks a common title word`() {
        val songs = (1..5).map { song(id = "love$it", title = "Love Song $it", artist = "Common Band") } +
            song(id = "rare", title = "Random", artist = "Zzyzx")

        // "love" is common (low idf); "zzyzx" is unique (high idf) → its song must rank first.
        val result = generate("love zzyzx", songs)

        assertThat(result).isNotEmpty()
        assertThat(result.first()).isEqualTo("rare")
    }

    @Test
    fun `decade marker filters by release year`() {
        val songs = listOf(
            song(id = "nineties", title = "Old Hit", artist = "A", year = 1994),
            song(id = "tens", title = "New Hit", artist = "B", year = 2016),
        )

        val result = generate("90s", songs)

        assertThat(result).contains("nineties")
        assertThat(result).doesNotContain("tens")
    }

    @Test
    fun `favorites profile prefers favorite songs`() {
        val songs = listOf(
            song(id = "fav", title = "Loved One", artist = "A", favorite = true),
            song(id = "plain", title = "Other", artist = "B", favorite = false),
        )

        val result = generate("mis favoritos", songs)

        assertThat(result).contains("fav")
        assertThat(result).doesNotContain("plain")
    }

    @Test
    fun `empty query falls back to a non-empty selection`() {
        val songs = (1..3).map { song(id = "s$it", title = "Track $it", artist = "Artist") }
        val result = generate("", songs)
        assertThat(result).isNotEmpty()
    }

    // ---- Tempo/energy scoring (BPM directive + mood conflicts) -------------------------

    @Test
    fun `chillout with low bpm target excludes heavy metal even when metal dominates play counts`() {
        // Regression: this exact query shape (with an appended BPM directive) once degenerated
        // into "most played songs" and returned heavy metal for a chillout request.
        val songs = listOf(
            song(id = "metal1", title = "Numb", artist = "Linkin Park", genre = "Nu Metal"),
            song(id = "metal2", title = "Faint", artist = "Linkin Park", genre = "Alternative Metal"),
            song(id = "ambient", title = "Weightless", artist = "Marconi Union", genre = "Ambient"),
            song(id = "jazz", title = "Blue in Green", artist = "Miles Davis", genre = "Jazz"),
        )
        val engagements = mapOf(
            "metal1" to SongEngagementStats(playCount = 250, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
            "metal2" to SongEngagementStats(playCount = 180, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
        )

        val result = generate("chillout\n[Target BPM: 60 BPM]", songs, engagements)

        assertThat(result).containsAtLeast("ambient", "jazz")
        assertThat(result).containsNoneOf("metal1", "metal2")
    }

    @Test
    fun `compound word chillout triggers the chill profile`() {
        val index = LibraryIndex.build(listOf(song(id = "x", title = "T", artist = "A")))
        val parsed = PlaylistIntentEngine.parse("chillout", index)
        assertThat(parsed.moods).contains(MoodProfile.CHILL)
    }

    @Test
    fun `bpm directive is parsed as a slot and stripped from free text`() {
        val index = LibraryIndex.build(listOf(song(id = "x", title = "T", artist = "A")))
        val parsed = PlaylistIntentEngine.parse("chillout\n[Target BPM: 60 BPM]\n[Target Length: 25 songs]", index)

        assertThat(parsed.targetBpm).isEqualTo(60f)
        assertThat(parsed.contentStems).doesNotContain("target")
        assertThat(parsed.contentStems).doesNotContain("bpm")
        assertThat(parsed.contentStems).doesNotContain("60")
    }

    @Test
    fun `measured bpm proximity selects tempo-appropriate songs`() {
        // No genres, no text signal — only measured tempo distinguishes the songs.
        val songs = listOf(
            song(id = "slow", title = "Alpha"),
            song(id = "fast", title = "Beta"),
        )

        val result = PlaylistIntentEngine.generate(
            query = "[Target BPM: 65 BPM]",
            minLength = 1,
            maxLength = 2,
            index = LibraryIndex.build(songs),
            engagements = emptyMap(),
            random = Random(0),
            bpmBySongId = mapOf("slow" to 68f, "fast" to 172f),
        )

        assertThat(result).contains("slow")
        assertThat(result).doesNotContain("fast")
    }

    @Test
    fun `chillout does not free-ride a long untagged heavy track on the duration bonus`() {
        // The reported bug: a metal-heavy library, no measured BPM/vibe data. A long track with a
        // missing genre used to collect the CHILL "long song" bonus unconditionally and leak into
        // the mix. With the bonus gated behind an actual chill genre match, it must score nothing.
        val songs = listOf(
            song(id = "ambient", title = "Drift", artist = "Cloud", genre = "Ambient", durationMs = 240_000),
            song(id = "long_untagged", title = "Untitled", artist = "Nobody", genre = null, durationMs = 360_000),
        )
        val engagements = mapOf(
            "long_untagged" to SongEngagementStats(playCount = 300, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
        )

        val result = generate("chillout", songs, engagements)

        assertThat(result).contains("ambient")
        assertThat(result).doesNotContain("long_untagged")
    }

    @Test
    fun `chillout excludes an unrecognized aggressive subgenre`() {
        // "Deathcore" / "Djent" don't contain the substring "metal", so a thin lexicon returned
        // null energy for them -> no exclusion. They must be recognized as high-energy.
        val songs = listOf(
            song(id = "deathcore", title = "Rupture", artist = "Gore", genre = "Deathcore", durationMs = 300_000),
            song(id = "djent", title = "Polyrhythm", artist = "Mesh", genre = "Progressive Djent"),
            song(id = "lofi", title = "Rainy Window", artist = "Bedroom", genre = "Lo-Fi"),
        )

        val result = generate("chillout", songs)

        assertThat(result).contains("lofi")
        assertThat(result).containsNoneOf("deathcore", "djent")
    }

    @Test
    fun `rock request excludes a phonk track and keeps genre-adjacent songs`() {
        // The reported bug: "rock" is not a mood, so before the genre taxonomy nothing stopped a
        // similar-energy phonk track from scoring in. Now cross-family songs are excluded outright,
        // while a same-family "grunge" track (no literal "rock" in its tag) is still welcome.
        val songs = listOf(
            song(id = "rock", title = "Anthem", artist = "The Band", genre = "Rock"),
            song(id = "grunge", title = "Seattle", artist = "Flannel", genre = "Grunge"),
            song(id = "phonk", title = "Drift", artist = "Memphis", genre = "Phonk"),
        )

        val result = generate("rock\n[Target BPM: 120 BPM]", songs)

        assertThat(result).containsAtLeast("rock", "grunge")
        assertThat(result).doesNotContain("phonk")
    }

    @Test
    fun `chillout fallback never pads with recognized aggressive songs`() {
        // The reported failure: an all-heavy library, no chill songs to match, so the energy
        // fallback fires. It must not surface the least-loud metal — it must return nothing.
        val songs = listOf(
            song(id = "metal1", title = "Numb", artist = "Linkin Park", genre = "Nu Metal", durationMs = 300_000),
            song(id = "metal2", title = "1x1", artist = "Bring Me The Horizon", genre = "Metalcore", durationMs = 200_000),
            song(id = "metal3", title = "Rupture", artist = "Gore", genre = "Deathcore"),
        )
        val engagements = mapOf(
            "metal1" to SongEngagementStats(playCount = 400, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
            "metal2" to SongEngagementStats(playCount = 300, totalPlayDurationMs = 0, lastPlayedTimestamp = 0),
        )

        val result = generate("chillout\n[Target BPM: 60 BPM]", songs, engagements)

        assertThat(result).isEmpty()
    }

    @Test
    fun `tagging a song as metal removes it from a chillout mix`() {
        // Simulates the user editing "Alternative"/"Music"/none -> "Nu Metal" and regenerating.
        // The untagged version is admissible (offline we can't rule it out); the tagged one is not.
        val untagged = listOf(song(id = "lp", title = "Numb", artist = "Linkin Park", genre = "Alternative"))
        val tagged = listOf(song(id = "lp", title = "Numb", artist = "Linkin Park", genre = "Nu Metal"))

        val beforeTag = generate("chillout\n[Target BPM: 60 BPM]", untagged)
        val afterTag = generate("chillout\n[Target BPM: 60 BPM]", tagged)

        assertThat(beforeTag).contains("lp")
        assertThat(afterTag).doesNotContain("lp")
    }

    @Test
    fun `editing a song genre changes the library signature so the cached index rebuilds`() {
        val before = LibraryIndex.signatureOf(listOf(song(id = "a", genre = "Alternative")))
        val after = LibraryIndex.signatureOf(listOf(song(id = "a", genre = "Nu Metal")))
        assertThat(before).isNotEqualTo(after)
    }

    @Test
    fun `workout query penalizes low-energy genres`() {
        val songs = listOf(
            song(id = "metal", title = "Iron", artist = "Steel", genre = "Metal"),
            song(id = "ambient", title = "Drift", artist = "Cloud", genre = "Ambient"),
        )

        val result = generate("para entrenar", songs)

        assertThat(result).contains("metal")
        assertThat(result).doesNotContain("ambient")
    }
}
