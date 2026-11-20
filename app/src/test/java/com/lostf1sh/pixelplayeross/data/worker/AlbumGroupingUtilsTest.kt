package com.lostf1sh.pixelplayeross.data.worker

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.LocalAlbumGroupingSource
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import org.junit.jupiter.api.Test

class AlbumGroupingUtilsTest {

    @Test
    fun `title grouping reuses the existing local album id across differing album artists`() {
        val firstTrack = testSong("Artist", "Artist", albumId = 21L)
        val guestTrack = testSong("Artist", "Artist & Guest", albumId = 22L, albumName = " HURRY UP TOMORROW ")
        val existingAlbum = AlbumEntity(21L, firstTrack.albumName, "Artist", 1L, null, 1, 0L, 0)

        val titleIndex = LocalAlbumIndex(listOf(existingAlbum), groupByTitle = true)
        assertThat(titleIndex.idFor(guestTrack)).isEqualTo(21L)
        assertThat(titleIndex.idFor(firstTrack)).isEqualTo(21L)

        val defaultIndex = LocalAlbumIndex(listOf(existingAlbum), groupByTitle = false)
        assertThat(defaultIndex.idFor(guestTrack)).isEqualTo(22L)
        assertThat(defaultIndex.idFor(firstTrack)).isEqualTo(21L)
    }

    @Test
    fun `different titles stay separate even when MediaStore reuses an album id`() {
        val first = testSong("Artist", "Artist", albumId = 21L)
        val second = first.copy(albumName = "Dawn FM")
        val albumIndex = LocalAlbumIndex(emptyList(), groupByTitle = true)

        assertThat(albumIndex.idFor(first)).isEqualTo(21L)
        assertThat(albumIndex.idFor(second)).isNotEqualTo(21L)
        assertThat(albumIndex.idFor(second.copy(albumArtist = "Different Artist")))
            .isEqualTo(albumIndex.idFor(second))
    }

    @Test
    fun `title grouping does not merge unknown albums or group remote tracks by title`() {
        val first = testSong("Artist A", "Artist A", albumId = 21L)
        val second = testSong("Artist B", "Artist B", albumId = 22L)

        assertThat(buildAlbumGroupingKey(first.copy(albumName = "Unknown Album"), groupByTitle = true))
            .isNotEqualTo(buildAlbumGroupingKey(second.copy(albumName = "Unknown Album"), groupByTitle = true))
        assertThat(buildAlbumGroupingKey(first.copy(contentUriString = "navidrome://123"), groupByTitle = true))
            .isNotEqualTo(buildAlbumGroupingKey(second, groupByTitle = true))
    }

    @Test
    fun `title grouping reuses a stored directory group without merging other unknown albums`() {
        val track = testSong("Artist", null, parentDirectoryPath = "/music/first", albumName = "Unknown Album")
        val album = AlbumEntity(77L, track.albumName, "Artist", 1L, null, 2, 0L, 0)
        val index = LocalAlbumIndex(
            existingAlbums = listOf(album),
            groupByTitle = true,
            existingFallbackSources = listOf(LocalAlbumGroupingSource(album.id, track.albumName, null, "/music/first")),
        )

        assertThat(index.idFor(track)).isEqualTo(album.id)
        assertThat(index.idFor(track.copy(albumId = album.id, parentDirectoryPath = "/music/second")))
            .isNotEqualTo(album.id)
    }

    @Test
    fun `resolveAlbumArtist prefers embedded album artist`() {
        val resolved = resolveAlbumArtist(
            rawAlbumArtist = null,
            metadataAlbumArtist = "The Weeknd"
        )

        assertThat(resolved).isEqualTo("The Weeknd")
    }

    @Test
    fun `buildAlbumGroupingKey ignores per-track artist when title and art match`() {
        val soloTrack = testSong(
            artistName = "The Weeknd",
            albumArtist = null,
            albumArtUriString = "content://art/hurry-up",
            parentDirectoryPath = "/music/The Weeknd & Justice - Hurry Up Tomorrow"
        )
        val collabTrack = testSong(
            artistName = "The Weeknd, Justice",
            albumArtist = null,
            albumArtUriString = "content://art/hurry-up",
            parentDirectoryPath = "/music/The Weeknd & Justice - Hurry Up Tomorrow"
        )

        assertThat(buildAlbumGroupingKey(soloTrack)).isEqualTo(buildAlbumGroupingKey(collabTrack))
    }

