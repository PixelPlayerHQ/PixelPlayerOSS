package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylist
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.LibraryActionRow
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistBottomSheet(
    playlistUiState: PlaylistUiState,
    songs: List<Song>,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    currentPlaylistId: String? = null
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var searchQuery by remember { mutableStateOf("") }
    val editablePlaylists = remember(playlistUiState.playlists) {
        playlistUiState.playlists.filterNot { it.isSmartPlaylist }
    }
    val filteredPlaylists = remember(searchQuery, editablePlaylists) {
        if (searchQuery.isBlank()) editablePlaylists
        else editablePlaylists.filter { it.name.contains(searchQuery, true) }
    }
    // Stable map across recompositions; PlaylistContainer mutates it on checkbox toggles.
    val selectedPlaylists = remember { mutableStateMapOf<String, Boolean>() }

    // Reconcile entries when the playlist set changes (async load, in-sheet creation,
    // or search filtering). Only seed playlist ids that have no entry yet, so existing
    // user toggles are preserved, while newly appearing playlists get their initial
    // (and, for the single-song case, "already contains this song") state evaluated.
    LaunchedEffect(filteredPlaylists, songs) {
        val singleSongId = songs.singleOrNull()?.id
        filteredPlaylists.forEach { playlist ->
            if (playlist.id !in selectedPlaylists) {
                selectedPlaylists[playlist.id] =
                    singleSongId != null && playlist.songIds.contains(singleSongId)
            }
        }
    }

    val isAnyPlaylistSelected = selectedPlaylists.values.any { it }

    val alpha by animateFloatAsState(
        targetValue = if (isAnyPlaylistSelected) 1f else 0.4f,
        label = "fab_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets } // Handle insets such as the keyboard
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (songs.size > 1) {
                            stringResource(R.string.playlist_picker_add_songs_title, songs.size)
                        } else {
                            stringResource(R.string.playlist_picker_select_playlists)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = {
                            searchQuery = ""
                        }) { Icon(Icons.Filled.Clear, null) }
                    }
                )




                LibraryActionRow(
                    modifier = Modifier.padding(
                        top = 10.dp,
                        start = 10.dp,
                        end = 10.dp
                    ),
                    //currentPage = pagerState.currentPage,
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
                    currentSong = songs.firstOrNull() ?: Song.emptySong(), // Fallback safe
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists
                )

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialogRedesigned(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name ->
                            // Pass all selected songs to the new playlist
                            playlistViewModel.createPlaylist(name, songIds = songs.map { it.id })
                            showCreatePlaylistDialog = false
                            onDismiss() // Close sheet after creation + add
                            playerViewModel.sendToast("Playlist created and songs added")
                        }
                    )
                }
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                    },
                shape = CircleShape,
                onClick = {
                    if (!isAnyPlaylistSelected) return@MediumExtendedFloatingActionButton

                    if (songs.size == 1) {
                         playlistViewModel.addOrRemoveSongFromPlaylists(
                            songs.first().id,
                            selectedPlaylists.filter { it.value }.keys.toList(),
                            currentPlaylistId
                        )
                    } else {
                         // Batch add
                         val selectedPlaylistIds = selectedPlaylists.filter { it.value }.keys.toList()
                         if (selectedPlaylistIds.isNotEmpty()) {
                             playlistViewModel.addSongsToPlaylists(
                                 songs.map { it.id },
                                 selectedPlaylistIds
                             )
                         }
                    }
                    onDismiss()
                    playerViewModel.sendToast(if (songs.size > 1) "Songs added to playlists" else "Saved")
                    playerViewModel.multiSelectionStateHolder.clearSelection()
                },
                icon = { Icon(Icons.Rounded.Save, "Save") },
                text = { Text(if (songs.size > 1) "Add" else "Save") },
            )
        }
    }
}
