package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionActionRow
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionCountPill
import com.lostf1sh.pixelplayeross.presentation.selection.DetailSongSelection
import com.lostf1sh.pixelplayeross.presentation.viewmodel.CloudDownloadsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal val DetailSongSelectionControlsHeight = 112.dp

@Composable
internal fun <K> DetailSongSelectionControls(
    selection: DetailSongSelection<K>,
    visibleSongs: () -> Map<K, Song>,
    favoriteSongIds: Set<String>,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    currentPlaylistId: String? = null,
    cloudDownloadsViewModel: CloudDownloadsViewModel = hiltViewModel()
) {
    var showOptions by remember(selection) { mutableStateOf(false) }
    var showBatchEdit by remember(selection) { mutableStateOf(false) }
    var playlistSheetSongs by remember(selection) { mutableStateOf<ImmutableList<Song>?>(null) }
    val isActive = selection.isActive

    LaunchedEffect(isActive) {
        if (!isActive) {
            showOptions = false
            showBatchEdit = false
        }
    }

    if (isActive) {
        val rows = visibleSongs()
        LaunchedEffect(rows) {
            selection.keepVisible(rows.keys)
        }

        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelectionCountPill(selectedCount = selection.keys.size)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 8.dp
            ) {
                SelectionActionRow(
                    selectedCount = selection.keys.size,
                    onSelectAll = { selection.selectAll(rows.keys) },
                    onDeselect = selection::clear,
                    onOptionsClick = { showOptions = true },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (showOptions || showBatchEdit) {
            val keys = selection.keys
            val songs = remember(keys, rows) { selection.songsIn(rows) }
            val uniqueSongs = remember(songs) { songs.distinctBy(Song::id).toImmutableList() }
            val context = LocalContext.current
            if (showOptions && songs.isNotEmpty()) {
                val range = remember(keys, rows) { selection.rangeIn(rows.keys.toList()) }
                MultiSelectionBottomSheet(
                    selectedSongs = songs,
                    favoriteSongIds = favoriteSongIds,
                    onDismiss = { showOptions = false },
                    onPlayAll = { playerViewModel.playSelectedSongs(songs, selection.completionCallback()) },
                    onAddToQueue = { playerViewModel.addSelectedToQueue(songs, selection.completionCallback()) },
                    onPlayNext = { playerViewModel.addSelectedAsNext(songs, selection.completionCallback()) },
                    onAddToPlaylist = {
                        playlistSheetSongs = songs
                        selection.clear()
                    },
                    onToggleLikeAll = { shouldLike ->
                        val onFinished = selection.completionCallback()
                        if (shouldLike) {
                            playerViewModel.likeSelectedSongs(uniqueSongs, onFinished)
                        } else {
                            playerViewModel.unlikeSelectedSongs(uniqueSongs, onFinished)
                        }
                    },
                    onShareAll = { playerViewModel.shareSelectedAsZip(uniqueSongs, selection.completionCallback()) },
                    onDownloadAll = {
                        val count = CloudOfflineRepository.downloadCandidates(uniqueSongs).size
                        cloudDownloadsViewModel.downloadSelected(uniqueSongs)
                        selection.clear()
                        playerViewModel.sendToast(
                            context.resources.getQuantityString(R.plurals.cloud_download_selected_started, count, count)
                        )
                    },
                    onDeleteAll = { activity, onComplete ->
                        playerViewModel.deleteSelectedFromDevice(activity, uniqueSongs, selection.completionCallback()) {
                            showOptions = false
                            onComplete(true)
                        }
                    },
                    onBatchEdit = {
                        showOptions = false
                        showBatchEdit = true
                    },
                    onSelectBetween = range?.let { { selection.selectAll(it) } }
                )
            }

            if (showBatchEdit && uniqueSongs.isNotEmpty()) {
                EditMultipleSongsSheet(
                    visible = true,
                    songs = uniqueSongs,
                    onDismiss = { showBatchEdit = false },
                    onSave = { selectedSongs, title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, trackGain, albumGain, coverArtUpdate ->
                        playerViewModel.saveBatchMetadata(
                            selectedSongs, title, artist, album, albumArtist, composer, genre, lyrics,
                            trackNumber, discNumber, trackGain, albumGain, coverArtUpdate, selection.completionCallback()
                        )
                    }
                )
            }
        }
    }

    playlistSheetSongs?.let { songs ->
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = songs,
            onDismiss = { playlistSheetSongs = null },
            currentPlaylistId = currentPlaylistId,
            bottomBarHeight = bottomBarHeight,
            playerViewModel = playerViewModel
        )
    }
}
