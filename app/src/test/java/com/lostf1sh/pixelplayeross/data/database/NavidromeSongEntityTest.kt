package com.lostf1sh.pixelplayeross.data.database

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeSong
import org.junit.jupiter.api.Test

class NavidromeSongEntityTest {

    @Test
    fun `toSong keeps server id separate from playlist scoped row id`() {
        val entity = NavidromeSongEntity(
            id = "__library___song-1",
            navidromeId = "song-1",
            playlistId = "__library__",
            title = "Track",
            artist = "Artist",
            artistId = "artist-1",
            album = "Album",
            albumId = "album-1",
            coverArtId = "cover-1",
            duration = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            genre = "Genre",
            bitRate = 320,
            mimeType = "audio/mpeg",
            suffix = "mp3",
            path = "Artist/Album/Track.mp3",
            dateAdded = 123L
        )

        val song = entity.toSong()

        assertThat(song.id).isEqualTo("navidrome___library___song-1")
        assertThat(song.navidromeId).isEqualTo("song-1")
        assertThat(song.contentUriString).isEqualTo("navidrome://song-1")
    }

    private fun navidromeSong(created: Long) = NavidromeSong(
        id = "song-1",
        title = "Track",
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        created = created
    )

    @Test
    fun `toEntity uses server created time as date added`() {
        val entity = navidromeSong(created = 1_700_000_000_000L)
            .toEntity(playlistId = "__library__", nowMs = 1_800_000_000_000L)

        assertThat(entity.dateAdded).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `toEntity falls back to sync time when server created time is missing`() {
        val entity = navidromeSong(created = 0L)
            .toEntity(playlistId = "__library__", nowMs = 1_800_000_000_000L)

        assertThat(entity.dateAdded).isEqualTo(1_800_000_000_000L)
    }
}
