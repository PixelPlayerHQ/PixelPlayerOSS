package com.lostf1sh.pixelplayeross.data.database

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinSong
import org.junit.jupiter.api.Test

class JellyfinSongEntityTest {

    private fun entity(discNumber: Int) = JellyfinSongEntity(
        id = "__library___song-1",
        jellyfinId = "song-1",
        playlistId = "__library__",
        title = "Track",
        artist = "Artist",
        artistId = "artist-1",
        album = "Album",
        albumId = "album-1",
        duration = 180_000L,
        trackNumber = 3,
        discNumber = discNumber,
        year = 2024,
        genre = "Genre",
        bitRate = 320,
        mimeType = "audio/flac",
        path = "/music/Artist/Album/Track.flac",
        dateAdded = 123L
    )

    @Test
    fun `toSong carries the disc and track number`() {
        val song = entity(discNumber = 2).toSong()

        assertThat(song.discNumber).isEqualTo(2)
        assertThat(song.trackNumber).isEqualTo(3)
    }

    @Test
    fun `toSong reports no disc when the server sent none`() {
        assertThat(entity(discNumber = 0).toSong().discNumber).isNull()
    }

    @Test
    fun `toEntity caches the disc number`() {
        val cached = JellyfinSong(
            id = "song-1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            trackNumber = 3,
            discNumber = 2
        ).toEntity(playlistId = "__library__")

        assertThat(cached.discNumber).isEqualTo(2)
        assertThat(cached.trackNumber).isEqualTo(3)
    }
}
