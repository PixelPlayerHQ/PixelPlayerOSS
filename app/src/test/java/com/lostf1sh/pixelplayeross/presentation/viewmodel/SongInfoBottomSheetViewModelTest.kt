package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SongInfoBottomSheetViewModelTest {

    private val viewModel = SongInfoBottomSheetViewModel(
        musicDao = mockk(relaxed = true),
        cloudOfflineRepository = mockk(relaxed = true),
        musicBrainzRepository = mockk(relaxed = true),
        appContext = mockk(relaxed = true),
    )

    @Test
    fun `local song shows its file path under the localized path label`() {
        val info = viewModel.getSongLocationInfo(
            song(path = "/storage/emulated/0/Music/song.flac", contentUri = "content://media/external/audio/media/1")
        )

        assertThat(info.labelRes).isEqualTo(R.string.song_info_label_path)
        assertThat(info.value).isEqualTo("/storage/emulated/0/Music/song.flac")
        assertThat(info.isCloud).isFalse()
    }

    @Test
    fun `navidrome song shows the provider name under the localized provider label`() {
        val info = viewModel.getSongLocationInfo(song(path = "", contentUri = "navidrome://song-1"))

        assertThat(info.labelRes).isEqualTo(R.string.song_info_label_provider)
        assertThat(info.value).isEqualTo("Navidrome")
        assertThat(info.isCloud).isTrue()
    }

    @Test
    fun `jellyfin song shows the provider name under the localized provider label`() {
        val info = viewModel.getSongLocationInfo(song(path = "", contentUri = "jellyfin://item-1"))

        assertThat(info.labelRes).isEqualTo(R.string.song_info_label_provider)
        assertThat(info.value).isEqualTo("Jellyfin")
        assertThat(info.isCloud).isTrue()
    }

    private fun song(path: String, contentUri: String) = Song(
        id = "1",
        title = "Song",
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
