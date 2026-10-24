package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State holder for multi-selection functionality in LibraryScreen tabs.
 * Manages selection state with order preservation using a LinkedHashSet internally.
 *
 * Selection order is maintained - the first selected song is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class MultiSelectionStateHolder @Inject constructor() {

    private val _selectedSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    
    /**
     * Immutable flow of selected songs, preserving selection order.
     */
    val selectedSongs: StateFlow<ImmutableList<Song>> = _selectedSongs.asStateFlow()
    
    /**
     * Set of selected song IDs for efficient lookup.
     */
    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one song selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected songs.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * The selection as it was before the current drag-to-select gesture changed it, so songs the
     * drag deselects and then gives back keep their place in the order. Every drag starts with the
     * long press that toggles its first song, and any change other than a drag step resets this.
     */
    private var dragSelectionOrigin: List<Song>? = null

    /**
     * Toggles the selection state of a song.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param song The song to toggle
     */
    fun toggleSelection(song: Song) {
        val currentList = _selectedSongs.value.toMutableList()
        val currentIds = _selectedSongIds.value.toMutableSet()
        
        if (currentIds.contains(song.id)) {
            currentList.removeAll { it.id == song.id }
            currentIds.remove(song.id)
        } else {
            currentList.add(song)
            currentIds.add(song.id)
        }
        
        updateState(currentList, currentIds)
    }

    /**
     * Selects all songs from the provided list.
     * Previously selected songs that are in the new list maintain their position.
     * New songs are appended in their list order.
     *
     * @param songs The complete list of songs to select
     */
    fun selectAll(songs: List<Song>) {
        val currentIds = _selectedSongIds.value
        val currentList = _selectedSongs.value.toMutableList()
        
        songs.forEach { song ->
            if (!currentIds.contains(song.id)) {
                currentList.add(song)
            }
        }
        
        val newIds = currentList.map { it.id }.toSet()
        updateState(currentList, newIds)
    }

    /**
     * Replaces any previous selection with a resolved album/artist song set.
     * Duplicate IDs are removed while the first occurrence and its order are retained.
     */
    fun replaceSelection(songs: List<Song>) {
        val uniqueSongs = songs.distinctBy { it.id }
        updateState(uniqueSongs, uniqueSongs.mapTo(linkedSetOf()) { it.id })
    }

    /**
     * Applies one step of a drag-to-select gesture as a single state update.
     * Songs in [deselect] are removed first. Songs in [select] that were selected before the drag
     * started (a deselecting drag that is dragged back) return to their old place in the order;
     * the others are appended in the given order, so selection badges follow the finger.
     *
     * @param select Songs the drag has just selected
     * @param deselect Songs the drag has just deselected
     */
    fun applyDragSelection(select: List<Song>, deselect: List<Song>) {
        if (select.isEmpty() && deselect.isEmpty()) return
        val currentList = _selectedSongs.value
        val origin = dragSelectionOrigin ?: currentList
        val originIds = origin.mapTo(hashSetOf()) { it.id }
        val removedIds = deselect.mapTo(hashSetOf()) { it.id }
        val keptIds = currentList.mapNotNullTo(hashSetOf()) { song -> song.id.takeUnless { it in removedIds } }
        val restoredIds = select.mapNotNullTo(hashSetOf()) { song -> song.id.takeIf { it in originIds } }

        // Songs selected before the drag keep their relative order; songs the drag added follow.
        val songs = origin.filterTo(ArrayList()) { it.id in keptIds || it.id in restoredIds }
        currentList.filterTo(songs) { it.id in keptIds && it.id !in originIds }
        val ids = songs.mapTo(linkedSetOf()) { it.id }
        select.forEach { song ->
            if (ids.add(song.id)) {
                songs.add(song)
            }
        }

        if (ids != _selectedSongIds.value) {
            updateState(songs, ids)
        }
        dragSelectionOrigin = origin
    }

    /**
     * Clears all selected songs, exiting selection mode.
     */
    fun clearSelection() {
        updateState(emptyList(), emptySet())
    }

    /**
     * Checks if a song is currently selected.
     *
     * @param songId The ID of the song to check
     * @return True if the song is selected, false otherwise
     */
    fun isSelected(songId: String): Boolean {
        return _selectedSongIds.value.contains(songId)
    }

    /**
     * Gets the selection index (1-based) of a song for display purposes.
     * Returns null if the song is not selected.
     *
     * @param songId The ID of the song
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(songId: String): Int? {
        val index = _selectedSongs.value.indexOfFirst { it.id == songId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Updates all state flows atomically.
     */
    private fun updateState(songs: List<Song>, ids: Set<String>) {
        dragSelectionOrigin = null
        _selectedSongs.value = songs.toImmutableList()
        _selectedSongIds.value = ids
        _selectedCount.value = songs.size
        _isSelectionMode.value = songs.isNotEmpty()
    }
}
