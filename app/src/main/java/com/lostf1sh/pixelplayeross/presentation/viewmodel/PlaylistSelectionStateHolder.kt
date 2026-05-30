package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State holder for multi-selection functionality for playlists in LibraryScreen.
 * Manages playlist selection state with order preservation.
 *
 * Selection order is maintained - the first selected playlist is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class PlaylistSelectionStateHolder @Inject constructor() {

    // Internal mutable state - uses List to preserve selection order
    private val _selectedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    
    /**
     * Immutable flow of selected playlists, preserving selection order.
     */
    val selectedPlaylists: StateFlow<List<Playlist>> = _selectedPlaylists.asStateFlow()
    
    /**
     * Set of selected playlist IDs for efficient lookup.
     */
    private val _selectedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylistIds: StateFlow<Set<String>> = _selectedPlaylistIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one playlist selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected playlists.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * Toggles the selection state of a playlist.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param playlist The playlist to toggle
     */
    fun toggleSelection(playlist: Playlist) {
        val currentList = _selectedPlaylists.value.toMutableList()
        val currentIds = _selectedPlaylistIds.value.toMutableSet()
        
        if (currentIds.contains(playlist.id)) {
            // Remove from selection
            currentList.removeAll { it.id == playlist.id }
            currentIds.remove(playlist.id)
        } else {
            // Add to selection (preserving order)
            currentList.add(playlist)
            currentIds.add(playlist.id)
        }
        
        updateState(currentList, currentIds)
    }

    /**
     * Selects all playlists from the provided list.
     * Previously selected playlists that are in the new list maintain their position.
     * New playlists are appended in their list order.
     *
     * @param playlists The complete list of playlists to select
     */
    fun selectAll(playlists: List<Playlist>) {
        val currentIds = _selectedPlaylistIds.value
        val currentList = _selectedPlaylists.value.toMutableList()
        
        // Add playlists that aren't already selected
        playlists.forEach { playlist ->
            if (!currentIds.contains(playlist.id)) {
                currentList.add(playlist)
            }
        }
        
        val newIds = currentList.map { it.id }.toSet()
        updateState(currentList, newIds)
    }

    /**
     * Clears all selected playlists, exiting selection mode.
     */
    fun clearSelection() {
        updateState(emptyList(), emptySet())
    }

    /**
     * Checks if a playlist is currently selected.
     *
     * @param playlistId The ID of the playlist to check
     * @return True if the playlist is selected, false otherwise
     */
    fun isSelected(playlistId: String): Boolean {
        return _selectedPlaylistIds.value.contains(playlistId)
    }

    /**
     * Gets the selection index (1-based) of a playlist for display purposes.
     * Returns null if the playlist is not selected.
     *
     * @param playlistId The ID of the playlist
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(playlistId: String): Int? {
        val index = _selectedPlaylists.value.indexOfFirst { it.id == playlistId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Removes a specific playlist from selection if it exists.
     * Useful when a playlist is deleted.
     *
     * @param playlistId The ID of the playlist to remove
     */
    fun removeFromSelection(playlistId: String) {
        if (!_selectedPlaylistIds.value.contains(playlistId)) return
        
        val currentList = _selectedPlaylists.value.filter { it.id != playlistId }
        val currentIds = _selectedPlaylistIds.value - playlistId
        updateState(currentList, currentIds)
    }

    /**
     * Updates all state flows atomically.
     */
    private fun updateState(playlists: List<Playlist>, ids: Set<String>) {
        _selectedPlaylists.value = playlists
        _selectedPlaylistIds.value = ids
        _selectedCount.value = playlists.size
        _isSelectionMode.value = playlists.isNotEmpty()
    }
}
