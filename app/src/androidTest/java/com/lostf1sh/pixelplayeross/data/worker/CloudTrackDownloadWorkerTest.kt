package com.lostf1sh.pixelplayeross.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.OfflineTrackDao
import com.lostf1sh.pixelplayeross.data.database.OfflineTrackEntity
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import com.lostf1sh.pixelplayeross.data.offline.CloudDownloadExports
import com.lostf1sh.pixelplayeross.data.offline.CloudExportResult
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudTrackDownloadWorkerTest {
    @Test
    fun restartResumesExportFromCompletedPrivateFileWithoutDownloadingAgain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("completed-track", ".mp3", context.cacheDir)
        try {
            file.writeText("offline audio fixture")
            val entity = OfflineTrackEntity(
                downloadId = "download",
                attemptId = UUID.randomUUID().toString(),
                songId = "1",
                sourceUri = "navidrome://track",
                provider = "navidrome",
                title = "Export test track",
                mimeType = "audio/mpeg",
                localPath = file.absolutePath,
                state = OfflineDownloadStatus.COMPLETE.storageValue,
                createdAt = 0L,
                updatedAt = 0L
            )
            val dao = mockk<OfflineTrackDao>()
            val navidrome = mockk<NavidromeRepository>()
            val jellyfin = mockk<JellyfinRepository>()
            val exports = mockk<CloudDownloadExports>()
            coEvery { dao.getByDownloadId(entity.downloadId) } returns entity
            coEvery { dao.isCurrentAttempt(entity.downloadId, entity.attemptId) } returns true
            coEvery { exports.exportDownloadedTrack(file, entity.title, entity.attemptId, true) } throws
                CancellationException("worker stopped") andThen CloudExportResult.EXPORTED

            val factory = object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker =
                    CloudTrackDownloadWorker(
                        appContext, workerParameters, dao, navidrome, jellyfin, exports, OkHttpClient()
                    )
            }
            fun worker() = TestListenableWorkerBuilder<CloudTrackDownloadWorker>(context)
                .setWorkerFactory(factory)
                .setInputData(workDataOf(
                    CloudTrackDownloadWorker.KEY_DOWNLOAD_ID to entity.downloadId,
                    CloudTrackDownloadWorker.KEY_ATTEMPT_ID to entity.attemptId,
                    CloudTrackDownloadWorker.KEY_SOURCE_URI to entity.sourceUri
                ))
                .build()

            assertThat(runCatching { worker().doWork() }.exceptionOrNull())
                .isInstanceOf(CancellationException::class.java)
            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(file.readText()).isEqualTo("offline audio fixture")
            coVerify(exactly = 2) { exports.exportDownloadedTrack(file, entity.title, entity.attemptId, true) }
            verify(exactly = 0) {
                navidrome.getStreamUrl(any(), any())
                jellyfin.getStreamUrl(any(), any())
            }
        } finally {
            file.delete()
        }
        Unit
    }
}
