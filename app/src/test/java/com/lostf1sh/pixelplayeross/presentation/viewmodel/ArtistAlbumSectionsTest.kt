package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtistAlbumSectionsTest {

    @Test
    fun `each album section shows the genre most of its tracks share`() {
        val sections = buildAlbumSections(
            listOf(
                song("1", albumId = 10L, album = "Glad EP", year = 2016, genre = "House"),
                song("2", albumId = 10L, album = "Glad EP", year = 2016, genre = "House; Electronic"),
                song("3", albumId = 10L, album = "Glad EP", year = 2016, genre = "Electronic, Deep House"),
                song("4", albumId = 20L, album = "Mixed Bag", year = 2022, genre = "Indie Rock|Alternative"),
                song("5", albumId = 20L, album = "Mixed Bag", year = 2022, genre = "Alternative; Indie Rock"),
                song("6", albumId = 20L, album = "Mixed Bag", year = 2022, genre = "Alternative")
            )
        )

        assertEquals(listOf("Mixed Bag", "Glad EP"), sections.map { it.title })
        assertEquals(listOf("Alternative", "House"), sections.map { it.genre })
    }

    @Test
    fun `a tie between genres goes to the earliest track`() {
        val section = buildAlbumSections(
            listOf(
                song("2", albumId = 40L, album = "Neon Ritual", year = 2023, genre = "Electronic/House"),
                song("1", albumId = 40L, album = "Neon Ritual", year = 2023, genre = "Electronic")
            )
        ).single()

        assertEquals("Electronic", section.genre)
    }

    @Test
    fun `an album without genre tags has no section genre`() {
        val section = buildAlbumSections(
            listOf(song("1", albumId = 30L, album = "Plain", year = 2020, genre = null))
        ).single()

        assertNull(section.genre)
    }

    @Test
    fun `a cloud album without genre tags does not show the source placeholder as its genre`() {
        val section = buildAlbumSections(
            listOf(
                song("1", albumId = 50L, album = "Streamed", year = 2021, genre = "Jellyfin"),
                song("2", albumId = 50L, album = "Streamed", year = 2021, genre = "Jellyfin")
            )
        ).single()

        assertNull(section.genre)
    }

    private fun song(id: String, albumId: Long, album: String, year: Int, genre: String?) = Song(
        id = id,
        title = "Track $id",
        artist = "Test Hawkes",
        artistId = 1L,
        album = album,
        albumId = albumId,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        genre = genre,
        trackNumber = id.toInt(),
        year = year,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
