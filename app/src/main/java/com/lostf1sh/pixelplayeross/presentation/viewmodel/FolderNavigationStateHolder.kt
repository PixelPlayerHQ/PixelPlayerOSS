package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.os.Environment
import com.lostf1sh.pixelplayeross.data.model.MusicFolder
import com.lostf1sh.pixelplayeross.data.model.Song
import dagger.hilt.android.scopes.ViewModelScoped
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ViewModelScoped
class FolderNavigationStateHolder @Inject constructor() {
    fun setFoldersPlaylistViewState(
        isPlaylistView: Boolean,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit)
    ) {
        updateUiState { currentState ->
            if (currentState.isFoldersPlaylistView == isPlaylistView) {
                return@updateUiState currentState
            }
            currentState.copy(
                isFoldersPlaylistView = isPlaylistView,
                currentFolderPath = null,
                currentFolder = null
            )
        }
    }

    fun navigateToFolder(
        path: String,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        cachedSongsById: () -> Map<String, Song>,
        onFolderChanged: (String) -> Unit
    ) {
        val storageRootPath = getUiState().folderSourceRootPath.ifBlank {
            Environment.getExternalStorageDirectory().path
        }
        if (path == storageRootPath) {
            updateUiState {
                it.copy(
                    currentFolderPath = null,
                    currentFolder = null
                )
            }
            return
        }

        val folder = findFolder(path, getUiState().musicFolders)?.withCachedSongs(cachedSongsById())
        if (folder != null) {
            updateUiState {
                it.copy(
                    currentFolderPath = path,
                    currentFolder = folder
                )
            }
            onFolderChanged(path)
        }
    }

    fun navigateBackFolder(
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        cachedSongsById: () -> Map<String, Song>,
        onFolderChanged: (String) -> Unit
    ) {
        val state = getUiState()
        val currentFolder = state.currentFolder ?: return
        val parentPath = File(currentFolder.path).parent
        val sourceRoot = state.folderSourceRootPath.ifBlank {
            Environment.getExternalStorageDirectory().path
        }
        if (parentPath == null || parentPath == sourceRoot) {
            updateUiState {
                it.copy(
                    currentFolderPath = null,
                    currentFolder = null
                )
            }
            return
        }

        val parentFolder = findFolder(parentPath, state.musicFolders)?.withCachedSongs(cachedSongsById())
        updateUiState {
            it.copy(
                currentFolderPath = parentPath,
                currentFolder = parentFolder
            )
        }
        onFolderChanged(parentPath)
    }

    fun hydrateCurrentFolderSongsIfNeeded(
        scope: CoroutineScope,
        folderPath: String,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        requiresHydration: (Song) -> Boolean,
        hydrateSongs: suspend (List<Song>) -> List<Song>
    ) {
        scope.launch {
            val currentFolder = getUiState().currentFolder ?: return@launch
            if (currentFolder.path != folderPath || currentFolder.songs.isEmpty()) return@launch
            val currentSongs = currentFolder.songs
            if (currentSongs.none(requiresHydration)) return@launch
            val hydratedSongs = hydrateSongs(currentSongs)
            if (hydratedSongs.isEmpty()) return@launch
            updateUiState { state ->
                if (state.currentFolder?.path != folderPath) return@updateUiState state
                state.copy(
                    currentFolder = state.currentFolder.copy(songs = hydratedSongs.toImmutableList())
                )
            }
        }
    }

    // The folder tree only holds title-only stubs. Swapping in the full songs the library already
    // has lets a metadata sort show its final order from the first frame instead of re-sorting
    // once the stubs are hydrated; songs missing from the cache are still hydrated afterwards.
    private fun MusicFolder.withCachedSongs(songsById: Map<String, Song>): MusicFolder {
        if (songs.isEmpty() || songsById.isEmpty()) return this
        return copy(songs = songs.map { stub -> songsById[stub.id] ?: stub }.toImmutableList())
    }

    private fun findFolder(path: String?, folders: List<MusicFolder>): MusicFolder? {
        if (path == null) return null
        val queue = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.remove()
            if (folder.path == path) return folder
            queue.addAll(folder.subFolders)
        }
        return null
    }
}
