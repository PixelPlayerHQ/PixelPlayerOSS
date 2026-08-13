package com.lostf1sh.pixelplayeross.data.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lostf1sh.pixelplayeross.data.database.OfflineTrackDao
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import kotlin.coroutines.coroutineContext

@HiltWorker
class CloudTrackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: OfflineTrackDao,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    baseOkHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {
    private val client = baseOkHttpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure()
        val attemptId = inputData.getString(KEY_ATTEMPT_ID)
            ?: return@withContext Result.failure()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)
            ?: return@withContext Result.failure()
        val entity = dao.getByDownloadId(downloadId)
            ?: return@withContext Result.success()
        if (entity.attemptId != attemptId || entity.sourceUri != sourceUri) {
            return@withContext Result.success()
        }

        val now = System.currentTimeMillis()
        if (dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = 0L,
            totalBytes = null,
            localPath = null,
            errorMessage = null,
            updatedAt = now
        ) == 0) return@withContext Result.success()

        val tempFile = CloudOfflineRepository.downloadDirectory(applicationContext)
            .resolve("${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.part")
        tempFile.delete()
        var finalizedFile: File? = null

        try {
            val source = resolveSource(sourceUri)
            val requestBuilder = Request.Builder().url(source.url)
            source.headers.forEach { (name, value) -> requestBuilder.header(name, value) }

            client.newCall(requestBuilder.get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadHttpException(response.code)
                }
                if (!CloudStreamSecurity.isSupportedAudioContentType(response.header("Content-Type"))) {
                    throw IOException("Server returned a non-audio response")
                }
                if (!CloudStreamSecurity.isAcceptableContentLength(response.header("Content-Length"))) {
                    throw IOException("Audio file is too large")
                }

                val body = response.body
                val total = body.contentLength().takeIf { it >= 0L }
                val extension = extensionFor(response.header("Content-Type"), entity.mimeType)
                val finalFile = CloudOfflineRepository.downloadDirectory(applicationContext)
                    .resolve(
                        "${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.$extension"
                    )
                var copied = 0L
                var lastPublished = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                                throw IOException("Audio file is too large")
                            }
                            if (copied - lastPublished >= PROGRESS_STEP_BYTES) {
                                publishProgress(downloadId, attemptId, copied, total)
                                lastPublished = copied
                            }
                        }
                    }
                }

                if (copied <= 0L) throw IOException("Downloaded file is empty")
                if (total != null && copied != total) {
                    throw IOException("Download ended early ($copied/$total bytes)")
                }
                coroutineContext.ensureActive()
                if (!dao.isCurrentAttempt(downloadId, attemptId)) {
                    throw StaleDownloadAttemptException()
                }
                finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                finalizedFile = finalFile
                coroutineContext.ensureActive()
                val completed = dao.updateState(
                    downloadId = downloadId,
                    attemptId = attemptId,
                    state = OfflineDownloadStatus.COMPLETE.storageValue,
                    bytesDownloaded = copied,
                    totalBytes = total ?: copied,
                    localPath = finalFile.absolutePath,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                if (completed == 0) {
                    finalFile.delete()
                }
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            finalizedFile?.delete()
            throw cancelled
        } catch (_: StaleDownloadAttemptException) {
            tempFile.delete()
            finalizedFile?.delete()
            Result.success()
        } catch (error: Throwable) {
            tempFile.delete()
            finalizedFile?.delete()
            val shouldRetry = runAttemptCount < MAX_RETRIES &&
                (error is IOException || (error is DownloadHttpException && error.code >= 500))
            Timber.tag(TAG).w(error, "Cloud track download failed for %s", sourceUri)
            val updated = dao.updateState(
                downloadId = downloadId,
                attemptId = attemptId,
                state = if (shouldRetry) {
                    OfflineDownloadStatus.QUEUED.storageValue
                } else {
                    OfflineDownloadStatus.FAILED.storageValue
                },
                bytesDownloaded = 0L,
                totalBytes = null,
                localPath = null,
                errorMessage = error.message ?: error.javaClass.simpleName,
                updatedAt = System.currentTimeMillis()
            )
            when {
                updated == 0 -> Result.success()
                shouldRetry -> Result.retry()
                else -> Result.failure(
                    workDataOf(KEY_ERROR to (error.message ?: "Download failed"))
                )
            }
        }
    }

    private suspend fun publishProgress(
        downloadId: String,
        attemptId: String,
        copied: Long,
        total: Long?
    ) {
        val updated = dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = copied,
            totalBytes = total,
            localPath = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        if (updated == 0) throw StaleDownloadAttemptException()
        setProgress(workDataOf(KEY_BYTES to copied, KEY_TOTAL_BYTES to (total ?: -1L)))
    }

    private fun resolveSource(sourceUri: String): DownloadSource {
        val parsed = sourceUri.toUri()
        val id = parsed.host ?: parsed.path?.removePrefix("/")
            ?: throw IOException("Cloud track identifier is missing")
        return when (parsed.scheme?.lowercase()) {
            "navidrome" -> {
                if (!CloudStreamSecurity.validateNavidromeSongId(id)) {
                    throw IOException("Invalid Navidrome track identifier")
                }
                DownloadSource(
                    url = navidromeRepository.getStreamUrl(id),
                    allowedHost = navidromeRepository.serverUrl
                )
            }
            "jellyfin" -> {
                if (!CloudStreamSecurity.validateJellyfinItemId(id)) {
                    throw IOException("Invalid Jellyfin track identifier")
                }
                DownloadSource(
                    url = jellyfinRepository.getStreamUrl(id),
                    headers = jellyfinRepository.getAuthorizationHeader()
                        ?.let { mapOf("Authorization" to it) }
                        .orEmpty(),
                    allowedHost = jellyfinRepository.serverUrl
                )
            }
            else -> throw IOException("Unsupported cloud provider")
        }.also { source ->
            val host = source.allowedHost
                ?.toHttpUrlOrNull()
                ?.host
                ?: throw IOException("Cloud account is not connected")
            if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                    url = source.url,
                    allowedHostSuffixes = setOf(host),
                    allowHttpForAllowedHosts = true
                )
            ) {
                throw IOException("Unsafe cloud download URL")
            }
        }
    }

    private fun extensionFor(responseType: String?, fallbackType: String?): String {
        val type = responseType?.substringBefore(';')?.lowercase()
            ?: fallbackType?.substringBefore(';')?.lowercase()
        return when (type) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "application/mp4", "video/mp4" -> "m4a"
            "audio/aac", "audio/aacp" -> "aac"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/webm" -> "webm"
            else -> "mp3"
        }
    }

    private data class DownloadSource(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val allowedHost: String?
    )

    private class DownloadHttpException(val code: Int) : IOException("Server returned HTTP $code")
    private class StaleDownloadAttemptException : Exception()

    companion object {
        const val TAG = "cloud_track_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ATTEMPT_ID = "attempt_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        private const val PROGRESS_STEP_BYTES = 512L * 1024L
        private const val MAX_RETRIES = 3
    }
}
