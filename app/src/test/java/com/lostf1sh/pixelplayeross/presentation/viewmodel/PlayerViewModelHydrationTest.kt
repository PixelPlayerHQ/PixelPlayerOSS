package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.Test

class PlayerViewModelHydrationTest {

    @Test
    fun withRepositoryHydration_fillsMissingLookupFieldsAndLyrics() {
        val currentSong = testSong(
            path = "",
            contentUriString = "content://media/external/audio/media/42",
            albumArtUriString = "content://art/current",
            lyrics = null
        )
        val repositorySong = testSong(
            path = "/music/track.mp3",
            contentUriString = "content://media/external/audio/media/42",
            albumArtUriString = null,
            lyrics = "[00:01.00]Hello world"
        )

        val hydratedSong = currentSong.withRepositoryHydration(repositorySong)

        assertThat(hydratedSong.path).isEqualTo("/music/track.mp3")
        assertThat(hydratedSong.lyrics).isEqualTo("[00:01.00]Hello world")
        assertThat(hydratedSong.albumArtUriString).isEqualTo("content://art/current")
    }

    @Test
    fun improvesLyricsLookupComparedTo_returnsTrueWhenHydrationAddsLyricsOrPath() {
        val partialSong = testSong(path = "", lyrics = null)
        val hydratedSong = testSong(path = "/music/track.mp3", lyrics = "Stored lyrics")

        assertThat(hydratedSong.improvesLyricsLookupComparedTo(partialSong)).isTrue()
        assertThat(partialSong.improvesLyricsLookupComparedTo(hydratedSong)).isFalse()
    }

    @Test
    fun parsePersistedLyrics_returnsParsedLyricsForNonBlankContent() {
        val parsedLyrics = parsePersistedLyrics("[00:01.00]Line one")

        assertThat(parsedLyrics).isNotNull()
        assertThat(parsedLyrics!!.synced).isNotEmpty()
        assertThat(parsedLyrics.synced!!.first().line).isEqualTo("Line one")
    }

    private fun testSong(
        path: String = "/music/original.mp3",
        contentUriString: String = "content://media/external/audio/media/42",
        albumArtUriString: String? = "content://art/original",
        lyrics: String? = null
    ): Song {
        return Song(
            id = "42",
            title = "Track",
            artist = "Artist",
            artistId = 7L,
            album = "Album",
            albumId = 11L,
            path = path,
            contentUriString = contentUriString,
            albumArtUriString = albumArtUriString,
            duration = 215_000L,
            lyrics = lyrics,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
    }
}
