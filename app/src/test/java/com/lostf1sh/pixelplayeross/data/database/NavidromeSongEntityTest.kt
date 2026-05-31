package com.lostf1sh.pixelplayeross.data.database

import com.google.common.truth.Truth.assertThat
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
}
