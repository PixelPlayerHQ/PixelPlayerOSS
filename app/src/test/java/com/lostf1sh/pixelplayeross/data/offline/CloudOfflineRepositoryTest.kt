package com.lostf1sh.pixelplayeross.data.offline

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Test

class CloudOfflineRepositoryTest {
    @Test
    fun `provider detection accepts only supported cloud schemes`() {
        assertThat(CloudOfflineRepository.providerFor("navidrome://track_1")).isEqualTo("navidrome")
        assertThat(CloudOfflineRepository.providerFor("jellyfin://ABC123")).isEqualTo("jellyfin")
        assertThat(CloudOfflineRepository.providerFor("NAVIDROME://track_1")).isEqualTo("navidrome")
        assertThat(CloudOfflineRepository.providerFor("https://example.com/song.mp3")).isNull()
        assertThat(CloudOfflineRepository.providerFor("file:///music/song.mp3")).isNull()
    }

    @Test
    fun `download ids are stable and do not expose provider identifiers`() {
        val uri = "navidrome://private-track-id"
        val first = CloudOfflineRepository.downloadId(uri)
        val second = CloudOfflineRepository.downloadId(uri)

        assertThat(first).isEqualTo(second)
        assertThat(first).hasLength(64)
        assertThat(first).doesNotContain("private-track-id")
        assertThat(first).isNotEqualTo(CloudOfflineRepository.downloadId("navidrome://other"))
    }

    @Test
    fun `cloud song detection uses canonical playback uri`() {
        assertThat(CloudOfflineRepository.isCloudSong(song("navidrome://abc"))).isTrue()
        assertThat(CloudOfflineRepository.isCloudSong(song("jellyfin://ABC123"))).isTrue()
        assertThat(CloudOfflineRepository.isCloudSong(song("content://media/audio/1"))).isFalse()
    }

    @Test
    fun `download candidates keep both providers while excluding local songs and duplicate uris`() {
        val navidrome = song("navidrome://one")
        val duplicate = song("navidrome://one").copy(id = "duplicate")
        val jellyfin = song("jellyfin://two")
        val local = song("content://media/audio/3")

        val candidates = CloudOfflineRepository.downloadCandidates(
            listOf(navidrome, duplicate, jellyfin, local)
        )

        assertThat(candidates).containsExactly(navidrome, jellyfin).inOrder()
    }

    @Test
    fun `separate attempts cannot share temporary or final file names`() {
        val downloadId = CloudOfflineRepository.downloadId("navidrome://track_1")

        val first = CloudOfflineRepository.attemptFileStem(downloadId, "attempt-a")
        val second = CloudOfflineRepository.attemptFileStem(downloadId, "attempt-b")

        assertThat(first).isNotEqualTo(second)
        assertThat("$first.part").isNotEqualTo("$second.part")
        assertThat("$first.flac").isNotEqualTo("$second.flac")
    }

    @Test
    fun `repeated batch actions do not replace queued or active downloads`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.QUEUED.storageValue,
                completedFileAvailable = false
            )
        ).isFalse()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.DOWNLOADING.storageValue,
                completedFileAvailable = false
            )
        ).isFalse()
    }

    @Test
    fun `complete download is reused only while its non-empty file is available`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.COMPLETE.storageValue,
                completedFileAvailable = true
            )
        ).isFalse()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.COMPLETE.storageValue,
                completedFileAvailable = false
            )
        ).isTrue()
    }

    @Test
    fun `failed and previously unseen downloads create a new attempt`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.FAILED.storageValue,
                completedFileAvailable = false
            )
        ).isTrue()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = null,
                completedFileAvailable = false
            )
        ).isTrue()
    }

    private fun song(uri: String) = Song.emptySong().copy(contentUriString = uri)
}
