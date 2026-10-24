package com.lostf1sh.pixelplayeross.presentation.model

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RecentlyPlayedSongUiTest {

    @Test
    fun `withoutSong removes only the matching song and keeps the order`() {
        val recent = listOf(item("a", 30L), item("b", 20L), item("c", 10L))

        val result = recent.withoutSong("b")

        assertEquals(listOf("a", "c"), result.map { it.song.id })
        assertEquals(listOf(30L, 10L), result.map { it.lastPlayedTimestamp })
    }

    @Test
    fun `withoutSong returns the same list when the song is not in it`() {
        val recent = listOf(item("a", 30L), item("b", 20L))

        assertSame(recent, recent.withoutSong("z"))
    }

    @Test
    fun `withUpdatedSongs shows edited song data without reordering the pills`() {
        val recent = listOf(item("a", 30L), item("b", 20L), item("c", 10L))
        // Playing "c" again moved it to the front of the live list, and "b" was renamed.
        val latest = listOf(
            item("c", 40L),
            item("a", 30L),
            item("b", 20L, title = "Renamed")
        )

        val result = recent.withUpdatedSongs(latest)

        assertEquals(listOf("a", "b", "c"), result.map { it.song.id })
        assertEquals(listOf("Song a", "Renamed", "Song c"), result.map { it.song.title })
        assertEquals(listOf(30L, 20L, 10L), result.map { it.lastPlayedTimestamp })
    }

    @Test
    fun `withUpdatedSongs keeps songs that are missing from the live list`() {
        val recent = listOf(item("a", 30L), item("b", 20L))
        val latest = listOf(item("a", 30L, title = "Renamed"))

        val result = recent.withUpdatedSongs(latest)

        assertEquals(listOf("a", "b"), result.map { it.song.id })
        assertEquals(listOf("Renamed", "Song b"), result.map { it.song.title })
    }

    @Test
    fun `withUpdatedSongs returns the same list when nothing changed`() {
        val recent = listOf(item("a", 30L), item("b", 20L))

        assertSame(recent, recent.withUpdatedSongs(listOf(item("b", 50L), item("a", 30L))))
        assertSame(recent, recent.withUpdatedSongs(emptyList()))
    }

    private fun item(
        id: String,
        timestamp: Long,
        title: String = "Song $id"
    ) = RecentlyPlayedSongUiModel(
        song = Song.emptySong().copy(id = id, title = title, artist = "Artist $id"),
        lastPlayedTimestamp = timestamp
    )
}
