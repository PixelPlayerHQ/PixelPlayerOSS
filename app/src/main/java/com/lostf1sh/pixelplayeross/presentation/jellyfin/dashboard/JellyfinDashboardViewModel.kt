package com.lostf1sh.pixelplayeross.presentation.jellyfin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.database.JellyfinPlaylistEntity
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@HiltViewModel
class JellyfinDashboardViewModel @Inject constructor(
    private val repository: JellyfinRepository
) : ViewModel() {

    val playlists: StateFlow<ImmutableList<JellyfinPlaylistEntity>> = repository.getPlaylists()
        .map { it.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val selectedPlaylistSongs: StateFlow<ImmutableList<Song>> = _selectedPlaylistSongs.asStateFlow()

    private val _libraries = MutableStateFlow<ImmutableList<JellyfinLibrary>>(persistentListOf())
    val libraries: StateFlow<ImmutableList<JellyfinLibrary>> = _libraries.asStateFlow()

    private val _librariesLoadFailed = MutableStateFlow(false)
    val librariesLoadFailed: StateFlow<Boolean> = _librariesLoadFailed.asStateFlow()

    val selectedLibraryIds: StateFlow<Set<String>> = repository.selectedLibraryIdsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _librarySelectionNeedsSync = MutableStateFlow(false)
    val librarySelectionNeedsSync: StateFlow<Boolean> = _librarySelectionNeedsSync.asStateFlow()

    val username: String? get() = repository.username
    val serverUrl: String? get() = repository.serverUrl
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    init {
        loadLibraries()
        if (repository.isFullSyncDue) {
            syncAllPlaylistsAndSongs()
        }
    }

    fun loadLibraries() {
        viewModelScope.launch {
            repository.getLibraries()
                .onSuccess { libraries ->
                    _libraries.value = libraries.toImmutableList()
                    _librariesLoadFailed.value = false
                }
                .onFailure {
                    _libraries.value = persistentListOf()
                    _librariesLoadFailed.value = true
                }
        }
    }

    fun setSelectedLibraryIds(libraryIds: Set<String>) {
        viewModelScope.launch {
            repository.setSelectedLibraryIds(libraryIds)
            _librarySelectionNeedsSync.value = true
        }
    }

    fun syncAllPlaylistsAndSongs() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing all playlists and songs..."
            val result = repository.syncAllPlaylistsAndSongs()
            result.fold(
                onSuccess = { summary ->
                    _librarySelectionNeedsSync.value = false
                    _syncMessage.value = if (summary.failedPlaylistCount == 0) {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs"
                    } else {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs (${summary.failedPlaylistCount} failed)"
                    }
                },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing playlists..."
            val result = repository.syncPlaylists()
            result.fold(
                onSuccess = { _syncMessage.value = "Synced ${it.size} playlists" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing songs..."
            val result = repository.syncPlaylistSongs(playlistId)
            result.fold(
                onSuccess = { count ->
                    try {
                        repository.syncUnifiedLibrarySongsFromJellyfin()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to sync unified library after playlist sync")
                    }
                    _syncMessage.value = "Synced $count songs"
                },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            repository.getPlaylistSongs(playlistId).collect { songs ->
                _selectedPlaylistSongs.value = songs.toImmutableList()
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
