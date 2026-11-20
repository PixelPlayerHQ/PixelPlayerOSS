package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SineWaveLine
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.utils.SegmentedItemShapeDefaults
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedItemShape
import com.lostf1sh.pixelplayeross.presentation.utils.seamlessSelectionScale
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistSelectionStateHolder
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.ui.theme.LocalShowScrollbar
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.flow.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistContainer(
    playlistUiState: PlaylistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: ImmutableList<Playlist> = playlistUiState.playlists,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistSelectionStateHolder: PlaylistSelectionStateHolder? = null,
    isGridView: Boolean = false
) {

    Column(modifier = Modifier.fillMaxSize()) {
        if (playlistUiState.isLoading && filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { ContainedLoadingIndicator() }
        }

        if (filteredPlaylists.isEmpty() && !playlistUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        alpha = 0.95f,
                        strokeWidth = 3.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_no_playlist_created),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_new_playlist_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val playlistItems: @Composable () -> Unit = {
                if (isGridView) {
                    PlaylistGridItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isAddingToPlaylist = isAddingToPlaylist,
                        isSelectionMode = isSelectionMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                } else {
                    PlaylistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isAddingToPlaylist = isAddingToPlaylist,
                        isSelectionMode = isSelectionMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                }
            }
            if (isAddingToPlaylist) {
                playlistItems()
            } else {
                val playlistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = playlistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = playlistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    playlistItems()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: ImmutableList<Playlist>,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val listState = rememberLazyListState()
    val playlistFastScrollLabelProvider = remember(filteredPlaylists, currentSortOption) {
        { index: Int ->
            playlistFastScrollLabel(
                playlist = filteredPlaylists.getOrNull(index),
                sortOption = currentSortOption
            )
        }
    }
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }
    var pendingPlaylistSortScrollReset by remember { mutableStateOf(false) }

    LaunchedEffect(currentSortOption) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        pendingPlaylistSortScrollReset = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(filteredPlaylists, pendingPlaylistSortScrollReset) {
        if (!pendingPlaylistSortScrollReset) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingPlaylistSortScrollReset = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward), 22.dp, 12.dp), bottom = 6.dp)
                .fillMaxSize()
                .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
        ) {
            itemsIndexed(filteredPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                val selectionIndex = remember(playlist.id, isSelectionMode, selectedPlaylistIds) {
                    if (isSelectionMode && playlist.id in selectedPlaylistIds) {
                        selectedPlaylistIds.toList().indexOf(playlist.id)
                    } else {
                        -1
                    }
                }
                PlaylistItem(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    onClick = {
                        if (isAddingToPlaylist || isSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    },
                    isAddingToPlaylist = isAddingToPlaylist,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPlaylistIds.contains(playlist.id),
                    selectionIndex = selectionIndex,
                    customShape = getSegmentedItemShape(index, filteredPlaylists.size),
                    onLongPress = { onPlaylistLongPress(playlist) }
                )
            }
        }
        
        val bottomPadding = if (hasCurrentSong) {
            bottomBarHeight + MiniPlayerHeight + 16.dp
        } else {
            bottomBarHeight + 16.dp
        }

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            listState = listState,
            dragLabelProvider = playlistFastScrollLabelProvider
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlaylistGridItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    filteredPlaylists: ImmutableList<Playlist>,
    currentSortOption: SortOption?,
    isAddingToPlaylist: Boolean,
    isSelectionMode: Boolean,
    selectedPlaylistIds: Set<String>,
    onPlaylistLongPress: (Playlist) -> Unit,
    onPlaylistSelectionToggle: (Playlist) -> Unit
) {
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val gridState = rememberLazyGridState()
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }

    LaunchedEffect(currentSortOption, filteredPlaylists) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        gridState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val showScrollbar = LocalShowScrollbar.current &&
            (gridState.canScrollForward || gridState.canScrollBackward)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isAddingToPlaylist) 130.dp else 150.dp),
            state = gridState,
            modifier = Modifier
                .padding(start = 12.dp, end = animatedScrollbarInset(showScrollbar, 22.dp, 12.dp), bottom = 6.dp)
                .fillMaxSize()
                .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
        ) {
            items(filteredPlaylists.size, key = { filteredPlaylists[it].id }) { index ->
                val playlist = filteredPlaylists[index]
                val selectionIndex = remember(playlist.id, isSelectionMode, selectedPlaylistIds) {
                    if (isSelectionMode && playlist.id in selectedPlaylistIds) {
                        selectedPlaylistIds.toList().indexOf(playlist.id)
                    } else {
                        -1
                    }
                }
                PlaylistGridItem(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = isAddingToPlaylist,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPlaylistIds.contains(playlist.id),
                    selectionIndex = selectionIndex,
                    onClick = {
                        if (isAddingToPlaylist || isSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    },
                    onLongPress = { onPlaylistLongPress(playlist) }
                )
            }
        }

        val bottomPadding = if (hasCurrentSong) {
            bottomBarHeight + MiniPlayerHeight + 16.dp
        } else {
            bottomBarHeight + 16.dp
        }
        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            gridState = gridState
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlaylistGridItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    selectionIndex: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val playlistPreviewSongIds = remember(playlist.songIds) { playlist.songIds.take(4) }
    val playlistSongs by remember(playlistPreviewSongIds, playerViewModel) {
        playerViewModel.observeSongs(playlistPreviewSongIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistGridSelectionScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistGridContainerColor"
    )
    val cardShape = SegmentedItemShapeDefaults.LibraryContainerShape

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .seamlessSelectionScale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, cardShape)
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .then(
                if (isAddingToPlaylist) {
                    Modifier.toggleable(isSelected, role = Role.Checkbox, onValueChange = { onClick() })
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                }
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box {
            Column(modifier = Modifier.padding(10.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    PlaylistCover(
                        playlist = playlist,
                        playlistSongs = remember(playlistSongs) { playlistSongs.toImmutableList() },
                        size = maxWidth
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RoundedSans),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (isSelected && (isAddingToPlaylist || isSelectionMode)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex >= 0) {
                        Text(
                            text = (selectionIndex + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = if (isAddingToPlaylist) null
                            else stringResource(R.string.presentation_batch_g_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    isAddingToPlaylist: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int = -1,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    onLongPress: () -> Unit = {}
) {
    val playlistPreviewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    val playlistSongsInitialValue = remember(playlistPreviewSongIds) {
        if (playlistPreviewSongIds.isEmpty()) emptyList<Song>() else null
    }
    val playlistSongs by remember(playlistPreviewSongIds, playerViewModel) {
        playerViewModel.observeSongs(playlistPreviewSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = playlistSongsInitialValue)

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected && !isAddingToPlaylist) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistSelectionScaleAnimation"
    )

    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected && !isAddingToPlaylist) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderAnimation"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isAddingToPlaylist -> MaterialTheme.colorScheme.surfaceContainerHigh
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistContainerColorAnimation"
    )

    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected && !isAddingToPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "playlistBorderColorAnimation"
    )

    val cardShape = remember(customShape) { customShape ?: RoundedCornerShape(12.dp) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected && !isAddingToPlaylist) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = selectionBorderColor,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .then(
                if (isAddingToPlaylist) {
                    Modifier.toggleable(isSelected, role = Role.Checkbox, onValueChange = { onClick() })
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                }
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = remember(playlistSongs) { (playlistSongs ?: emptyList()).toImmutableList() },
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = RoundedSans),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.source == "NAVIDROME") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_navidrome),
                            contentDescription = stringResource(R.string.cd_navidrome_logo),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected && isSelectionMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex >= 0) {
                        Text(
                            text = (selectionIndex + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isAddingToPlaylist) {
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(checked = isSelected, onCheckedChange = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    // Open straight into typing: the name is the only required input here.
    val nameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Wait a frame so the dialog window exists before asking the IME to show.
        withFrameNanos { }
        nameFocusRequester.requestFocus()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.create_playlist_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedSans,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.create_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.create_playlist_name_placeholder)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .focusRequester(nameFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (playlistName.isNotBlank()) onCreate(playlistName.trim()) }
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { onCreate(playlistName.trim()) },
                        modifier = Modifier.weight(1f),
                        enabled = playlistName.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_create), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
