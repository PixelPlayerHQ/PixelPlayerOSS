package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
internal data class LibrarySongPlaybackUiState(
    val isCurrentSong: Boolean = false,
    val isPlaying: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    song: Song,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    albumArtSize: Dp = 50.dp,
    customShape: Shape? = null,
    showAlbumArt: Boolean = true,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    isCurrentSong: Boolean? = null,
    isPlaying: Boolean? = null,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val effectiveIsCurrentSong: Boolean
    val effectiveIsPlaying: Boolean

    if (isCurrentSong != null && isPlaying != null) {
        effectiveIsCurrentSong = isCurrentSong
        effectiveIsPlaying = isPlaying
    } else {
        val playbackUiState by remember(song.id, playerViewModel) {
            playerViewModel.stablePlayerState
                .map { state ->
                    val isCurrent = state.currentSong?.id == song.id
                    LibrarySongPlaybackUiState(
                        isCurrentSong = isCurrent,
                        isPlaying = isCurrent && state.isPlaying
                    )
                }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())
        effectiveIsCurrentSong = playbackUiState.isCurrentSong
        effectiveIsPlaying = playbackUiState.isPlaying
    }

    EnhancedSongListItem(
        modifier = modifier,
        song = song,
        customShape = customShape,
        isPlaying = effectiveIsPlaying,
        isCurrentSong = effectiveIsCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        showAlbumArt = showAlbumArt,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        onClick = onClick
    )
}
