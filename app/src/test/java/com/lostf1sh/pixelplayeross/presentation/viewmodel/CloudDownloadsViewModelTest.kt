package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.offline.CloudDownloadExports
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownload
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class CloudDownloadsViewModelTest {

    @Test
    fun `downloads are grouped and storage includes completed and partial files`() {
        val downloads = listOf(
            download("complete", OfflineDownloadStatus.COMPLETE, bytes = 12_000L),
            download("queued", OfflineDownloadStatus.QUEUED),
            download("downloading", OfflineDownloadStatus.DOWNLOADING, bytes = 4_000L),
            download("failed", OfflineDownloadStatus.FAILED, bytes = 8_000L)
        )

        val state = downloads.toCloudDownloadsUiState()

        assertThat(state.completed.map { it.downloadId }).containsExactly("complete")
        assertThat(state.active.map { it.downloadId })
            .containsExactly("queued", "downloading")
            .inOrder()
        assertThat(state.failed.map { it.downloadId }).containsExactly("failed")
        assertThat(state.usedBytes).isEqualTo(16_000L)
        assertThat(state.totalCount).isEqualTo(4)
    }

    @Test
    fun `remove and retry actions are delegated to repository`() = runTest {
        val failed = download("failed", OfflineDownloadStatus.FAILED)
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(listOf(failed))
        val viewModel = viewModel(repository)

        advanceUntilIdle()
        viewModel.retry(failed)
        viewModel.remove(failed)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.retry(failed.sourceUri) }
        coVerify(exactly = 1) { repository.remove(failed.sourceUri) }
    }

    @Test
    fun `retry ignores downloads that are not failed`() = runTest {
        val complete = download("complete", OfflineDownloadStatus.COMPLETE)
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(listOf(complete))
        val viewModel = viewModel(repository)

        viewModel.retry(complete)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.retry(any()) }
    }

    @Test
    fun `batch download keeps supported cloud songs and removes duplicates`() = runTest {
        val navidrome = song("navidrome", "navidrome://one")
        val duplicate = song("duplicate", "navidrome://one")
        val jellyfin = song("jellyfin", "jellyfin://two")
        val local = song("local", "content://media/audio/3")
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(emptyList())
        val viewModel = viewModel(repository)

        viewModel.downloadSelected(listOf(navidrome, duplicate, jellyfin, local))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.enqueueAll(listOf(navidrome, jellyfin)) }
    }

    @Test
    fun `batch download ignores a local only selection`() = runTest {
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(emptyList())
        val viewModel = viewModel(repository)

        viewModel.downloadSelected(listOf(song("local", "content://media/audio/3")))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.enqueueAll(any()) }
    }

    @Test
    fun `download all snapshots the synced cloud library and reports the number newly queued`() = runTest {
        val navidrome = song("navidrome", "navidrome://one")
        val jellyfin = song("jellyfin", "jellyfin://two")
        val music = mockk<MusicRepository>()
        coEvery { music.getAllSongsOnce() } returns listOf(
            navidrome,
            navidrome.copy(id = "duplicate"),
            song("local", "content://media/audio/3"),
            jellyfin
        )
        val repository = mockk<CloudOfflineRepository>()
        every { repository.observeAll() } returns flowOf(emptyList())
        coEvery { repository.enqueueAll(listOf(navidrome, jellyfin)) } returns 1
        val viewModel = viewModel(repository, music)

        viewModel.feedback.test {
            viewModel.downloadAll()
            viewModel.downloadAll()
            assertThat(awaitItem()).isEqualTo(CloudDownloadFeedback(R.string.cloud_download_batch_queued, 1))
            coVerify(exactly = 1) { repository.enqueueAll(listOf(navidrome, jellyfin)) }
        }
    }

    @Test
    fun `download all does not queue local tracks when no cloud library has been synced`() = runTest {
        val music = mockk<MusicRepository>()
        coEvery { music.getAllSongsOnce() } returns listOf(song("local", "content://media/audio/3"))
        val repository = mockk<CloudOfflineRepository>()
        every { repository.observeAll() } returns flowOf(emptyList())
        val viewModel = viewModel(repository, music)

        viewModel.feedback.test {
            viewModel.downloadAll()
            assertThat(awaitItem()).isEqualTo(CloudDownloadFeedback(R.string.cloud_download_no_synced_tracks))
            coVerify(exactly = 0) { repository.enqueueAll(any()) }
        }
    }

    private fun viewModel(
        repository: CloudOfflineRepository,
        music: MusicRepository = mockk()
    ): CloudDownloadsViewModel {
        val exports = mockk<CloudDownloadExports>()
        every { exports.folder } returns flowOf(null)
        return CloudDownloadsViewModel(repository, music, exports)
    }

    private fun download(
        id: String,
        status: OfflineDownloadStatus,
        bytes: Long = 0L
    ) = OfflineDownload(
        downloadId = id,
        sourceUri = "navidrome://$id",
        status = status,
        bytesDownloaded = bytes,
        totalBytes = null,
        localPath = null,
        errorMessage = null,
        title = id,
        provider = "navidrome"
    )

    private fun song(id: String, uri: String) = Song.emptySong().copy(
        id = id,
        title = id,
        contentUriString = uri
    )
}
