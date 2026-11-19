package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.offline.CloudDownloadExports
import com.lostf1sh.pixelplayeross.data.offline.CloudExportResult
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownload
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CloudDownloadsUiState(
    val completed: ImmutableList<OfflineDownload> = persistentListOf(),
    val active: ImmutableList<OfflineDownload> = persistentListOf(),
    val failed: ImmutableList<OfflineDownload> = persistentListOf(),
    val usedBytes: Long = 0L
) {
    val totalCount: Int get() = completed.size + active.size + failed.size
}

internal fun List<OfflineDownload>.toCloudDownloadsUiState(): CloudDownloadsUiState =
    CloudDownloadsUiState(
        completed = filter { it.status == OfflineDownloadStatus.COMPLETE }.toImmutableList(),
        active = filter {
            it.status == OfflineDownloadStatus.QUEUED ||
                it.status == OfflineDownloadStatus.DOWNLOADING
        }.toImmutableList(),
        failed = filter { it.status == OfflineDownloadStatus.FAILED }.toImmutableList(),
        usedBytes = asSequence()
            .filter {
                it.status == OfflineDownloadStatus.COMPLETE ||
                    it.status == OfflineDownloadStatus.DOWNLOADING
            }
            .sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    )

@HiltViewModel
class CloudDownloadsViewModel @Inject constructor(
    private val repository: CloudOfflineRepository,
    private val musicRepository: MusicRepository,
    private val exports: CloudDownloadExports
) : ViewModel() {
    val exportFolder = exports.folder
    private val _feedback = MutableSharedFlow<CloudDownloadFeedback>()
    val feedback = _feedback.asSharedFlow()
    private val _queueingAll = MutableStateFlow(false)
    val queueingAll = _queueingAll.asStateFlow()

    val uiState: StateFlow<CloudDownloadsUiState> = repository.observeAll()
        .map(List<OfflineDownload>::toCloudDownloadsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CloudDownloadsUiState()
        )

    fun remove(download: OfflineDownload) {
        viewModelScope.launch { repository.remove(download.sourceUri) }
    }

    fun retry(download: OfflineDownload) {
        if (download.status != OfflineDownloadStatus.FAILED) return
        viewModelScope.launch { repository.retry(download.sourceUri) }
    }

    fun downloadSelected(songs: List<Song>) {
        val cloudSongs = CloudOfflineRepository.downloadCandidates(songs)
        if (cloudSongs.isEmpty()) return
        viewModelScope.launch { repository.enqueueAll(cloudSongs) }
    }

    fun downloadAll() {
        if (!_queueingAll.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val tracks = CloudOfflineRepository.downloadCandidates(musicRepository.getAllSongsOnce())
                val count = if (tracks.isEmpty()) 0 else repository.enqueueAll(tracks)
                _feedback.emit(
                    when {
                        tracks.isEmpty() -> CloudDownloadFeedback(R.string.cloud_download_no_synced_tracks)
                        count == 0 -> CloudDownloadFeedback(R.string.cloud_download_nothing_to_queue)
                        else -> CloudDownloadFeedback(R.string.cloud_download_batch_queued, count)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _feedback.emit(CloudDownloadFeedback(R.string.cloud_download_batch_failed))
            } finally {
                _queueingAll.value = false
            }
        }
    }

    fun selectExportFolder(uri: Uri) = updateExportFolder { exports.selectFolder(uri) }

    fun clearExportFolder() = updateExportFolder { exports.clearFolder() }

    fun export(download: OfflineDownload) {
        val file = download.localPath?.let(::File) ?: return
        viewModelScope.launch {
            val result = exports.export(file, download.title, download.downloadId)
            _feedback.emit(
                CloudDownloadFeedback(
                    if (result == CloudExportResult.EXPORTED) R.string.cloud_download_export_completed
                    else R.string.cloud_download_export_failed
                )
            )
        }
    }

    private fun updateExportFolder(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _feedback.emit(CloudDownloadFeedback(R.string.cloud_download_export_folder_failed))
            }
        }
    }
}

data class CloudDownloadFeedback(@StringRes val message: Int, val count: Int? = null)
