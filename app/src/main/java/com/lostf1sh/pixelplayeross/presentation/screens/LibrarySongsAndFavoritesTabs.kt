@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.animatedScrollbarInset
import com.lostf1sh.pixelplayeross.ui.theme.LocalShowScrollbar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.selection.dragToSelect
import com.lostf1sh.pixelplayeross.presentation.selection.rememberDragToSelectState
import com.lostf1sh.pixelplayeross.presentation.components.songFastScrollLabel
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.lostf1sh.pixelplayeross.presentation.utils.SegmentedItemShapeDefaults
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedItemShape

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryFavoritesTab(
    favoriteSongs: LazyPagingItems<Song>,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    currentSelectedSongIds: () -> Set<String> = { emptySet() },
    onSongDragSelectionChange: (select: List<Song>, deselect: List<Song>) -> Unit = { _, _ -> },
    sortOption: SortOption,
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    storageFilter: StorageFilter = StorageFilter.ALL,
    hasCurrentSong: Boolean = false
) {
    val listState = rememberRestoredLazyListState(favoriteSongs)
    val coroutineScope = rememberCoroutineScope()
    val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
    val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
    val dragSelectState = rememberDragToSelectState<Song>()
    val dragSelectSongAt: (Int) -> Song? = remember(favoriteSongs) {
        { index -> if (index in 0 until favoriteSongs.itemCount) favoriteSongs.peek(index) else null }
    }
    val favoriteFastScrollLabelProvider = remember(favoriteSongs, sortOption) {
        { index: Int ->
            songFastScrollLabel(
                song = favoriteSongs.peek(index),
                sortOption = sortOption
            )
        }
    }
    val currentSongId by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isCurrentSongFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()

    val currentSongListIndex = remember(favoriteSongs.itemCount, currentSongId) {
        if (currentSongId == null) -1
        else {
            val items = favoriteSongs.itemSnapshotList
            items.indexOfFirst { it?.id == currentSongId }
        }
    }
    val locateCurrentSongAction: (() -> Unit)? = remember(currentSongId) {
        if (currentSongId == null) {
            null
        } else {
            {
                playerViewModel.requestLocateCurrentSong(LibraryTabId.LIKED)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playerViewModel.scrollToIndexEvent.collect { request ->
                // A Songs request carries a Songs-order index, which means nothing in this list.
                if (request.tab != LibraryTabId.LIKED) return@collect
                // The list has no header rows and uses paging placeholders, so the
                // favourites-order index is already the list item index.
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

    LaunchedEffect(locateCurrentSongAction) {
        registerActionCallback(locateCurrentSongAction)
    }

    ResetScrollOnSortChange(
        sortKey = sortOption.storageKey,
        items = favoriteSongs,
        scrollToTop = { listState.scrollToItem(0) }
    )

    LaunchedEffect(
        currentSongListIndex,
        favoriteSongs.itemCount,
        listState,
        currentSongId,
        isCurrentSongFavorite
    ) {
        // This tab only lists favourites, so a song that is not one can never be located here.
        if (currentSongId == null || favoriteSongs.itemCount == 0 || !isCurrentSongFavorite) {
            visibilityCallback(false)
            return@LaunchedEffect
        }

        if (currentSongListIndex == -1) {
             visibilityCallback(true)
             return@LaunchedEffect
        }

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                false
            } else {
                currentSongListIndex in visibleItems.first().index..visibleItems.last().index
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

    if (favoriteSongs.itemCount == 0 && favoriteSongs.loadState.refresh !is LoadState.Loading) {
        LibraryExpressiveEmptyState(
            tabId = LibraryTabId.LIKED,
            storageFilter = storageFilter,
            bottomBarHeight = bottomBarHeight
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val songsPullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = songsPullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = songsPullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = 12.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward), 22.dp, 12.dp), bottom = 6.dp)
                            .clip(SegmentedItemShapeDefaults.LibraryContainerShape)
                            .dragToSelect(
                                state = dragSelectState,
                                listState = listState,
                                itemAt = dragSelectSongAt,
                                itemKey = Song::id,
                                selectedKeys = currentSelectedSongIds,
                                onSelectionChange = onSongDragSelectionChange,
                                bottomInset = if (hasCurrentSong) bottomBarHeight + MiniPlayerHeight else bottomBarHeight
                            ),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                    ) {
                        items(
                            count = favoriteSongs.itemCount,
                            key = { index -> favoriteSongs.peek(index)?.id ?: index },
                            contentType = { "song" }
                        ) { index ->
                            val song = favoriteSongs[index]
                            val itemShape = getSegmentedItemShape(index, favoriteSongs.itemCount)
                            if (song != null) {
                                LibraryPlaybackAwareSongItem(
                                    song = song,
                                    playerViewModel = playerViewModel,
                                    customShape = itemShape,
                                    onMoreOptionsClick = { onMoreOptionsClick(song) },
                                    isSelected = selectedSongIds.contains(song.id),
                                    selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = {
                                        onSongLongPress(song)
                                        dragSelectState.onItemLongPressed(song)
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            onSongSelectionToggle(song)
                                        } else {
                                            playerViewModel.showAndPlaySongFromFavorites(song)
                                        }
                                    }
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
                        listState = listState,
                        dragLabelProvider = favoriteFastScrollLabelProvider
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibrarySongsTabPaginated(
    paginatedSongs: LazyPagingItems<Song>,
    stablePlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    val dummyListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    when {
        paginatedSongs.loadState.refresh is LoadState.Loading && paginatedSongs.itemCount == 0 -> {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
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

        paginatedSongs.loadState.refresh is LoadState.Error && paginatedSongs.itemCount == 0 -> {
            val error = (paginatedSongs.loadState.refresh as LoadState.Error).error
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
                    Button(onClick = { paginatedSongs.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        paginatedSongs.itemCount == 0 && paginatedSongs.loadState.refresh is LoadState.NotLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_off_24),
                        contentDescription = stringResource(R.string.cd_no_songs_in_library),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.library_no_songs_in_library_message), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.library_no_songs_rescan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                        paginatedSongs.refresh()
                    },
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
                        val activeListState = if (paginatedSongs.itemCount > 0) listState else dummyListState
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 12.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward), 22.dp, 12.dp), bottom = 6.dp)
                                .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
                            state = activeListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                        ) {
                            item(
                                key = "songs_top_spacer",
                                contentType = "songs_top_spacer"
                            ) { Spacer(Modifier.height(0.dp)) }

                            items(
                                count = paginatedSongs.itemCount,
                                key = { index -> paginatedSongs.peek(index)?.id ?: "paged_song_$index" },
                                contentType = paginatedSongs.itemContentType { "song" }
                            ) { index ->
                                val song = paginatedSongs[index]
                                val itemShape = getSegmentedItemShape(index, paginatedSongs.itemCount)
                                if (song != null) {
                                    val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying

                                    val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                        { songFromListItem -> onMoreOptionsClick(songFromListItem) }
                                    }
                                    val rememberedOnClick: () -> Unit = remember(song) {
                                        { playerViewModel.showAndPlaySongFromLibrary(song) }
                                    }

                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = isPlayingThisSong,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        isLoading = false,
                                        customShape = itemShape,
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

                            if (paginatedSongs.loadState.append is LoadState.Loading) {
                                item(contentType = "songs_append_loading") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }

                        val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong())
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = activeListState
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface, Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}
