package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.playlist.M3uSyncRepository
import com.lostf1sh.pixelplayeross.data.playlist.M3uSyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class M3uSyncViewModel @Inject constructor(
    private val repository: M3uSyncRepository,
) : ViewModel() {
    val state: StateFlow<M3uSyncState> = repository.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun selectFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.configure(uri) }
                .onFailure { error ->
                    _messages.emit(error.message ?: "Unable to use the selected folder")
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            runCatching { repository.syncNow() }
                .onFailure { error ->
                    _messages.emit(error.message ?: "Playlist synchronization failed")
                }
        }
    }

    fun disable() {
        viewModelScope.launch {
            runCatching { repository.disable() }
                .onFailure { error ->
                    _messages.emit(error.message ?: "Unable to disable playlist synchronization")
                }
        }
    }
}
