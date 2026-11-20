package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylist
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.LibraryActionRow
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    playlistUiState: PlaylistUiState,
    songs: ImmutableList<Song>,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    currentPlaylistId: String? = null
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    val editablePlaylists = remember(playlistUiState.playlists) {
        playlistUiState.playlists.filterNot { it.isSmartPlaylist }.toImmutableList()
    }
    val filteredPlaylists = remember(searchQuery, editablePlaylists) {
        if (searchQuery.isBlank()) editablePlaylists
        else editablePlaylists.filter { it.name.contains(searchQuery, true) }.toImmutableList()
    }
    val singleSong = songs.singleOrNull()
    val initialPlaylistIds = remember(editablePlaylists, singleSong?.id) {
        if (singleSong == null) emptySet()
        else buildSet {
            editablePlaylists.forEach { playlist ->
                if (singleSong.id in playlist.songIds) add(playlist.id)
            }
        }
    }
    val selectionEdits = rememberPlaylistPickerSelectionEdits(songs)
    val edits = selectionEdits.toMap()
    val selection = remember(initialPlaylistIds, edits, songs.size) {
        PlaylistPickerSelection(initialPlaylistIds, edits, songs.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val playlistCreatedToast = stringResource(R.string.playlist_created_songs_added_toast)
            val songsAddedToast = stringResource(R.string.playlist_songs_added_toast)
            val savedToast = stringResource(R.string.playlist_saved_toast)

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (songs.size > 1) {
                            stringResource(R.string.playlist_picker_add_songs_title, songs.size)
                        } else {
                            stringResource(R.string.playlist_picker_select_playlists)
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.cd_close))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            unfocusedTrailingIconColor = Color.Transparent,
                            focusedSupportingTextColor = Color.Transparent,
                        ),
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.search_playlists_hint)) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = {
                                searchQuery = ""
                            }) { Icon(Icons.Filled.Clear, null) }
                        }
                    )
                    val layoutDescription = stringResource(
                        if (isGridView) R.string.presentation_batch_d_view_grid
                        else R.string.presentation_batch_d_view_list
                    )
                    FilledTonalIconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.semantics { stateDescription = layoutDescription }
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.cd_toggle_grid_list)
                        )
                    }
                }
                LibraryActionRow(
                    modifier = Modifier.padding(
                        top = 10.dp,
                        start = 10.dp,
                        end = 10.dp
                    ),
                    onMainActionClick = {
                        showCreatePlaylistDialog = true
                    },
                    iconRotation = 0f,
                    showSortButton = false,
                    showImportButton = false,
                    onSortClick = { },
                    isPlaylistTab = true,
                    isFoldersTab = false,
                    currentFolder = null,
                    folderRootPath = "",
                    folderRootLabel = "Internal Storage",
                    onFolderClick = { },
                    onNavigateBack = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlaylistContainer(
                    playlistUiState = playlistUiState,
                    isRefreshing = false,
                    onRefresh = { },
                    bottomBarHeight = bottomBarHeight,
                    navController = null,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylistIds = selection.selectedPlaylistIds,
                    onPlaylistSelectionToggle = { playlist ->
                        val initiallySelected = playlist.id in initialPlaylistIds
                        val selected = !(selectionEdits[playlist.id] ?: initiallySelected)
                        if (selected == initiallySelected) {
                            selectionEdits.remove(playlist.id)
                        } else {
                            selectionEdits[playlist.id] = selected
                        }
                    },
                    isGridView = isGridView
                )

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialogRedesigned(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name ->
                            playlistViewModel.createPlaylist(name, songIds = songs.map { it.id })
                            showCreatePlaylistDialog = false
                            onDismiss()
                            playerViewModel.sendToast(playlistCreatedToast)
                            playerViewModel.multiSelectionStateHolder.clearSelection()
                        }
                    )
                }
            }

            Button(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .heightIn(min = 56.dp),
                shape = CircleShape,
                enabled = selection.canSave,
                onClick = {
                    val selectedPlaylistIds = selection.selectedPlaylistIds.toList()
                    if (singleSong != null) {
                        playlistViewModel.addOrRemoveSongFromPlaylists(
                            singleSong.id,
                            selectedPlaylistIds,
                            currentPlaylistId
                        )
                    } else {
                        playlistViewModel.addSongsToPlaylists(songs.map { it.id }, selectedPlaylistIds)
                    }
                    onDismiss()
                    playerViewModel.sendToast(if (songs.size > 1) songsAddedToast else savedToast)
                    playerViewModel.multiSelectionStateHolder.clearSelection()
                }
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (songs.size > 1) R.string.song_picker_action_add else R.string.action_save))
            }
        }
    }
}

@Composable
internal fun rememberPlaylistPickerSelectionEdits(
    songs: ImmutableList<Song>
): SnapshotStateMap<String, Boolean> {
    val songIds = remember(songs) {
        buildSet(songs.size) { songs.forEach { add(it.id) } }
    }
    return remember(songIds) { mutableStateMapOf() }
}

internal class PlaylistPickerSelection(
    initialPlaylistIds: Set<String>,
    edits: Map<String, Boolean>,
    songCount: Int
) {
    val selectedPlaylistIds = buildSet {
        addAll(initialPlaylistIds)
        edits.forEach { (id, selected) ->
            if (selected) add(id) else remove(id)
        }
    }

    val canSave = when (songCount) {
        0 -> false
        1 -> selectedPlaylistIds != initialPlaylistIds
        else -> selectedPlaylistIds.isNotEmpty()
    }
}
