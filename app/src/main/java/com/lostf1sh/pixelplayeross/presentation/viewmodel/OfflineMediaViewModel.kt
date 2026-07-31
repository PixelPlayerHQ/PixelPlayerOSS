package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.offline.CachedCollectionType
import com.lostf1sh.pixelplayeross.data.offline.OfflineCacheRequestResult
import com.lostf1sh.pixelplayeross.data.offline.OfflineMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OfflineMediaViewModel @Inject constructor(
    private val repository: OfflineMediaRepository,
) : ViewModel() {
    val uiState = repository.uiState

    fun isCached(type: CachedCollectionType, sourceId: String, songs: List<Song>): Boolean =
        repository.isCollectionCached(type, sourceId, songs)

    fun cache(
        type: CachedCollectionType,
        sourceId: String,
        title: String,
        subtitle: String?,
        artworkUri: String?,
        songs: List<Song>,
        onResult: (OfflineCacheRequestResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            onResult(repository.cacheCollection(type, sourceId, title, subtitle, artworkUri, songs))
        }
    }

    fun remove(collectionId: String) {
        viewModelScope.launch { repository.removeCollection(collectionId) }
    }

    fun remove(type: CachedCollectionType, sourceId: String, songs: List<Song>) {
        remove(repository.collectionId(type, sourceId, songs))
    }
}
