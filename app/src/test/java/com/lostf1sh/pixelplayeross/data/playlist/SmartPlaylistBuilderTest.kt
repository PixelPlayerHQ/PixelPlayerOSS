package com.lostf1sh.pixelplayeross.data.playlist

import com.lostf1sh.pixelplayeross.data.DailyMixManager.SongEngagementStats
import com.lostf1sh.pixelplayeross.data.model.SmartPlaylistRule
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SmartPlaylistBuilderTest {

    private val now = 1_700_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    private fun song(id: String, title: String = "T-$id", dateAdded: Long = 0L) = Song(
        id = id,
        title = title,
        artist = "Artist",
        artistId = 0L,
        album = "Album",
        albumId = 0L,
        path = "/music/$id.mp3",
        contentUriString = "content://$id",
        albumArtUriString = null,
        duration = 1_000L,
        dateAdded = dateAdded,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )

    private fun stats(playCount: Int = 0, durationMs: Long = 0L, lastPlayed: Long = 0L) =
        SongEngagementStats(playCount = playCount, totalPlayDurationMs = durationMs, lastPlayedTimestamp = lastPlayed)

    @Test
    fun `empty library returns empty list`() {
        val result = SmartPlaylistBuilder.buildSongIds(
            rule = SmartPlaylistRule.TOP_PLAYED,
            allSongs = emptyList(),
            engagements = emptyMap(),
            favoriteIds = emptySet(),
            now = now,
            limit = 10,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TOP_PLAYED orders by play count then duration and drops unplayed`() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val engagements = mapOf(
            "a" to stats(playCount = 5),
            "b" to stats(playCount = 3, durationMs = 1_000),
            "c" to stats(playCount = 3, durationMs = 500),
            "d" to stats(playCount = 0, durationMs = 0), // filtered out
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `limit is clamped between 1 and library size`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val engagements = mapOf(
            "a" to stats(playCount = 5),
            "b" to stats(playCount = 3),
            "c" to stats(playCount = 1),
        )
        // limit 0 -> at least 1
        assertEquals(
            listOf("a"),
            SmartPlaylistBuilder.buildSongIds(SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 0),
        )
        // limit larger than library -> all qualifying
        assertEquals(
            listOf("a", "b", "c"),
            SmartPlaylistBuilder.buildSongIds(SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 99),
        )
    }

    @Test
    fun `RECENTLY_PLAYED keeps only played songs newest first`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val engagements = mapOf(
            "a" to stats(lastPlayed = 300),
            "b" to stats(lastPlayed = 100),
            "c" to stats(lastPlayed = 0), // never played -> excluded
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.RECENTLY_PLAYED, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `FORGOTTEN_FAVORITES uses a strict 30-day threshold and only favorites`() {
        val songs = listOf(
            song("older", title = "older"),   // favorite, 31 days -> included
            song("exactly", title = "exactly"), // favorite, exactly 30 days -> excluded (strict <)
            song("recent", title = "recent"),  // favorite, 29 days -> excluded
            song("never", title = "never"),    // favorite, never played -> included
            song("nonfav", title = "nonfav"),  // 100 days but NOT a favorite -> excluded
        )
        val engagements = mapOf(
            "older" to stats(lastPlayed = daysAgo(31)),
            "exactly" to stats(lastPlayed = daysAgo(30)),
            "recent" to stats(lastPlayed = daysAgo(29)),
            "nonfav" to stats(lastPlayed = daysAgo(100)),
            // "never" has no engagement entry -> treated as lastPlayed 0
        )
        val favorites = setOf("older", "exactly", "recent", "never")
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.FORGOTTEN_FAVORITES, songs, engagements, favorites, now, limit = 10,
        )
        // Ordered by lastPlayed ascending: "never" (0) before "older" (31 days ago).
        assertEquals(listOf("never", "older"), result)
    }

    @Test
    fun `NEW_GEMS includes play count of exactly 2 but not 3, newest added first`() {
        val songs = listOf(
            song("z", dateAdded = 300), // playCount 3 -> excluded
            song("y", dateAdded = 200), // playCount 2 -> included (boundary)
            song("x", dateAdded = 100), // playCount 0 -> included
            song("w", dateAdded = 50),  // no engagement -> playCount 0 -> included
        )
        val engagements = mapOf(
            "z" to stats(playCount = 3),
            "y" to stats(playCount = 2),
            "x" to stats(playCount = 0),
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.NEW_GEMS, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("y", "x", "w"), result)
    }
}
