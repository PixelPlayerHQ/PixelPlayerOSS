package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.media3.session.MediaController
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ViewModelScoped
class QueueUndoStateHolder @Inject constructor() {
    private var queueItemUndoTimerJob: Job? = null

    fun removeSongFromQueue(
        scope: CoroutineScope,
        mediaController: MediaController?,
        songId: String,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
    ) {
        val controller = mediaController ?: return
        
        // Find index in CONTROLLER to ensure we remove the correct item,
        // even if the UI state is slightly out of sync with the player.
        var indexToRemove = -1
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == songId) {
                indexToRemove = i
                break
            }
        }
        
        if (indexToRemove == -1) return

        val currentQueue = getUiState().currentPlaybackQueue
        val removedSong = currentQueue.find { it.id == songId } ?: return

        controller.removeMediaItem(indexToRemove)

        updateUiState {
            it.copy(
                showQueueItemUndoBar = true,
                lastRemovedQueueSong = removedSong,
                lastRemovedQueueIndex = indexToRemove
            )
        }

        queueItemUndoTimerJob?.cancel()
        queueItemUndoTimerJob = scope.launch {
            delay(4000L)
            if (getUiState().showQueueItemUndoBar) {
                updateUiState {
                    it.copy(
                        showQueueItemUndoBar = false,
                        lastRemovedQueueSong = null,
                        lastRemovedQueueIndex = -1
                    )
                }
            }
        }
    }

    fun undoRemoveSongFromQueue(
        mediaController: MediaController?,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
    ) {
        val uiState = getUiState()
        val song = uiState.lastRemovedQueueSong ?: return
        val index = uiState.lastRemovedQueueIndex
        if (index < 0) return

        mediaController?.let { controller ->
            val mediaItem = MediaItemBuilder.build(song)
            val insertAt = index.coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, mediaItem)
        }

        hideQueueItemUndoBar(updateUiState)
    }

    fun hideQueueItemUndoBar(
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
    ) {
        queueItemUndoTimerJob?.cancel()
        updateUiState {
            it.copy(
                showQueueItemUndoBar = false,
                lastRemovedQueueSong = null,
                lastRemovedQueueIndex = -1
            )
        }
    }

    fun onCleared() {
        queueItemUndoTimerJob?.cancel()
    }
}
