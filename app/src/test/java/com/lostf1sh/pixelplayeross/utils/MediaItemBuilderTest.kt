package com.lostf1sh.pixelplayeross.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MediaItemBuilderTest {

    @Test
    fun artworkScheme_supportsNavidromeArtworkForInternalPlayback() {
        assertThat(MediaItemBuilder.isSupportedInternalArtworkScheme("navidrome_cover")).isTrue()
    }

    @Test
    fun artworkScheme_supportsJellyfinArtworkForInternalPlayback() {
        assertThat(MediaItemBuilder.isSupportedInternalArtworkScheme("jellyfin_cover")).isTrue()
    }

    @Test
    fun shouldPreferDirectLocalFileUri_prefersDirectFileUriForLocalM4aMediaStoreItems() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "content://media/external/audio/media/42",
            filePath = "/storage/emulated/0/Music/test-track.m4a",
            mimeType = "audio/mp4"
        )

        assertThat(shouldPreferFile).isTrue()
    }

    @Test
    fun shouldPreferDirectLocalFileUri_keepsContentUriForFormatsThatAlreadySeekCorrectly() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "content://media/external/audio/media/24",
            filePath = "/storage/emulated/0/Music/test-track.flac",
            mimeType = "audio/flac"
        )

        assertThat(shouldPreferFile).isFalse()
    }

    @Test
    fun shouldPreferDirectLocalFileUri_keepsCloudUrisUntouched() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "navidrome://song-123",
            filePath = "/storage/emulated/0/Download/cached-track.m4a",
            mimeType = "audio/mp4"
        )

        assertThat(shouldPreferFile).isFalse()
    }

    @Test
    fun playbackMimeType_clearsAmbiguousLocalM4aMimeType() {
        val playbackMimeType = MediaItemBuilder.playbackMimeType(
            contentUriString = "content://media/external/audio/media/42",
            filePath = "/storage/emulated/0/Music/test-track.m4a",
            mimeType = "audio/mp4"
        )

        assertThat(playbackMimeType).isNull()
    }

    @Test
    fun playbackMimeType_keepsNonMp4MimeTypeForLocalPlayback() {
        val playbackMimeType = MediaItemBuilder.playbackMimeType(
            contentUriString = "content://media/external/audio/media/84",
            filePath = "/storage/emulated/0/Music/test-track.flac",
            mimeType = "audio/flac"
        )

        assertThat(playbackMimeType).isEqualTo("audio/flac")
    }
}
