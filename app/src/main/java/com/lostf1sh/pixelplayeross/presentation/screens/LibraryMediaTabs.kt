@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class
)

package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.animatedScrollbarInset
import com.lostf1sh.pixelplayeross.ui.theme.LocalShowScrollbar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistContainer
import com.lostf1sh.pixelplayeross.presentation.components.albumFastScrollLabel
import com.lostf1sh.pixelplayeross.presentation.components.artistFastScrollLabel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.style.TextOverflow
import com.lostf1sh.pixelplayeross.presentation.utils.SegmentedItemShapeDefaults
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedGridItemShape
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedItemShape
import kotlinx.collections.immutable.ImmutableList

private val EmptyColorSchemePairFlow: StateFlow<ColorSchemePair?> = MutableStateFlow(null)

private const val AlbumGridColumns = 2
private val AlbumGridSpacing = 6.dp

/** Radius of the album grid's four outer corners; every inner corner is 8dp. */
private val AlbumGridOuterCornerRadius = SegmentedItemShapeDefaults.LibraryOuterCornerRadius

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryAlbumsTab(
    albums: LazyPagingItems<Album>,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    isListView: Boolean,
    currentAlbumSortOption: SortOption,
    onAlbumClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedAlbumIds: Set<Long> = emptySet(),
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectionToggle: (Album) -> Unit = {},
    getSelectionIndex: (Long) -> Int? = { null },
    storageFilter: StorageFilter = StorageFilter.ALL
) {
    val gridState = rememberRestoredLazyGridState(albums)
    val listState = rememberRestoredLazyListState(albums)
    val dummyListState = rememberLazyListState()
    val dummyGridState = rememberLazyGridState()
    val configuration = LocalConfiguration.current
    val albumGridColumns = remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp >= 840 -> 4
            configuration.screenWidthDp >= 600 -> 3
            else -> 2
        }
    }
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val albumFastScrollLabelProvider = remember(albums, currentAlbumSortOption) {
        { index: Int ->
            albumFastScrollLabel(
                album = albums.peek(index),
                sortOption = currentAlbumSortOption
            )
        }
    }

    ResetScrollOnSortChange(
        sortKey = currentAlbumSortOption.storageKey,
        items = albums,
        scrollToTop = {
            if (isListView) {
                listState.scrollToItem(0)
            } else {
                gridState.scrollToItem(0)
            }
        }
    )

    LaunchedEffect(albums, gridState, listState, isListView) {
        if (isListView) {
            snapshotFlow { listState.layoutInfo }
                .debounce(150)
                .distinctUntilChanged()
                .collect { layoutInfo ->
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isNotEmpty() && albums.itemCount > 0) {
                        val lastVisibleItemIndex = visibleItemsInfo.last().index
                        val totalItemsCount = albums.itemCount
                        val prefetchThreshold = 5
                        val prefetchCount = 4

                        if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                            val startIndexToPrefetch = lastVisibleItemIndex + 1
                            val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                            (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                                val album = albums.peek(indexToPrefetch)
                                album?.albumArtUriString?.let { uri ->
                                    val request = ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(Size(256, 256))
                                        .build()
                                    imageLoader.enqueue(request)
                                }
                            }
                        }
                    }
                }
        } else {
            snapshotFlow { gridState.layoutInfo }
                .debounce(150)
                .distinctUntilChanged()
                .collect { layoutInfo ->
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isNotEmpty() && albums.itemCount > 0) {
                        val lastVisibleItemIndex = visibleItemsInfo.last().index
                        val totalItemsCount = albums.itemCount
                        val prefetchThreshold = 5
                        val prefetchCount = 4

                        if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                            val startIndexToPrefetch = lastVisibleItemIndex + 1
                            val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                            (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                                val album = albums.peek(indexToPrefetch)
                                album?.albumArtUriString?.let { uri ->
                                    val request = ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(Size(256, 256))
                                        .build()
                                    imageLoader.enqueue(request)
                                }
                            }
                        }
                    }
            }
        }
    }

    val refreshState = albums.loadState.refresh
    val reachedEndOfPagination = albums.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = albums.itemCount == 0 && isLoading

    when {
        refreshState is LoadState.Error && albums.itemCount == 0 -> {
            val error = refreshState.error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_albums), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { albums.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        shouldShowInitialLoading -> {
            if (isListView) {
                LazyColumn(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .clip(SegmentedItemShapeDefaults.LibraryContainerShape)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(8, key = { "skeleton_album_list_$it" }) { index ->
                        AlbumListItem(
                            album = Album.empty(),
                            albumColorSchemePairFlow = EmptyColorSchemePairFlow,
                            onClick = {},
                            isLoading = true,
                            customShape = getSegmentedItemShape(index, 8)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .clip(SegmentedItemShapeDefaults.LibraryContainerShape)
                        .fillMaxSize(),
                    columns = GridCells.Fixed(albumGridColumns),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(AlbumGridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(AlbumGridSpacing)
                ) {
                    items(8, key = { "skeleton_album_grid_$it" }) { index ->
                        val itemShape = remember(index) {
                            getSegmentedGridItemShape(
                                index = index,
                                count = 8,
                                columns = albumGridColumns,
                                outerRadius = AlbumGridOuterCornerRadius
                            )
                        }
                        AlbumGridItemRedesigned(
                            album = Album.empty(),
                            albumColorSchemePairFlow = EmptyColorSchemePairFlow,
                            onClick = {},
                            isLoading = true,
                            customShape = itemShape
                        )
                    }
                }
            }
        }

        albums.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ALBUMS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                val albumsPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = albumsPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = albumsPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isListView) {
                            val activeListState = if (albums.itemCount > 0) listState else dummyListState
                            LazyColumn(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward), 24.dp, 14.dp), bottom = 6.dp)
                                    .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
                                state = activeListState,
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    count = albums.itemCount,
                                    key = { index -> albums.peek(index)?.id ?: "album_placeholder_$index" },
                                    contentType = { "album_list_item" }
                                ) { index ->
                                    val album = albums[index]
                                    val itemShape = getSegmentedItemShape(index, albums.itemCount)
                                    if (album != null) {
                                        val albumSpecificColorSchemeFlow =
                                            playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                        val rememberedOnClick = remember(album.id, onAlbumClick) {
                                            { onAlbumClick(album.id) }
                                        }
                                        val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                            { onAlbumLongPress(album) }
                                        }
                                        val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                            { onAlbumSelectionToggle(album) }
                                        }
                                        AlbumListItem(
                                            album = album,
                                            albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                            onClick = rememberedOnClick,
                                            isLoading = false,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedAlbumIds.contains(album.id),
                                            selectionIndex = getSelectionIndex(album.id),
                                            onLongPress = rememberedOnLongPress,
                                            onSelectionToggle = rememberedOnSelectionToggle,
                                            customShape = itemShape
                                        )
                                    } else {
                                        AlbumListItem(
                                            album = Album.empty(),
                                            albumColorSchemePairFlow = EmptyColorSchemePairFlow,
                                            onClick = {},
                                            isLoading = true,
                                            customShape = itemShape
                                        )
                                    }
                                }
                            }
                            val hasActiveSong by remember {
                                playerViewModel.stablePlayerState
                                    .map { it.currentSong != null && it.currentSong != Song.emptySong() }
                                    .distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = false)
                            val bottomPadding = if (hasActiveSong)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                listState = activeListState,
                                dragLabelProvider = albumFastScrollLabelProvider
                            )
                        } else {
                            val activeGridState = if (albums.itemCount > 0) gridState else dummyGridState
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (activeGridState.canScrollForward || activeGridState.canScrollBackward), 24.dp, 14.dp), bottom = 6.dp)
                                    .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
                                state = activeGridState,
                                columns = GridCells.Fixed(albumGridColumns),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(AlbumGridSpacing),
                                horizontalArrangement = Arrangement.spacedBy(AlbumGridSpacing)
                            ) {
                                items(
                                    count = albums.itemCount,
                                    key = { index -> albums.peek(index)?.id ?: "album_grid_placeholder_$index" },
                                    contentType = { "album_grid_item" }
                                ) { index ->
                                    val album = albums[index]
                                    val albumCount = albums.itemCount
                                    val itemShape = remember(index, albumCount) {
                                        getSegmentedGridItemShape(
                                            index = index,
                                            count = albumCount,
                                            columns = albumGridColumns,
                                            outerRadius = AlbumGridOuterCornerRadius
                                        )
                                    }
                                    if (album != null) {
                                        val albumSpecificColorSchemeFlow =
                                            playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                        val rememberedOnClick = remember(album.id, onAlbumClick) {
                                            { onAlbumClick(album.id) }
                                        }
                                        val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                            { onAlbumLongPress(album) }
                                        }
                                        val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                            { onAlbumSelectionToggle(album) }
                                        }
                                        AlbumGridItemRedesigned(
                                            album = album,
                                            albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                            onClick = rememberedOnClick,
                                            isLoading = false,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedAlbumIds.contains(album.id),
                                            selectionIndex = getSelectionIndex(album.id),
                                            onLongPress = rememberedOnLongPress,
                                            onSelectionToggle = rememberedOnSelectionToggle,
                                            customShape = itemShape
                                        )
                                    } else {
                                        AlbumGridItemRedesigned(
                                            album = Album.empty(),
                                            albumColorSchemePairFlow = EmptyColorSchemePairFlow,
                                            onClick = {},
                                            isLoading = true,
                                            customShape = itemShape
                                        )
                                    }
                                }
                            }

                            val hasActiveSong by remember {
                                playerViewModel.stablePlayerState
                                    .map { it.currentSong != null && it.currentSong != Song.emptySong() }
                                    .distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = false)
                            val bottomPadding = if (hasActiveSong)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                gridState = activeGridState,
                                dragLabelProvider = albumFastScrollLabelProvider
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryArtistsTab(
    artists: LazyPagingItems<Artist>,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    currentArtistSortOption: SortOption,
    onArtistClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedArtistIds: Set<Long> = emptySet(),
    onArtistLongPress: (Artist) -> Unit = {},
    onArtistSelectionToggle: (Artist) -> Unit = {},
    getSelectionIndex: (Long) -> Int? = { null },
    storageFilter: StorageFilter = StorageFilter.ALL
) {
    val listState = rememberRestoredLazyListState(artists)
    val dummyListState = rememberLazyListState()
    val artistFastScrollLabelProvider = remember(artists, currentArtistSortOption) {
        { index: Int ->
            artistFastScrollLabel(
                artist = artists.peek(index),
                sortOption = currentArtistSortOption
            )
        }
    }
    ResetScrollOnSortChange(
        sortKey = currentArtistSortOption.storageKey,
        items = artists,
        scrollToTop = { listState.scrollToItem(0) }
    )

    val refreshState = artists.loadState.refresh
    val reachedEndOfPagination = artists.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = artists.itemCount == 0 && isLoading

    when {
        refreshState is LoadState.Error && artists.itemCount == 0 -> {
            val error = refreshState.error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_artists), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { artists.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        shouldShowInitialLoading -> {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(SegmentedItemShapeDefaults.LibraryContainerShape)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(10, key = { "skeleton_artist_$it" }) { index ->
                    ArtistListItem(
                        artist = Artist.empty(),
                        onClick = {},
                        isLoading = true,
                        customShape = getSegmentedItemShape(index, 10)
                    )
                }
            }
        }

        artists.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ARTISTS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val genresPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = genresPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = genresPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val activeListState = if (artists.itemCount > 0) listState else dummyListState
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 12.dp, end = animatedScrollbarInset(LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward), 22.dp, 12.dp), bottom = 6.dp)
                                .clip(SegmentedItemShapeDefaults.LibraryContainerShape),
                            state = activeListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                        ) {
                            items(
                                count = artists.itemCount,
                                key = { index -> artists.peek(index)?.id ?: "artist_placeholder_$index" },
                                contentType = { "artist" }
                            ) { index ->
                                val artist = artists[index]
                                val itemShape = getSegmentedItemShape(index, artists.itemCount)
                                if (artist != null) {
                                    val rememberedOnClick = remember(artist.id, onArtistClick) {
                                        { onArtistClick(artist.id) }
                                    }
                                    val rememberedOnLongPress = remember(artist.id, onArtistLongPress) {
                                        { onArtistLongPress(artist) }
                                    }
                                    val rememberedOnSelectionToggle = remember(artist.id, onArtistSelectionToggle) {
                                        { onArtistSelectionToggle(artist) }
                                    }
                                    ArtistListItem(
                                        artist = artist,
                                        onClick = rememberedOnClick,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedArtistIds.contains(artist.id),
                                        selectionIndex = getSelectionIndex(artist.id),
                                        onLongPress = rememberedOnLongPress,
                                        onSelectionToggle = rememberedOnSelectionToggle,
                                        customShape = itemShape
                                    )
                                } else {
                                    ArtistListItem(
                                        artist = Artist.empty(),
                                        onClick = {},
                                        isLoading = true,
                                        customShape = itemShape
                                    )
                                }
                            }
                        }

                        val hasActiveSong by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong != null && it.currentSong != Song.emptySong() }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val bottomPadding = if (hasActiveSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = activeListState,
                            dragLabelProvider = artistFastScrollLabelProvider
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryPlaylistsTab(
    playlistUiState: PlaylistUiState,
    filteredPlaylists: ImmutableList<com.lostf1sh.pixelplayeross.data.model.Playlist> = playlistUiState.playlists,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (com.lostf1sh.pixelplayeross.data.model.Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (com.lostf1sh.pixelplayeross.data.model.Playlist) -> Unit = {},
    onPlaylistOptionsClick: () -> Unit = {},
    isGridView: Boolean = false
) {
    PlaylistContainer(
        playlistUiState = playlistUiState,
        filteredPlaylists = filteredPlaylists,
        currentSortOption = playlistUiState.currentPlaylistSortOption,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        bottomBarHeight = bottomBarHeight,
        navController = navController,
        playerViewModel = playerViewModel,
        isSelectionMode = isSelectionMode,
        selectedPlaylistIds = selectedPlaylistIds,
        onPlaylistLongPress = onPlaylistLongPress,
        onPlaylistSelectionToggle = onPlaylistSelectionToggle,
        isGridView = isGridView
    )
}
