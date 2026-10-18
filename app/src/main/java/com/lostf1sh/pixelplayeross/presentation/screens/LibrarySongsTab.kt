package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.animatedScrollbarInset
import com.lostf1sh.pixelplayeross.ui.theme.LocalShowScrollbar
import com.lostf1sh.pixelplayeross.presentation.components.songFastScrollLabel
import androidx.compose.ui.text.style.TextOverflow
import com.lostf1sh.pixelplayeross.presentation.utils.SegmentedItemShapeDefaults
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedItemShape


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibrarySongsTab(
    songs: LazyPagingItems<Song>,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    sortOption: SortOption,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    storageFilter: StorageFilter = StorageFilter.ALL,
    hasCurrentSong: Boolean = false
) {
    val listState = rememberRestoredLazyListState(songs)
    val dummyListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
    val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
    val songFastScrollLabelProvider = remember(songs, sortOption) {
        { index: Int ->
            songFastScrollLabel(
                song = songs.peek(index),
                sortOption = sortOption
            )
        }
    }
    val currentSongId by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    
    val currentSongListIndex = remember(songs.itemSnapshotList, currentSongId) {
        if (currentSongId == null) -1
        else {
            val snapshot = songs.itemSnapshotList
            val indexInSnapshot = snapshot.items.indexOfFirst { it.id == currentSongId }
            if (indexInSnapshot != -1) {
                indexInSnapshot + snapshot.placeholdersBefore
            } else {
                -1
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playerViewModel.scrollToIndexEvent.collect { request ->
                // A neighbouring pager page (e.g. Liked) is composed too; ignore its requests.
                if (request.tab != LibraryTabId.SONGS) return@collect
                val index = request.index
                if (index >= 0) {
                    val firstVisible = listState.firstVisibleItemIndex
                    if (Math.abs(index - firstVisible) > 20) {
                        listState.scrollToItem(index)
                    } else {
                        listState.animateScrollToItem(index)
                    }
                }
            }
        }
    }

    val locateCurrentSongAction: (() -> Unit)? = remember(currentSongId) {
        if (currentSongId == null) {
            null
        } else {
            {
                playerViewModel.requestLocateCurrentSong(LibraryTabId.SONGS)
            }
        }
    }

    LaunchedEffect(locateCurrentSongAction) {
        registerActionCallback(locateCurrentSongAction)
    }

    ResetScrollOnSortChange(
        sortKey = sortOption.storageKey,
        items = songs,
        scrollToTop = { listState.scrollToItem(0) }
    )
    
    LaunchedEffect(currentSongListIndex, songs.itemCount, isLoading, listState, currentSongId) {
        if (songs.itemCount == 0 || isLoading) {
            visibilityCallback(false)
            return@LaunchedEffect
        }
        
        if (currentSongListIndex == -1) {
             visibilityCallback(currentSongId != null)
             return@LaunchedEffect
        }

        snapshotFlow {
            val first = listState.firstVisibleItemIndex
            val count = listState.layoutInfo.visibleItemsInfo.size
            if (count == 0) {
                false
            } else {
                currentSongListIndex in first until (first + count)
            }
        }
            .distinctUntilChanged()
            .collect { isVisible ->
                visibilityCallback(!isVisible)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            visibilityCallback(false)
            registerActionCallback(null)
        }
    }

    val refreshState = songs.loadState.refresh
    val reachedEndOfPagination = songs.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = songs.itemCount == 0 && isLoading

    when {
        refreshState is LoadState.Error && songs.itemCount == 0 -> {
            val error = refreshState.error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_songs), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { songs.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        shouldShowInitialLoading -> {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 24.dp, bottom = 6.dp)
                    .clip(SegmentedItemShapeDefaults.LibraryContainerShape)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(12, key = { "skeleton_song_$it" }) { index ->
                    EnhancedSongListItem(
                        song = Song.emptySong(),
                        isPlaying = false,
                        isLoading = true,
                        customShape = getSegmentedItemShape(index, 12),
                        isCurrentSong = false,
                        onMoreOptionsClick = {},
                        onClick = {}
                    )
                }
            }
        }
        songs.itemCount == 0 && refreshState is LoadState.NotLoading && reachedEndOfPagination -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.SONGS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val activeListState = if (songs.itemCount > 0) listState else dummyListState
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 12.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward), 22.dp, 12.dp), bottom = 6.dp)
                                .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
                            state = activeListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                        ) {

                            items(
                                count = songs.itemCount,
                                key = { index -> songs.peek(index)?.id ?: index },
                                contentType = { "song" }
                            ) { index ->
                                val song = songs[index]
                                val itemShape = getSegmentedItemShape(index, songs.itemCount)
                                
                                if (song != null) {
                                    val isSelected = selectedSongIds.contains(song.id)
                                    
                                    val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                        { songFromListItem -> onMoreOptionsClick(songFromListItem) }
                                    }
                                    
                                    val rememberedOnClick: () -> Unit = remember(song, isSelectionMode) {
                                        if (isSelectionMode) {
                                            { onSongSelectionToggle(song) }
                                        } else {
                                            { playerViewModel.showAndPlaySongFromLibrary(song) }
                                        }
                                    }
                                    
                                    val rememberedOnLongPress: () -> Unit = remember(song) {
                                        { onSongLongPress(song) }
                                    }

                                    LibraryPlaybackAwareSongItem(
                                        song = song,
                                        playerViewModel = playerViewModel,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                        customShape = itemShape,
                                        onLongPress = rememberedOnLongPress,
                                        onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                        onClick = rememberedOnClick
                                    )
                                } else {
                                     EnhancedSongListItem(
                                        song = Song.emptySong(),
                                        isPlaying = false,
                                        isLoading = true,
                                        customShape = itemShape,
                                        isCurrentSong = false,
                                        onMoreOptionsClick = {},
                                        onClick = {}
                                     )
                                }
                            }
                        }
                        
                        val bottomPadding = if (hasCurrentSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp 
                        else 
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = activeListState,
                            dragLabelProvider = songFastScrollLabelProvider
                        )
                    }
                }
            }
        }
    }
}
