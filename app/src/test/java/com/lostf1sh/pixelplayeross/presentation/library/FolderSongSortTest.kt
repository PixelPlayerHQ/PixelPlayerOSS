package com.lostf1sh.pixelplayeross.presentation.library

import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FolderSongSortTest {

    @Test
    fun `title ascending keeps the previous folder order of title then artist then id`() {
        val songs = listOf(
            song(id = "3", title = "beta", artist = "B"),
            song(id = "2", title = "Alpha", artist = "Z"),
            song(id = "4", title = "alpha", artist = "A"),
            song(id = "1", title = "Beta", artist = "B")
        )

        assertEquals(listOf("4", "2", "1", "3"), ids(songs, SortOption.FolderSongTitleAZ))
    }

    @Test
    fun `title descending reverses only the title`() {
        val songs = listOf(
            song(id = "1", title = "Alpha", artist = "B"),
            song(id = "2", title = "Beta", artist = "B"),
            song(id = "3", title = "Beta", artist = "A")
        )

        assertEquals(listOf("3", "2", "1"), ids(songs, SortOption.FolderSongTitleZA))
    }

    @Test
    fun `artist sort groups by artist then album then disc and track`() {
        val songs = listOf(
            song(id = "1", title = "Zulu", artist = "Mira", album = "Second", trackNumber = 1),
            song(id = "2", title = "Yankee", artist = "aurora", album = "Low Tide", trackNumber = 2),
            song(id = "3", title = "X-ray", artist = "Aurora", album = "Low Tide", trackNumber = 1),
            song(id = "4", title = "Whiskey", artist = "Aurora", album = "Morning", trackNumber = 1),
            song(id = "5", title = "Victor", artist = "Mira", album = "First", trackNumber = 9)
        )

        assertEquals(listOf("3", "2", "4", "5", "1"), ids(songs, SortOption.FolderSongArtist))
        assertEquals(listOf("5", "1", "3", "2", "4"), ids(songs, SortOption.FolderSongArtistDesc))
    }

    @Test
    fun `album sort orders tracks by disc then track number`() {
        val songs = listOf(
            song(id = "d2t1", title = "Disc 2 Track 1", album = "Double Disc", discNumber = 2, trackNumber = 1),
            song(id = "d1t2", title = "Disc 1 Track 2", album = "Double Disc", discNumber = 1, trackNumber = 2),
            song(id = "untagged", title = "Bonus", album = "Double Disc", discNumber = null, trackNumber = 0),
            song(id = "d1t1", title = "Disc 1 Track 1", album = "Double Disc", discNumber = null, trackNumber = 1),
            song(id = "other", title = "Opening", album = "Another", trackNumber = 5)
        )

        assertEquals(
            listOf("other", "d1t1", "d1t2", "untagged", "d2t1"),
            ids(songs, SortOption.FolderSongAlbum)
        )
        assertEquals(
            listOf("d1t1", "d1t2", "untagged", "d2t1", "other"),
            ids(songs, SortOption.FolderSongAlbumDesc)
        )
    }

    @Test
    fun `albums that share a name stay grouped by release`() {
        val songs = listOf(
            song(id = "a2", title = "Second", album = "Greatest Hits", albumId = 20L, trackNumber = 2),
            song(id = "b1", title = "First", album = "Greatest Hits", albumId = 10L, trackNumber = 1),
            song(id = "a1", title = "First", album = "Greatest Hits", albumId = 20L, trackNumber = 1),
            song(id = "b2", title = "Second", album = "Greatest Hits", albumId = 10L, trackNumber = 2)
        )

        assertEquals(listOf("b1", "b2", "a1", "a2"), ids(songs, SortOption.FolderSongAlbum))
    }

    @Test
    fun `album artist sort falls back to the track artist when the tag is blank or missing`() {
        val songs = listOf(
            song(id = "feat", title = "Glass", artist = "Aurora Fields feat. Mira", albumArtist = "Aurora Fields", album = "Low Tide", trackNumber = 2),
            song(id = "blank", title = "Static", artist = "Blue Quartet", albumArtist = " ", album = "Night Set"),
            song(id = "missing", title = "Echo", artist = "Mira", albumArtist = null, album = "Solo"),
            song(id = "tagged", title = "Harbor", artist = "Aurora Fields", albumArtist = "Aurora Fields", album = "Low Tide", trackNumber = 1),
            song(id = "compilation", title = "Intro", artist = "Zed", albumArtist = "Various Artists", album = "Mix")
        )

        assertEquals(
            listOf("tagged", "feat", "blank", "missing", "compilation"),
            ids(songs, SortOption.FolderSongAlbumArtist)
        )
        assertEquals(
            listOf("compilation", "missing", "blank", "tagged", "feat"),
            ids(songs, SortOption.FolderSongAlbumArtistDesc)
        )
    }

    @Test
    fun `date added defaults to newest first and ascending flips it`() {
        val songs = listOf(
            song(id = "old", title = "Old", dateAdded = 100L),
            song(id = "new", title = "New", dateAdded = 300L),
            song(id = "mid", title = "Mid", dateAdded = 200L)
        )

        assertEquals(listOf("new", "mid", "old"), ids(songs, SortOption.FolderSongDateAdded))
        assertEquals(listOf("old", "mid", "new"), ids(songs, SortOption.FolderSongDateAddedAsc))
    }

    @Test
    fun `duration defaults to longest first and ascending flips it`() {
        val songs = listOf(
            song(id = "short", title = "Short", duration = 5_000L),
            song(id = "long", title = "Long", duration = 50_000L),
            song(id = "mid", title = "Mid", duration = 20_000L)
        )

        assertEquals(listOf("long", "mid", "short"), ids(songs, SortOption.FolderSongDuration))
        assertEquals(listOf("short", "mid", "long"), ids(songs, SortOption.FolderSongDurationAsc))
    }

    @Test
    fun `descending sorts keep ascending tie breakers`() {
        val songs = listOf(
            song(id = "b", title = "Bravo", duration = 10_000L, dateAdded = 1L),
            song(id = "a", title = "alpha", duration = 10_000L, dateAdded = 1L),
            song(id = "c", title = "Charlie", duration = 10_000L, dateAdded = 1L)
        )

        assertEquals(listOf("a", "b", "c"), ids(songs, SortOption.FolderSongDuration))
        assertEquals(listOf("a", "b", "c"), ids(songs, SortOption.FolderSongDurationAsc))
        assertEquals(listOf("a", "b", "c"), ids(songs, SortOption.FolderSongDateAdded))
    }

    @Test
    fun `equal titles and artists fall back to the id for a stable order`() {
        val songs = listOf(
            song(id = "2", title = "Same", artist = "Same"),
            song(id = "1", title = "Same", artist = "Same")
        )

        SortOption.FOLDER_SONGS.forEach { option ->
            assertEquals(listOf("1", "2"), ids(songs, option), "${option.storageKey} is not stable")
        }
    }

    @Test
    fun `an option from another list falls back to title ascending`() {
        val songs = listOf(
            song(id = "z", title = "Zulu"),
            song(id = "a", title = "Alpha")
        )

        assertEquals(listOf("a", "z"), ids(songs, SortOption.FolderNameZA))
        assertEquals(listOf("a", "z"), ids(songs, SortOption.SongDurationAsc))
    }

    @Test
    fun `a single song comes back unchanged`() {
        val songs = listOf(song(id = "only", title = "Only"))

        assertSame(songs, sortFolderSongs(songs, SortOption.FolderSongArtist))
    }

    private fun ids(songs: List<Song>, option: SortOption): List<String> =
        sortFolderSongs(songs, option).map { it.id }

    private fun song(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 1L,
        albumArtist: String? = null,
        trackNumber: Int = 0,
        discNumber: Int? = null,
        dateAdded: Long = 0L,
        duration: Long = 180_000L
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 1L,
        album = album,
        albumId = albumId,
        albumArtist = albumArtist,
        path = "/storage/emulated/0/Music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = duration,
        trackNumber = trackNumber,
        discNumber = discNumber,
        dateAdded = dateAdded,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
