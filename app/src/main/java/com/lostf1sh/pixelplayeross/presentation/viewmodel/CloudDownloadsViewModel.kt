package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownload
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import com.lostf1sh.pixelplayeross.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val repository: CloudOfflineRepository
) : ViewModel() {
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
}
