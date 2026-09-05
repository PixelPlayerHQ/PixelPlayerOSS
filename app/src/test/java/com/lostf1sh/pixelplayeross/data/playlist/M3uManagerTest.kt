package com.lostf1sh.pixelplayeross.data.playlist

import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class M3uManagerTest {

    @Test
    fun `parser preserves marker order and reports ambiguous basenames`() {
        val songs = listOf(
            song("1", "/Music/Album/one.flac"),
            song("2", "/Music/A/shared.flac"),
            song("3", "/Music/B/shared.flac"),
        )
        val content = """
            #EXTM3U
            #PIXELPLAYER-PLAYLIST-ID:playlist-42
            /Music/Album/one.flac
            shared.flac
            missing.flac
        """.trimIndent()

        val result = M3uManager.parseContent(content, songs)

        assertThat(result.playlistId).isEqualTo("playlist-42")
        assertThat(result.songIds).containsExactly("1")
        assertThat(result.ambiguousEntries).containsExactly("shared.flac")
        assertThat(result.unresolvedEntries).containsExactly("missing.flac")
    }

    @Test
    fun `generator emits stable identity and cloud uri fallback`() {
        val playlist = Playlist(
            id = "playlist-42",
            name = "Road Trip",
            songIds = listOf("1", "cloud"),
        )
        val songs = listOf(
            song("1", "/Music/one.flac"),
            song("cloud", "", contentUri = "navidrome://song-cloud"),
        )

        val content = M3uManager.generateContent(playlist, songs)

        assertThat(content).contains("#PIXELPLAYER-PLAYLIST-ID:playlist-42")
        assertThat(content).contains("/Music/one.flac")
        assertThat(content).contains("navidrome://song-cloud")
    }

    @Test
    fun `duplicate exact locations are ambiguous instead of picking an arbitrary song`() {
        val songs = listOf(
            song("1", "/Music/duplicate.flac"),
            song("2", "/Music/duplicate.flac"),
        )

        val result = M3uManager.parseContent("/Music/duplicate.flac", songs)

        assertThat(result.songIds).isEmpty()
        assertThat(result.ambiguousEntries).containsExactly("/Music/duplicate.flac")
    }

    @Test
    fun `parser preserves repeated tracks in their original order`() {
        val songs = listOf(
            song("intro", "/Music/intro.flac"),
            song("chorus", "/Music/chorus.flac"),
            song("outro", "/Music/outro.flac"),
        )
        val content = """
            /Music/intro.flac
            /Music/chorus.flac
            /Music/chorus.flac
            /Music/outro.flac
        """.trimIndent()

        val result = M3uManager.parseContent(content, songs)

        assertThat(result.songIds)
            .containsExactly("intro", "chorus", "chorus", "outro")
            .inOrder()
        assertThat(result.unresolvedEntries).isEmpty()
        assertThat(result.ambiguousEntries).isEmpty()
    }

    @Test
    fun `bounded reader accepts content exactly at the limit`() {
        val content = readM3uTextBounded(StringReader("1234"), maxCharacters = 4)

        assertThat(content).isEqualTo("1234")
    }

    @Test
    fun `bounded reader rejects content beyond the limit`() {
        assertThrows(IOException::class.java) {
            readM3uTextBounded(StringReader("12345"), maxCharacters = 4)
        }
    }

    private fun song(id: String, path: String, contentUri: String = "content://media/$id") = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = path,
        contentUriString = contentUri,
        albumArtUriString = null,
        duration = 60_000L,
        mimeType = "audio/flac",
        bitrate = null,
        sampleRate = null,
    )
}