    @Test
    fun `buildAlbumGroupingKey keeps same-titled albums apart when directories differ`() {
        val firstAlbum = testSong(
            artistName = "Artist A",
            albumArtist = null,
            albumArtUriString = null,
            parentDirectoryPath = "/music/Artist A/Greatest Hits"
        )
        val secondAlbum = testSong(
            artistName = "Artist B",
            albumArtist = null,
            albumArtUriString = null,
            parentDirectoryPath = "/music/Artist B/Greatest Hits"
        )

        assertThat(buildAlbumGroupingKey(firstAlbum)).isNotEqualTo(buildAlbumGroupingKey(secondAlbum))
    }

    @Test
    fun `buildAlbumGroupingKey ignores reused artwork for local same-titled albums`() {
        val firstAlbum = testSong(
            artistName = "Artist A",
            albumArtist = null,
            albumArtUriString = "pixelplayer_local_art://song/10",
            parentDirectoryPath = "/music/Artist A/Feels",
            albumName = "Unknown Album",
            albumId = 10L
        )
        val secondAlbum = testSong(
            artistName = "Artist B",
            albumArtist = null,
            albumArtUriString = "pixelplayer_local_art://song/10",
            parentDirectoryPath = "/music/Artist B/Feels",
            albumName = "Unknown Album",
            albumId = 11L
        )

        assertThat(buildAlbumGroupingKey(firstAlbum)).isNotEqualTo(buildAlbumGroupingKey(secondAlbum))
    }

    @Test
    fun `buildAlbumGroupingKeys keeps media fallback even when artwork exists`() {
        val album = AlbumEntity(
            id = 77L,
            title = "Unknown Album",
            artistName = "",
            artistId = 0L,
            albumArtUriString = "pixelplayer_local_art://song/10",
            songCount = 1,
            dateAdded = 0L,
            year = 0
        )

        val keys = buildAlbumGroupingKeys(album)

        assertThat(keys).contains(
            AlbumGroupingKey(
                normalizedTitle = "unknown album",
                identity = "media:77"
            )
        )
    }

    @Test
    fun `chooseAlbumDisplayArtist prefers dominant track artist when grouping is off`() {
        val songs = listOf(
            testSong(artistName = "The Weeknd", albumArtist = "The Weeknd & Justice"),
            testSong(artistName = "The Weeknd", albumArtist = "The Weeknd & Justice"),
            testSong(artistName = "The Weeknd, Justice", albumArtist = "The Weeknd & Justice")
        )

        val displayArtist = chooseAlbumDisplayArtist(
            songs = songs,
            preferAlbumArtist = false
        )

        assertThat(displayArtist).isEqualTo("The Weeknd")
    }

    @Test
    fun `chooseAlbumDisplayArtist prefers album artist when grouping is on`() {
        val songs = listOf(
            testSong(artistName = "The Weeknd", albumArtist = "The Weeknd & Justice"),
            testSong(artistName = "The Weeknd, Justice", albumArtist = "The Weeknd & Justice")
        )

        val displayArtist = chooseAlbumDisplayArtist(
            songs = songs,
            preferAlbumArtist = true
        )

        assertThat(displayArtist).isEqualTo("The Weeknd & Justice")
    }

    private fun testSong(
        artistName: String,
        albumArtist: String?,
        albumArtUriString: String? = null,
        parentDirectoryPath: String = "/music/default",
        albumName: String = "Hurry Up Tomorrow",
        albumId: Long = 42L
    ): SongEntity {
        return SongEntity(
            id = albumId,
            title = "Track",
            artistName = artistName,
            artistId = 1L,
            albumArtist = albumArtist,
            albumName = albumName,
            albumId = albumId,
            contentUriString = "content://media/$albumId",
            albumArtUriString = albumArtUriString,
            duration = 180_000L,
            genre = null,
            filePath = "$parentDirectoryPath/track.flac",
            parentDirectoryPath = parentDirectoryPath
        )
    }
}
