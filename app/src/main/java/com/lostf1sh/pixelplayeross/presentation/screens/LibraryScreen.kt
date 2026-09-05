@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafelyReplacing

import android.os.Trace
import com.lostf1sh.pixelplayeross.utils.traceSection
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material.icons.rounded.ViewModule
import com.lostf1sh.pixelplayeross.presentation.components.ToggleSegmentButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import com.lostf1sh.pixelplayeross.ui.theme.ShapeCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.ShimmerBox
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.MusicFolder
import com.lostf1sh.pixelplayeross.data.model.FolderSource
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.resolveMainScreenBottomGradientHeight
import com.lostf1sh.pixelplayeross.presentation.components.resolveNavBarOccupiedHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistArtCollage
import com.lostf1sh.pixelplayeross.presentation.components.ReorderTabsSheet
import com.lostf1sh.pixelplayeross.presentation.components.EditMultipleSongsSheet
import com.lostf1sh.pixelplayeross.presentation.components.SongInfoBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.LibraryActionRow
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.components.MultiSelectionBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistMultiSelectionBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.DescribePlaylistDialog
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistCreationTypeDialog
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionActionRow
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionCountPill
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.CloudDownloadsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.toLibraryTabIdOrNull
import com.lostf1sh.pixelplayeross.data.preferences.LibraryNavigationMode
import com.lostf1sh.pixelplayeross.data.worker.SyncProgress
import com.lostf1sh.pixelplayeross.presentation.screens.search.components.GenreTypography
import com.lostf1sh.pixelplayeross.presentation.components.SyncProgressBar
import com.lostf1sh.pixelplayeross.presentation.viewmodel.LibraryViewModel
import com.lostf1sh.pixelplayeross.presentation.selection.appendDistinctSelection
import com.lostf1sh.pixelplayeross.presentation.selection.selectionIndex
import com.lostf1sh.pixelplayeross.presentation.selection.toggleOrderedSelection
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import androidx.paging.compose.collectAsLazyPagingItems
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.presentation.components.AutoScrollingTextOnDemand
import com.lostf1sh.pixelplayeross.presentation.screens.CreatePlaylistDialog
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistContainer
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.PlayingEqIcon
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import android.widget.Toast
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.focus.focusModifier
import com.lostf1sh.pixelplayeross.data.model.PlaylistShapeType
import kotlinx.coroutines.flow.first
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.LoadState
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.LibrarySortBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import com.lostf1sh.pixelplayeross.presentation.components.rememberModalSheetState

val ListExtraBottomGap = 30.dp
val PlayerSheetCollapsedCornerRadius = 32.dp
private const val ENABLE_FOLDERS_SOURCE_TOGGLE = true
private const val ENABLE_FOLDERS_STORAGE_FILTER = false
private const val FOLDER_NAVIGATION_ROOT_KEY = "__folder_root__"
private const val FOLDER_NAVIGATION_FORWARD = 1
private const val FOLDER_NAVIGATION_BACKWARD = -1
private const val PULL_REFRESH_MIN_VISIBLE_MS = 900L
private const val PULL_REFRESH_MAX_VISIBLE_MS = 1_500L
private const val INLINE_SYNC_MIN_VISIBLE_MS = 600L

private data class LibraryScreenPlayerProjection(
    val currentFolder: MusicFolder? = null,
    val folderSourceRootPath: String = "",
    val folderSource: FolderSource = FolderSource.INTERNAL,
    val isFoldersPlaylistView: Boolean = false,
    val currentStorageFilter: StorageFilter = StorageFilter.ALL,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongDateLiked,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val isAlbumsListView: Boolean = false,
    val isSdCardAvailable: Boolean = false,
    val musicFolders: ImmutableList<MusicFolder> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = true,
    val isSyncingLibrary: Boolean = false,
    val isLoadingInitialSongs: Boolean = true,
    val hideLocalMedia: Boolean = false
)

private fun PlayerUiState.toLibraryScreenProjection(): LibraryScreenPlayerProjection =
    LibraryScreenPlayerProjection(
        currentFolder = currentFolder,
        folderSourceRootPath = folderSourceRootPath,
        folderSource = folderSource,
        isFoldersPlaylistView = isFoldersPlaylistView,
        currentStorageFilter = currentStorageFilter,
        currentSongSortOption = currentSongSortOption,
        currentAlbumSortOption = currentAlbumSortOption,
        currentArtistSortOption = currentArtistSortOption,
        currentFavoriteSortOption = currentFavoriteSortOption,
        currentFolderSortOption = currentFolderSortOption,
        isAlbumsListView = isAlbumsListView,
        isSdCardAvailable = isSdCardAvailable,
        musicFolders = musicFolders,
        isLoadingLibraryCategories = isLoadingLibraryCategories,
        isSyncingLibrary = isSyncingLibrary,
        isLoadingInitialSongs = isLoadingInitialSongs,
        hideLocalMedia = hideLocalMedia
    )

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    songInfoBottomSheetViewModel: SongInfoBottomSheetViewModel = hiltViewModel(),
    cloudDownloadsViewModel: CloudDownloadsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lastTabIndex by playerViewModel.lastLibraryTabIndexFlow.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val syncManager = playerViewModel.syncManager
    var isRefreshing by remember { mutableStateOf(false) }
    val isFetchingChanges by syncManager.isFetchingChanges
        .collectAsStateWithLifecycle(initialValue = false)
    val isSyncing by syncManager.isSyncing
        .collectAsStateWithLifecycle(initialValue = false)

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val tabTitles by playerViewModel.libraryTabsFlow.collectAsStateWithLifecycle()
    val currentTabId by playerViewModel.currentLibraryTabId.collectAsStateWithLifecycle()
    val libraryNavigationMode by playerViewModel.libraryNavigationMode.collectAsStateWithLifecycle()
    val isCompactNavigation = libraryNavigationMode == LibraryNavigationMode.COMPACT_PILL
    val tabCount = tabTitles.size.coerceAtLeast(1)
    val normalizedLastTabIndex = positiveMod(lastTabIndex, tabCount)
    val compactInitialPage = remember(tabCount, normalizedLastTabIndex) {
        infinitePagerInitialPage(tabCount, normalizedLastTabIndex)
    }
    val pagerState = if (isCompactNavigation) {
        rememberPagerState(initialPage = compactInitialPage) { Int.MAX_VALUE }
    } else {
        rememberPagerState(initialPage = normalizedLastTabIndex) { tabCount }
    }
    val currentTabIndex by remember(pagerState, tabTitles, isCompactNavigation) {
        derivedStateOf {
            resolveTabIndex(
                page = pagerState.currentPage,
                tabCount = tabTitles.size,
                compactMode = isCompactNavigation
            )
        }
    }
    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()
    val canNavigateBackInFolders by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState -> uiState.currentFolder != null && uiState.folderBackGestureNavigationEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistCreationTypeDialog by remember { mutableStateOf(false) }
    var showDescribePlaylistDialog by remember { mutableStateOf(false) }

    val m3uImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { playlistViewModel.importM3u(it) }
    }

    var showReorderTabsSheet by remember { mutableStateOf(false) }
    var showTabSwitcherSheet by remember { mutableStateOf(false) }

    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var selectedAlbums by remember { mutableStateOf<ImmutableList<Album>>(persistentListOf()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var selectedArtists by remember { mutableStateOf<ImmutableList<Artist>>(persistentListOf()) }
    val selectedArtistIds = remember(selectedArtists) { selectedArtists.map { it.id }.toSet() }
    val isArtistSelectionMode = selectedArtists.isNotEmpty()
    val latestSelectedAlbums by rememberUpdatedState(selectedAlbums)
    val latestSelectedArtists by rememberUpdatedState(selectedArtists)
    var showBatchEditSheet by remember { mutableStateOf(false) }

    var songsShowLocateButton by remember { mutableStateOf(false) }
    var likedShowLocateButton by remember { mutableStateOf(false) }
    var foldersShowLocateButton by remember { mutableStateOf(false) }
    var songsLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var likedLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var foldersLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingFoldersLocatePath by remember { mutableStateOf<String?>(null) }

    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song) 
        }
    }

    val onSongSelectionToggle: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }

    val toggleAlbumSelection: (Album) -> Unit = remember(selectedAlbums, multiSelectionState) {
        { album ->
            // A changed category selection invalidates any songs resolved for an earlier snapshot.
            multiSelectionState.clearSelection()
            showMultiSelectionSheet = false
            selectedAlbums = toggleOrderedSelection(selectedAlbums, album, Album::id).toImmutableList()
        }
    }

    val onAlbumLongPress: (Album) -> Unit = remember(toggleAlbumSelection, haptic) {
        { album -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            toggleAlbumSelection(album) 
        }
    }

    val onAlbumSelectionToggle: (Album) -> Unit = remember(toggleAlbumSelection) {
        { album -> toggleAlbumSelection(album) }
    }

    val getAlbumSelectionIndex: (Long) -> Int? = remember(selectedAlbums) {
        { albumId -> selectionIndex(selectedAlbums, albumId, Album::id) }
    }

    val toggleArtistSelection: (Artist) -> Unit = remember(selectedArtists, multiSelectionState) {
        { artist ->
            // A changed category selection invalidates any songs resolved for an earlier snapshot.
            multiSelectionState.clearSelection()
            showMultiSelectionSheet = false
            selectedArtists = toggleOrderedSelection(selectedArtists, artist, Artist::id).toImmutableList()
        }
    }

    val onArtistLongPress: (Artist) -> Unit = remember(toggleArtistSelection, haptic) {
        { artist ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            toggleArtistSelection(artist)
        }
    }

    val onArtistSelectionToggle: (Artist) -> Unit = remember(toggleArtistSelection) {
        { artist -> toggleArtistSelection(artist) }
    }

    val getArtistSelectionIndex: (Long) -> Int? = remember(selectedArtists) {
        { artistId -> selectionIndex(selectedArtists, artistId, Artist::id) }
    }

    val presentResolvedCategorySongs: (List<Song>) -> Unit = remember(
        multiSelectionState,
        playerViewModel,
        context
    ) {
        { songs ->
            if (songs.isEmpty()) {
                playerViewModel.sendToast(context.getString(R.string.no_valid_songs))
            } else {
                multiSelectionState.replaceSelection(songs)
                showMultiSelectionSheet = true
            }
        }
    }

    val openSelectedAlbumActions: () -> Unit = remember(
        selectedAlbums,
        playerViewModel,
        scope,
        presentResolvedCategorySongs
    ) {
        {
            val selectionSnapshot = selectedAlbums.toList()
            val snapshotIds = selectionSnapshot.map(Album::id)
            scope.launch {
                runCatching {
                    playerViewModel.resolveAlbumSongsForBatchActions(selectionSnapshot)
                }.onSuccess { songs ->
                    if (latestSelectedAlbums.map(Album::id) == snapshotIds) {
                        presentResolvedCategorySongs(songs)
                    }
                }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.tag("LibrarySelection").e(error, "Unable to resolve selected albums")
                        playerViewModel.sendToast(context.getString(R.string.no_valid_songs))
                    }
            }
        }
    }

    val openSelectedArtistActions: () -> Unit = remember(
        selectedArtists,
        playerViewModel,
        scope,
        presentResolvedCategorySongs
    ) {
        {
            val selectionSnapshot = selectedArtists.toList()
            val snapshotIds = selectionSnapshot.map(Artist::id)
            scope.launch {
                runCatching {
                    playerViewModel.resolveArtistSongsForBatchActions(selectionSnapshot)
                }.onSuccess { songs ->
                    if (latestSelectedArtists.map(Artist::id) == snapshotIds) {
                        presentResolvedCategorySongs(songs)
                    }
                }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.tag("LibrarySelection").e(error, "Unable to resolve selected artists")
                        playerViewModel.sendToast(context.getString(R.string.no_valid_songs))
                    }
            }
        }
    }

    val playlistMultiSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistMultiSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistMultiSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistMultiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }
    var showMergePlaylistDialog by remember { mutableStateOf(false) }
    var pendingMergePlaylistIds by remember { mutableStateOf(emptyList<String>()) }

    val onPlaylistLongPress: (com.lostf1sh.pixelplayeross.data.model.Playlist) -> Unit = remember(playlistMultiSelectionState, haptic) {
        { playlist ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            playlistMultiSelectionState.toggleSelection(playlist)
            Timber.tag("PlaylistMultiSelect").d("Toggled: ${playlist.name}, total selected: ${playlistMultiSelectionState.selectedPlaylists.value.size}")
        }
    }

    val onPlaylistSelectionToggle: (com.lostf1sh.pixelplayeross.data.model.Playlist) -> Unit = remember(playlistMultiSelectionState) {
        { playlist -> playlistMultiSelectionState.toggleSelection(playlist) }
    }

    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    var isMinDelayActive by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }

    val onRefresh: () -> Unit = remember(scope, syncManager) {
        {
            val currentRefreshGeneration = refreshGeneration + 1
            refreshGeneration = currentRefreshGeneration
            isMinDelayActive = true
            isRefreshing = true
            syncManager.incrementalSync()
            scope.launch {
                kotlinx.coroutines.delay(PULL_REFRESH_MIN_VISIBLE_MS)
                if (currentRefreshGeneration != refreshGeneration) return@launch
                isMinDelayActive = false
                val stillFetching = syncManager.isFetchingChanges.first()
                if (!stillFetching) {
                    isRefreshing = false
                    return@launch
                }

                val remainingVisibleMs =
                    (PULL_REFRESH_MAX_VISIBLE_MS - PULL_REFRESH_MIN_VISIBLE_MS)
                        .coerceAtLeast(0L)
                if (remainingVisibleMs > 0L) {
                    kotlinx.coroutines.delay(remainingVisibleMs)
                }
                if (currentRefreshGeneration != refreshGeneration) return@launch
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(isFetchingChanges) {
        if (!isFetchingChanges && !isMinDelayActive) {
            isRefreshing = false
        }
    }

    var inlineSyncVisible by remember { mutableStateOf(false) }
    var inlineSyncShownAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isSyncing, isRefreshing) {
        if (isSyncing && !isRefreshing) {
            if (!inlineSyncVisible) {
                inlineSyncShownAt = System.currentTimeMillis()
                inlineSyncVisible = true
            }
        } else if (isRefreshing) {
            inlineSyncVisible = false
            inlineSyncShownAt = null
        } else if (inlineSyncVisible) {
            val shownAt = inlineSyncShownAt
            val elapsed = if (shownAt != null) {
                System.currentTimeMillis() - shownAt
            } else {
                INLINE_SYNC_MIN_VISIBLE_MS
            }
            val remaining = INLINE_SYNC_MIN_VISIBLE_MS - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            inlineSyncVisible = false
            inlineSyncShownAt = null
        }
    }

    val hasSelectionInCurrentTab = when (currentTabId) {
        LibraryTabId.PLAYLISTS -> isPlaylistSelectionMode
        LibraryTabId.ALBUMS -> isAlbumSelectionMode
        LibraryTabId.SONGS,
        LibraryTabId.LIKED,
        LibraryTabId.FOLDERS -> isSelectionMode
        LibraryTabId.ARTISTS -> isArtistSelectionMode
    }
    val canHandleFolderBack by remember {
        derivedStateOf {
            currentTabId == LibraryTabId.FOLDERS &&
                    canNavigateBackInFolders &&
                    !isSortSheetVisible
        }
    }

    BackHandler(enabled = hasSelectionInCurrentTab || canHandleFolderBack) {
        when {
            hasSelectionInCurrentTab -> {
                when (currentTabId) {
                    LibraryTabId.PLAYLISTS -> {
                        playlistMultiSelectionState.clearSelection()
                        showPlaylistMultiSelectionSheet = false
                        showMergePlaylistDialog = false
                        pendingMergePlaylistIds = emptyList()
                    }

                    LibraryTabId.ALBUMS -> {
                        selectedAlbums = persistentListOf()
                        multiSelectionState.clearSelection()
                        showMultiSelectionSheet = false
                    }

                    LibraryTabId.ARTISTS -> {
                        selectedArtists = persistentListOf()
                        multiSelectionState.clearSelection()
                        showMultiSelectionSheet = false
                    }

                    LibraryTabId.SONGS,
                    LibraryTabId.LIKED,
                    LibraryTabId.FOLDERS -> {
                        multiSelectionState.clearSelection()
                        showMultiSelectionSheet = false
                    }

                }
            }

            canHandleFolderBack -> {
                playerViewModel.navigateBackFolder()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playlistViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playlistViewModel.playlistCreationEvent.collect { success ->
                if (success) {
                    showCreatePlaylistDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_playlist_created), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        traceSection("LibraryScreen.InitialTabLoad") {
            playerViewModel.onLibraryTabSelected(normalizedLastTabIndex)
        }
    }

    LaunchedEffect(currentTabIndex) {
        traceSection("LibraryScreen.PageChangeTabLoad") {
            playerViewModel.onLibraryTabSelected(currentTabIndex)
        }

        multiSelectionState.clearSelection()
        playlistMultiSelectionState.clearSelection()
        selectedAlbums = persistentListOf()
        selectedArtists = persistentListOf()
        showMultiSelectionSheet = false
        showPlaylistMultiSelectionSheet = false
    }

    val fabState by remember { derivedStateOf { currentTabIndex } }
    val transition = updateTransition(
        targetState = fabState,
        label = "Action Button Icon Transition"
    )

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)

    val dm = LocalPixelPlayerDarkTheme.current

    val iconRotation by transition.animateFloat(
        label = "Action Button Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (tabTitles.getOrNull(page)?.toLibraryTabIdOrNull()) {
            LibraryTabId.PLAYLISTS -> 0f
            else -> 360f
        }
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val gradientColorsDark = remember(primaryContainer) {
        listOf(primaryContainer.copy(alpha = 0.5f), Color.Transparent).toImmutableList()
    }
    val gradientColorsLight = remember(onPrimaryContainer) {
        listOf(onPrimaryContainer.copy(alpha = 0.2f), Color.Transparent).toImmutableList()
    }

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val currentTab = tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull() ?: currentTabId
    val currentTabTitle = currentTab.displayTitle()

    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    Scaffold(
        modifier = Modifier.background(brush = gradientBrush),
        topBar = {
            Column(
                modifier = Modifier.background(headerContainerColor)
            ) {
                TopAppBar(
                    title = {
                        if (isCompactNavigation) {
                            LibraryNavigationCompactTitle(
                                modifier = Modifier.padding(start = 2.dp),
                                title = currentTabTitle,
                                pageIndex = pagerState.currentPage,
                                onClick = {
                                    showTabSwitcherSheet = true
                                },
                                onSwipeLeft = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                onSwipeRight = {
                                    scope.launch {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                }
                            )
                        } else {
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = stringResource(R.string.presentation_batch_d_library_title),
                                fontFamily = RoundedSans,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 40.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        FilledIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            onClick = {
                                navController.navigateSafely(Screen.AudioBookmarks.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = stringResource(R.string.audio_bookmarks_cd_open)
                            )
                        }
                        FilledIconButton(
                            modifier = Modifier.padding(end = 14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            onClick = {
                                navController.navigateSafely(Screen.Settings.route)
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_settings_24),
                                contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
                if (!isCompactNavigation) {
                    val showTabIndicator = false
                    PrimaryScrollableTabRow(
                        selectedTabIndex = currentTabIndex,
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        indicator = {
                            if (showTabIndicator) {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex = currentTabIndex),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, rawId ->
                            val tabId = rawId.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
                            TabAnimation(
                                index = index,
                                title = tabId.storageKey,
                                selectedIndex = currentTabIndex,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            targetPageForTabIndex(
                                                currentPage = pagerState.currentPage,
                                                targetTabIndex = index,
                                                tabCount = tabTitles.size,
                                                compactMode = isCompactNavigation
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(tabId.titleRes).uppercase(LocalLocale.current.platformLocale),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (currentTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        TabAnimation(
                            index = -1,
                            title = stringResource(R.string.presentation_batch_d_edit_library_tabs_cd),
                            selectedIndex = currentTabIndex,
                            onClick = { showReorderTabsSheet = true }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.presentation_batch_d_reorder_tabs_cd),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else {
                    CompactLibraryPagerIndicator(
                        currentIndex = currentTabIndex,
                        pageCount = tabTitles.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                    )
                }
            }
        }
    ) { innerScaffoldPadding ->
        val playerUiState by remember(playerViewModel) {
            playerViewModel.playerUiState
                .map { uiState -> uiState.toLibraryScreenProjection() }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = LibraryScreenPlayerProjection())
        val isLibraryContentEmpty by remember(playerViewModel) {
            combine(
                playerViewModel.songCountFlow,
                playerViewModel.albumsFlow,
                playerViewModel.artistsFlow
            ) { songCount, albums, artists ->
                songCount == 0 && albums.isEmpty() && artists.isEmpty()
            }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = true)

        Box(
            modifier = Modifier
                .padding(top = innerScaffoldPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .background(color = headerContainerColor)
                    .fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp, vertical = 0.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 34.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 0.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 0.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusTR = 34.dp,
                        smoothnessAsPercentTL = 60
                    )
                ) {
                    Column(Modifier.fillMaxSize()) {
                        val availableSortOptions by playerViewModel.availableSortOptions.collectAsStateWithLifecycle()
                        val sanitizedSortOptions = remember(availableSortOptions, currentTabId) {
                            val cleaned = availableSortOptions.filterIsInstance<SortOption>()
                            val ensured = if (cleaned.any { option ->
                                    option.storageKey == currentTabId.defaultSort.storageKey
                                }
                            ) {
                                cleaned
                            } else {
                                buildList {
                                    add(currentTabId.defaultSort)
                                    addAll(cleaned)
                                }
                            }

                            val distinctByKey = ensured.distinctBy { it.storageKey }
                            distinctByKey.ifEmpty { listOf(currentTabId.defaultSort) }.toImmutableList()
                        }

                        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
                        val visiblePlaylists = playlistUiState.playlists
                        val allSongsLazyPagingItems = libraryViewModel.songsPagingFlow.collectAsLazyPagingItems()
                        val albumsLazyPagingItems = libraryViewModel.albumsPagingFlow.collectAsLazyPagingItems()
                        val artistsLazyPagingItems = libraryViewModel.artistsPagingFlow.collectAsLazyPagingItems()
                        val favoritePagingItems = libraryViewModel.favoritesPagingFlow.collectAsLazyPagingItems()
                        val isLibraryLoading by libraryViewModel.isLoadingLibrary.collectAsStateWithLifecycle()
                        val hasCurrentSong by remember(playerViewModel) {
                            playerViewModel.stablePlayerState
                                .map { state -> state.currentSong != null && state.currentSong != Song.emptySong() }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val isShuffleEnabled by remember(playerViewModel) {
                            playerViewModel.stablePlayerState
                                .map { it.isShuffleEnabled }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)

                        val currentSelectedSortOption: SortOption? = when (currentTabId) {
                            LibraryTabId.SONGS -> playerUiState.currentSongSortOption
                            LibraryTabId.ALBUMS -> playerUiState.currentAlbumSortOption
                            LibraryTabId.ARTISTS -> playerUiState.currentArtistSortOption
                            LibraryTabId.PLAYLISTS -> playlistUiState.currentPlaylistSortOption
                            LibraryTabId.LIKED -> playerUiState.currentFavoriteSortOption
                            LibraryTabId.FOLDERS -> playerUiState.currentFolderSortOption
                        }

                        val showLocateButton = when (currentTabId) {
                            LibraryTabId.SONGS -> songsShowLocateButton
                            LibraryTabId.LIKED -> likedShowLocateButton
                            LibraryTabId.FOLDERS -> foldersShowLocateButton
                            else -> false
                        }
                        val locateAction = when (currentTabId) {
                            LibraryTabId.SONGS -> songsLocateAction
                            LibraryTabId.LIKED -> likedLocateAction
                            LibraryTabId.FOLDERS -> foldersLocateAction
                            else -> null
                        }

                        val onSortOptionChanged: (SortOption) -> Unit = remember(playerViewModel, playlistViewModel, currentTabId) {
                            { option ->
                                when (currentTabId) {
                                    LibraryTabId.SONGS -> playerViewModel.sortSongs(option)
                                    LibraryTabId.ALBUMS -> playerViewModel.sortAlbums(option)
                                    LibraryTabId.ARTISTS -> playerViewModel.sortArtists(option)
                                    LibraryTabId.PLAYLISTS -> playlistViewModel.sortPlaylists(option)
                                    LibraryTabId.LIKED -> playerViewModel.sortFavoriteSongs(option)
                                    LibraryTabId.FOLDERS -> playerViewModel.sortFolders(option)
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = hasSelectionInCurrentTab,
                            label = "ActionRowModeSwitch",
                            transitionSpec = {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            },
                            modifier = Modifier
                                .padding(
                                    top = 6.dp,
                                    start = 10.dp,
                                    end = 10.dp
                                )
                                .heightIn(min = 56.dp)
                        ) { inSelectionMode ->
                            if (inSelectionMode) {
                                if (currentTabId == LibraryTabId.PLAYLISTS && isPlaylistSelectionMode) {
                                    SelectionActionRow(
                                        selectedCount = selectedPlaylists.size,
                                        onSelectAll = {
                                            playerViewModel.playlistSelectionStateHolder.selectAll(visiblePlaylists)
                                        },
                                        onDeselect = { playerViewModel.playlistSelectionStateHolder.clearSelection() },
                                        onOptionsClick = { showPlaylistMultiSelectionSheet = true }
                                    )
                                } else if (currentTabId == LibraryTabId.ALBUMS && isAlbumSelectionMode) {
                                    SelectionActionRow(
                                        selectedCount = selectedAlbums.size,
                                        onSelectAll = {
                                            multiSelectionState.clearSelection()
                                            showMultiSelectionSheet = false
                                            selectedAlbums = appendDistinctSelection(
                                                current = selectedAlbums,
                                                candidates = playerViewModel.albumsFlow.value,
                                                keyOf = Album::id
                                            ).toImmutableList()
                                        },
                                        onDeselect = {
                                            selectedAlbums = persistentListOf()
                                            multiSelectionState.clearSelection()
                                            showMultiSelectionSheet = false
                                        },
                                        onOptionsClick = openSelectedAlbumActions
                                    )
                                } else if (currentTabId == LibraryTabId.ARTISTS && isArtistSelectionMode) {
                                    SelectionActionRow(
                                        selectedCount = selectedArtists.size,
                                        onSelectAll = {
                                            multiSelectionState.clearSelection()
                                            showMultiSelectionSheet = false
                                            selectedArtists = appendDistinctSelection(
                                                current = selectedArtists,
                                                candidates = playerViewModel.artistsFlow.value,
                                                keyOf = Artist::id
                                            ).toImmutableList()
                                        },
                                        onDeselect = {
                                            selectedArtists = persistentListOf()
                                            multiSelectionState.clearSelection()
                                            showMultiSelectionSheet = false
                                        },
                                        onOptionsClick = openSelectedArtistActions
                                    )
                                } else {
                                    SelectionActionRow(
                                        selectedCount = selectedSongs.size,
                                        onSelectAll = {
                                            when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                                LibraryTabId.LIKED -> {
                                                    multiSelectionState.selectAll(favoritePagingItems.itemSnapshotList.items)
                                                }
                                                LibraryTabId.FOLDERS -> {
                                                    val songsToSelect =
                                                        playerViewModel.playerUiState.value.currentFolder?.songs ?: emptyList()
                                                    multiSelectionState.selectAll(songsToSelect)
                                                }
                                                LibraryTabId.SONGS -> {
                                                    scope.launch {
                                                        val songsToSelect =
                                                            playerViewModel.getSongsForCurrentLibrarySelection()
                                                        multiSelectionState.selectAll(songsToSelect)
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        },
                                        onDeselect = { multiSelectionState.clearSelection() },
                                        onOptionsClick = { showMultiSelectionSheet = true }
                                    )
                                }
                            } else {
                                LibraryActionRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 4.dp),
                                    onMainActionClick = {
                                        when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                            LibraryTabId.PLAYLISTS -> showPlaylistCreationTypeDialog = true
                                            LibraryTabId.LIKED -> playerViewModel.shuffleFavoriteSongs()
                                            LibraryTabId.ALBUMS -> playerViewModel.shuffleRandomAlbum()
                                            LibraryTabId.ARTISTS -> playerViewModel.shuffleRandomArtist()
                                            else -> playerViewModel.shuffleAllSongs()
                                        }
                                    },
                                    iconRotation = iconRotation,
                                    showSortButton = sanitizedSortOptions.isNotEmpty(),
                                    showLocateButton = showLocateButton,
                                    onSortClick = { playerViewModel.showSortingSheet() },
                                    onLocateClick = { locateAction?.invoke() },
                                    isPlaylistTab = currentTabId == LibraryTabId.PLAYLISTS,
                                    isFoldersTab = currentTabId == LibraryTabId.FOLDERS && (!playerUiState.isFoldersPlaylistView || playerUiState.currentFolder != null),
                                    onImportM3uClick = { m3uImportLauncher.launch("audio/x-mpegurl") },
                                    currentFolder = playerUiState.currentFolder,
                                    folderRootPath = playerUiState.folderSourceRootPath.ifBlank {
                                        Environment.getExternalStorageDirectory().path
                                    },
                                    folderRootLabel = playerUiState.folderSource.displayName,
                                    onFolderClick = { playerViewModel.navigateToFolder(it) },
                                    onNavigateBack = { playerViewModel.navigateBackFolder() },
                                    isShuffleEnabled = isShuffleEnabled,
                                    showStorageFilterButton = currentTabId == LibraryTabId.SONGS ||
                                            currentTabId == LibraryTabId.ALBUMS ||
                                            currentTabId == LibraryTabId.ARTISTS ||
                                            currentTabId == LibraryTabId.LIKED ||
                                            (ENABLE_FOLDERS_STORAGE_FILTER && currentTabId == LibraryTabId.FOLDERS),
                                    currentStorageFilter = playerUiState.currentStorageFilter,
                                    onStorageFilterClick = { playerViewModel.toggleStorageFilter() }
                                )
                            }
                        }

                        LibraryInlineSyncIndicator(
                            visible = inlineSyncVisible && !isLibraryContentEmpty,
                            syncManager = syncManager
                        )

                        if (isSortSheetVisible && sanitizedSortOptions.isNotEmpty()) {
                            val currentSelectionKey = currentSelectedSortOption?.storageKey
                            val selectedOptionForSheet = sanitizedSortOptions.firstOrNull { option ->
                                option.storageKey == currentSelectionKey
                            }
                                ?: sanitizedSortOptions.firstOrNull { option ->
                                    option.storageKey == currentTabId.defaultSort.storageKey
                                }
                                ?: sanitizedSortOptions.first()


                            val isAlbumTab = currentTabId == LibraryTabId.ALBUMS
                            val isFoldersTab = currentTabId == LibraryTabId.FOLDERS

                            LibrarySortBottomSheet(
                                title = stringResource(R.string.presentation_batch_d_sort_by),
                                options = sanitizedSortOptions,
                                selectedOption = selectedOptionForSheet,
                                onDismiss = { playerViewModel.hideSortingSheet() },
                                onOptionSelected = { option ->
                                    onSortOptionChanged(option)
                                    playerViewModel.hideSortingSheet()
                                },
                                onDirectionToggle = { option ->
                                    onSortOptionChanged(option)
                                },
                                showViewToggle = isFoldersTab,
                                viewSectionTitle = stringResource(R.string.presentation_batch_d_view_section_view),
                                viewToggleLabel = stringResource(R.string.presentation_batch_d_playlist_view),
                                viewToggleChecked = playerUiState.isFoldersPlaylistView,
                                onViewToggleChange = { isChecked ->
                                    playerViewModel.setFoldersPlaylistView(isChecked)
                                },
                                viewToggleContent = if (isAlbumTab) {
                                    {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isList = playerUiState.isAlbumsListView
                                            val primaryColor = MaterialTheme.colorScheme.tertiaryContainer
                                            val onPrimaryColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                                            val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = !isList,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setAlbumsListView(false) },
                                                text = stringResource(R.string.presentation_batch_d_view_grid),
                                                imageVector = Icons.Rounded.ViewModule
                                            )

                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = isList,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setAlbumsListView(true) },
                                                text = stringResource(R.string.presentation_batch_d_view_list),
                                                imageVector = Icons.AutoMirrored.Rounded.ViewList
                                            )
                                        }
                                    }
                                } else null,
                                sourceToggleContent = if (isFoldersTab && ENABLE_FOLDERS_SOURCE_TOGGLE) {
                                    {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isSdAvailable = playerUiState.isSdCardAvailable
                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = playerUiState.folderSource == FolderSource.INTERNAL,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setFoldersSource(FolderSource.INTERNAL) },
                                                text = stringResource(R.string.presentation_batch_d_storage_internal)
                                            )
                                            ToggleSegmentButton(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .alpha(if (isSdAvailable) 1f else 0.5f),
                                                active = playerUiState.folderSource == FolderSource.SD_CARD,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = {
                                                    if (isSdAvailable) {
                                                        playerViewModel.setFoldersSource(FolderSource.SD_CARD)
                                                    }
                                                },
                                                text = stringResource(R.string.presentation_batch_d_storage_sd_card)
                                            )
                                        }
                                        if (!playerUiState.isSdCardAvailable) {
                                            Text(
                                                text = stringResource(R.string.presentation_batch_d_sd_card_unavailable),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                                            )
                                        }
                                    }
                                } else null,
                                extraContent = {
                                    if (!isFoldersTab) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.presentation_batch_d_cloud_sources_heading),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontFamily = com.lostf1sh.pixelplayeross.ui.theme.RoundedSans,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                                        )
                                        com.lostf1sh.pixelplayeross.presentation.components.LibrarySheetToggleCard(
                                            label = stringResource(R.string.presentation_batch_d_cloud_only),
                                            checked = playerUiState.hideLocalMedia,
                                            boxBackgroundColor = if (playerUiState.hideLocalMedia)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                            boxCornerRadius = if (playerUiState.hideLocalMedia) 18.dp else 50.dp,
                                            onCheckedChange = { playerViewModel.setHideLocalMedia(it) }
                                        )
                                    }
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                pageSpacing = 0.dp,
                                beyondViewportPageCount = 1,
                                key = { it }
                            ) { page ->
                                val tabIndex = resolveTabIndex(
                                    page = page,
                                    tabCount = tabTitles.size,
                                    compactMode = isCompactNavigation
                                )
                                when (tabTitles.getOrNull(tabIndex)?.toLibraryTabIdOrNull()) {
                                    LibraryTabId.SONGS -> {
                                        LibrarySongsTab(
                                            songs = allSongsLazyPagingItems,
                                            isLoading = isLibraryLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = {
                                                onRefresh()
                                                allSongsLazyPagingItems.refresh()
                                            },
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            onLocateCurrentSongVisibilityChanged = { songsShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { songsLocateAction = it },
                                            sortOption = playerUiState.currentSongSortOption,
                                            storageFilter = playerUiState.currentStorageFilter,
                                            hasCurrentSong = hasCurrentSong
                                        )
                                    }
                                    LibraryTabId.ALBUMS -> {
                                        val isLoading = playerUiState.isLoadingLibraryCategories

                                        val stableOnAlbumClick: (Long) -> Unit = remember(navController) {
                                            { albumId: Long ->
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.AlbumDetail.createRoute(albumId),
                                                    patternToPop = Screen.AlbumDetail.route
                                                )
                                            }
                                        }
                                        LibraryAlbumsTab(
                                            albums = albumsLazyPagingItems,
                                            isLoading = isLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            isListView = playerUiState.isAlbumsListView,
                                            currentAlbumSortOption = playerUiState.currentAlbumSortOption,
                                            onAlbumClick = stableOnAlbumClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isAlbumSelectionMode,
                                            selectedAlbumIds = selectedAlbumIds,
                                            onAlbumLongPress = onAlbumLongPress,
                                            onAlbumSelectionToggle = onAlbumSelectionToggle,
                                            getSelectionIndex = getAlbumSelectionIndex,
                                            storageFilter = playerUiState.currentStorageFilter
                                        )
                                    }

                                    LibraryTabId.ARTISTS -> {
                                        val isLoading = playerUiState.isLoadingLibraryCategories

                                        LibraryArtistsTab(
                                            artists = artistsLazyPagingItems,
                                            isLoading = isLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            currentArtistSortOption = playerUiState.currentArtistSortOption,
                                            onArtistClick = { artistId ->
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.ArtistDetail.createRoute(artistId),
                                                    patternToPop = Screen.ArtistDetail.route
                                                )
                                            },
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isArtistSelectionMode,
                                            selectedArtistIds = selectedArtistIds,
                                            onArtistLongPress = onArtistLongPress,
                                            onArtistSelectionToggle = onArtistSelectionToggle,
                                            getSelectionIndex = getArtistSelectionIndex,
                                            storageFilter = playerUiState.currentStorageFilter
                                        )
                                    }

                                    LibraryTabId.PLAYLISTS -> {
                                        LibraryPlaylistsTab(
                                            playlistUiState = playlistUiState,
                                            filteredPlaylists = visiblePlaylists,
                                            navController = navController,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isPlaylistSelectionMode,
                                            selectedPlaylistIds = selectedPlaylistIds,
                                            onPlaylistLongPress = onPlaylistLongPress,
                                            onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                                            onPlaylistOptionsClick = { showPlaylistMultiSelectionSheet = true }
                                        )
                                    }

                                    LibraryTabId.LIKED -> {
                                        LibraryFavoritesTab(
                                            favoriteSongs = favoritePagingItems,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = {
                                                onRefresh()
                                                favoritePagingItems.refresh()
                                            },
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            sortOption = playerUiState.currentFavoriteSortOption,
                                            onLocateCurrentSongVisibilityChanged = { likedShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { likedLocateAction = it },
                                            storageFilter = playerUiState.currentStorageFilter,
                                            hasCurrentSong = hasCurrentSong
                                        )
                                    }

                                    LibraryTabId.FOLDERS -> {
                                        val folders = playerUiState.musicFolders
                                        val currentFolder = playerUiState.currentFolder
                                        val isLoading = playerUiState.isLoadingLibraryCategories
                                        val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
                                        val defaultFolderName = stringResource(R.string.presentation_batch_d_folder_name_fallback)

                                        LibraryFoldersTab(
                                            folders = folders,
                                            currentFolder = currentFolder,
                                            isLoading = isLoading,
                                            bottomBarHeight = bottomBarHeightDp,
                                            stablePlayerState = stablePlayerState,
                                            onNavigateBack = { playerViewModel.navigateBackFolder() },
                                            onFolderClick = { folderPath -> playerViewModel.navigateToFolder(folderPath) },
                                            onFolderAsPlaylistClick = { folder ->
                                                val encodedPath = Uri.encode(folder.path)
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.PlaylistDetail.createRoute(
                                                        "${PlaylistViewModel.FOLDER_PLAYLIST_PREFIX}$encodedPath"
                                                    ),
                                                    patternToPop = Screen.PlaylistDetail.route
                                                )
                                            },
                                            onPlaySong = { song, queue ->
                                                playerViewModel.showAndPlaySong(
                                                    song,
                                                    queue,
                                                    currentFolder?.name ?: defaultFolderName
                                                )
                                            },
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isPlaylistView = playerUiState.isFoldersPlaylistView,
                                            currentSortOption = playerUiState.currentFolderSortOption,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            onLocateCurrentSongVisibilityChanged = { foldersShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { foldersLocateAction = it },
                                            pendingLocatePath = pendingFoldersLocatePath,
                                            onClearPendingLocate = { pendingFoldersLocatePath = null },
                                            onRequestCrossFolderLocate = { folderPath ->
                                                pendingFoldersLocatePath = folderPath
                                                playerViewModel.navigateToFolder(folderPath)
                                            }
                                        )
                                    }

                                    null -> Unit
                                }
                            }

                            val selectionCount = when (currentTabId) {
                                LibraryTabId.PLAYLISTS -> selectedPlaylists.size.takeIf { isPlaylistSelectionMode } ?: 0
                                LibraryTabId.ALBUMS -> selectedAlbums.size.takeIf { isAlbumSelectionMode } ?: 0
                                LibraryTabId.ARTISTS -> selectedArtists.size.takeIf { isArtistSelectionMode } ?: 0
                                LibraryTabId.SONGS,
                                LibraryTabId.LIKED,
                                LibraryTabId.FOLDERS -> selectedSongs.size
                            }
                            SelectionCountPill(
                                selectedCount = selectionCount,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(1f)
                            )
                        }
                    }
                }
                if (
                    isLibraryContentEmpty &&
                    (
                            playerUiState.isSyncingLibrary ||
                                    playerUiState.isLoadingInitialSongs ||
                                    playerUiState.isLoadingLibraryCategories
                            )
                ) {
                    LibrarySyncOverlay(syncManager = syncManager)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(bottomGradientHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.2f to Color.Transparent,
                                0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                                1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    )
            ) {

            }
        }
    }



    PlaylistCreationTypeDialog(
        visible = showPlaylistCreationTypeDialog,
        onDismiss = { showPlaylistCreationTypeDialog = false },
        onManualSelected = {
            showPlaylistCreationTypeDialog = false
            showCreatePlaylistDialog = true
        },
        onDescribeSelected = {
            showPlaylistCreationTypeDialog = false
            showDescribePlaylistDialog = true
        }
    )

    val nlpPlaylistPreviewState by playlistViewModel.nlpPlaylistPreviewState.collectAsStateWithLifecycle()
    DescribePlaylistDialog(
        visible = showDescribePlaylistDialog,
        state = nlpPlaylistPreviewState,
        onGenerate = playlistViewModel::generateNlpPlaylistPreview,
        onSave = { name, songIds ->
            playlistViewModel.createPlaylist(name = name, songIds = songIds)
            playlistViewModel.resetNlpPlaylistPreview()
            showDescribePlaylistDialog = false
        },
        onDismiss = {
            playlistViewModel.resetNlpPlaylistPreview()
            showDescribePlaylistDialog = false
        }
    )

    CreatePlaylistDialog(
        visible = showCreatePlaylistDialog,
        onDismiss = { showCreatePlaylistDialog = false },
        onCreate = { name, imageUri, color, icon, songIds, cropScale, cropPanX, cropPanY, shapeType, d1, d2, d3, d4, smartRuleKey ->
            playlistViewModel.createPlaylist(
                name = name,
                coverImageUri = imageUri,
                coverColor = color,
                coverIcon = icon,
                songIds = songIds,
                cropScale = cropScale,
                cropPanX = cropPanX,
                cropPanY = cropPanY,
                isQueueGenerated = false,
                coverShapeType = shapeType,
                coverShapeDetail1 = d1,
                coverShapeDetail2 = d2,
                coverShapeDetail3 = d3,
                coverShapeDetail4 = d4,
                smartRuleKey = smartRuleKey
            )
        }
    )


    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) { derivedStateOf { currentSong?.let {
            favoriteIds.contains(
                it.id)
        } } }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_added_to_queue))
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_playing_next))
                },
                onAddToPlayList = {
                    playlistSheetSongs = persistentListOf(currentSong)
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate, customMetadataChanges ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate,
                        customMetadataChanges
                    )
                },
                removeFromListTrigger = {},
                songInfoViewModel = songInfoBottomSheetViewModel
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = { showPlaylistBottomSheet = false },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    val clearResolvedCategorySelection: () -> Unit = {
        selectedAlbums = persistentListOf()
        selectedArtists = persistentListOf()
    }

    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                clearResolvedCategorySelection()
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                clearResolvedCategorySelection()
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                clearResolvedCategorySelection()
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                clearResolvedCategorySelection()
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                clearResolvedCategorySelection()
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                clearResolvedCategorySelection()
                showMultiSelectionSheet = false
            },
            onDownloadAll = {
                val cloudSongCount = CloudOfflineRepository.downloadCandidates(selectedSongs).size
                cloudDownloadsViewModel.downloadSelected(selectedSongs)
                multiSelectionState.clearSelection()
                clearResolvedCategorySelection()
                playerViewModel.sendToast(
                    context.getString(R.string.cloud_download_selected_started, cloudSongCount)
                )
                showMultiSelectionSheet = false
            },
            onDeleteAll = { deleteActivity, onComplete ->
                playerViewModel.deleteSelectedFromDevice(deleteActivity, selectedSongs) {
                    clearResolvedCategorySelection()
                    showMultiSelectionSheet = false
                    onComplete(true)
                }
            },
            onBatchEdit = {
                showMultiSelectionSheet = false
                showBatchEditSheet = true
            }
        )
    }

    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = {
                showPlaylistMultiSelectionSheet = false
            },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onMergeAll = {
                pendingMergePlaylistIds = selectedPlaylistIds.toList()
                showMergePlaylistDialog = true
                showPlaylistMultiSelectionSheet = false
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            }
        )
    }

    if (showTabSwitcherSheet) {
        LibraryTabSwitcherSheet(
            tabs = tabTitles,
            currentIndex = currentTabIndex,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        targetPageForTabIndex(
                            currentPage = pagerState.currentPage,
                            targetTabIndex = index,
                            tabCount = tabTitles.size,
                            compactMode = isCompactNavigation
                        )
                    )
                }
                showTabSwitcherSheet = false
            },
            onEditClick = {
                showTabSwitcherSheet = false
                showReorderTabsSheet = true
            },
            onDismiss = { showTabSwitcherSheet = false }
        )
    }

    if (showReorderTabsSheet) {
        ReorderTabsSheet(
            tabs = tabTitles,
            onReorder = { newOrder ->
                playerViewModel.saveLibraryTabsOrder(newOrder)
            },
            onReset = {
                playerViewModel.resetLibraryTabsOrder()
            },
            onDismiss = { showReorderTabsSheet = false }
        )
    }

    if (showMergePlaylistDialog && pendingMergePlaylistIds.isNotEmpty()) {
        var mergePlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showMergePlaylistDialog = false
                pendingMergePlaylistIds = emptyList()
                mergePlaylistName = ""
            },
            title = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.presentation_batch_d_merge_playlists_prompt))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mergePlaylistName,
                        onValueChange = { mergePlaylistName = it },
                        placeholder = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.presentation_batch_d_merge_playlists_body,
                            pendingMergePlaylistIds.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mergePlaylistName.isNotEmpty()) {
                            playlistViewModel.mergePlaylistsIntoOne(
                                pendingMergePlaylistIds,
                                mergePlaylistName
                            )
                            playlistMultiSelectionState.clearSelection()
                            showMergePlaylistDialog = false
                            pendingMergePlaylistIds = emptyList()
                            mergePlaylistName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMergePlaylistDialog = false
                    pendingMergePlaylistIds = emptyList()
                    mergePlaylistName = ""
                }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showBatchEditSheet && selectedSongs.isNotEmpty()) {
        EditMultipleSongsSheet(
            visible = showBatchEditSheet,
            songs = selectedSongs,
            onDismiss = { showBatchEditSheet = false },
            onSave = { songs, title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.saveBatchMetadata(
                    songs = songs,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    composer = composer,
                    genre = genre,
                    lyrics = lyrics,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    replayGainTrackGainDb = replayGainTrackGainDb,
                    replayGainAlbumGainDb = replayGainAlbumGainDb,
                    coverArtUpdate = coverArtUpdate
                )
                clearResolvedCategorySelection()
            }
        )
    }
}

@Composable
private fun CompactLibraryPagerIndicator(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val safeIndex = positiveMod(currentIndex, pageCount)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == safeIndex
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 10.dp,
                label = "LibraryCompactPagerIndicatorWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                label = "LibraryCompactPagerIndicatorAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Slim, non-intrusive indicator for sync work that should not keep the list pulled
 * down: automatic startup syncs, background maintenance, and manual refreshes after
 * the short pull-to-refresh confirmation window. It sits just below
 * [LibraryActionRow] and collapses to zero height when not active.
 *
 * Distinct from [LibrarySyncOverlay], which is reserved for initial empty-library
 * loads. The parent screen also gates this indicator off while the pull spinner is
 * visible, so the two feedback channels do not compete.
 */
@Composable
private fun LibraryInlineSyncIndicator(
    visible: Boolean,
    syncManager: com.lostf1sh.pixelplayeross.data.worker.SyncManager
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(180)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(160))
    ) {
        val syncProgress by syncManager.syncProgress
            .collectAsStateWithLifecycle(initialValue = SyncProgress())

        val phaseLabel = when (syncProgress.phase) {
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                stringResource(R.string.sync_scanning)
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                stringResource(R.string.sync_processing)
            SyncProgress.SyncPhase.SCANNING_LRC ->
                stringResource(R.string.library_background_sync_lyrics)
            SyncProgress.SyncPhase.CLEANING_CACHE ->
                stringResource(R.string.library_background_sync_cache)
            SyncProgress.SyncPhase.SYNCING_CLOUD ->
                stringResource(R.string.library_background_sync_cloud)
            else ->
                stringResource(R.string.sync_in_progress)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * P1-1: Isolated sync/loading overlay composable.
 *
 * By collecting [SyncManager.syncProgress] HERE instead of in the parent [LibraryScreen],
 * only this small subtree recomposes on every progress tick (e.g., file count updates
 * during a library scan). The rest of [LibraryScreen] — including the Scaffold, pager,
 * and all tab content — remains unaffected during sync.
 */
@Composable
private fun LibrarySyncOverlay(syncManager: com.lostf1sh.pixelplayeross.data.worker.SyncManager) {
    val syncProgress by syncManager.syncProgress
        .collectAsStateWithLifecycle(initialValue = SyncProgress())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                if (syncProgress.hasProgress && syncProgress.isRunning) {
                    SyncProgressBar(
                        syncProgress = syncProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.syncing_library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Compact library header title. Measures the localized tab name and compresses it into the space
 * left by the header actions by animating the variable font width axis, weight, horizontal scale
 * and letter spacing, so long translations still fit on one line. Tapping opens the tab switcher,
 * swiping horizontally moves between tabs.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun LibraryNavigationCompactTitle(
    modifier: Modifier = Modifier,
    title: String,
    pageIndex: Int,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    // Nudges the baseline so the title lines up with the header action buttons.
    val verticalOffset = 2.dp

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var availableWidthPx by remember { mutableIntStateOf(0) }

    val rondValue = 46f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .onSizeChanged { availableWidthPx = it.width }
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(Unit) {
                var totalDrag = 0f
                val swipeThresholdPx = 50.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        if (totalDrag > swipeThresholdPx) {
                            onSwipeRight()
                        } else if (totalDrag < -swipeThresholdPx) {
                            onSwipeLeft()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val availableWidth = if (availableWidthPx > 0) {
            with(density) { availableWidthPx.toDp() }
        } else {
            400.dp
        }

        val displayedTitle = remember(title) {
            title.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }

        val baseTextStyle = remember {
            TextStyle(
                fontFamily = FontFamily(
                    Font(
                        resId = R.font.gflex_variable,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(800),
                            FontVariation.width(LibraryCompactTitleWidthMax),
                            FontVariation.slant(-10f),
                            FontVariation.Setting("ROND", rondValue),
                            FontVariation.Setting("XTRA", 520f),
                            FontVariation.Setting("YOPQ", 90f),
                            FontVariation.Setting("YTLC", 505f)
                        )
                    )
                ),
                fontSize = 40.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.2).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }

        val idealTextWidth = remember(displayedTitle, baseTextStyle) {
            with(density) {
                textMeasurer.measure(
                    text = AnnotatedString(displayedTitle),
                    style = baseTextStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width.toDp()
            }
        }

        val safetyPadding = 24.dp
        val maxAllowedWidth = (availableWidth - safetyPadding).coerceAtLeast(0.dp)

        val widthCompressionRatio = if (idealTextWidth.value > 0f) {
            (maxAllowedWidth.value / idealTextWidth.value).coerceIn(0f, 1f)
        } else {
            1f
        }

        // Keep a 10% margin of safety: font variation rounding can otherwise trip the ellipsis
        // before the text has actually run out of room.
        val conservativeRatio = (widthCompressionRatio * 0.9f).coerceIn(0f, 1f)

        val targetWidthAxis = if (conservativeRatio >= 0.3f) {
            LibraryCompactTitleWidthMin +
                    (LibraryCompactTitleWidthMax - LibraryCompactTitleWidthMin) *
                    ((conservativeRatio - 0.3f) / 0.7f)
        } else {
            LibraryCompactTitleWidthMin
        }
        val animatedWidthAxis by animateFloatAsState(
            targetValue = targetWidthAxis,
            label = "LibraryHeaderTitleWidthAxis"
        )

        val targetWeight = remember(conservativeRatio) {
            if (conservativeRatio < 0.3f) {
                val ratio = (conservativeRatio / 0.3f).coerceIn(0f, 1f)
                200 + (600 * ratio).toInt()
            } else {
                800
            }
        }

        val targetFontSize = remember(conservativeRatio) {
            if (conservativeRatio < 0.4f) {
                val ratio = (conservativeRatio / 0.4f).coerceIn(0f, 1f)
                (14f + (26f * ratio)).sp
            } else {
                40.sp
            }
        }

        val targetScaleX = remember(conservativeRatio) {
            if (conservativeRatio < 0.3f) {
                0.5f + 0.5f * (conservativeRatio / 0.3f)
            } else {
                1f
            }
        }
        val animatedScaleX by animateFloatAsState(
            targetValue = targetScaleX,
            label = "LibraryHeaderTitleScaleX"
        )

        val targetLetterSpacing = remember(conservativeRatio) {
            if (conservativeRatio < 1f) {
                (-0.2 - (1.3 * (1f - conservativeRatio))).sp
            } else {
                (-0.2).sp
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val finalTextStyle = remember(
            animatedWidthAxis,
            targetFontSize,
            targetLetterSpacing,
            targetWeight,
            primaryColor
        ) {
            TextStyle(
                fontFamily = FontFamily(
                    Font(
                        resId = R.font.gflex_variable,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(targetWeight),
                            FontVariation.width(animatedWidthAxis.coerceAtLeast(1f)),
                            FontVariation.slant(-10f),
                            FontVariation.Setting("ROND", rondValue),
                            FontVariation.Setting("XTRA", 520f),
                            FontVariation.Setting("YOPQ", 90f),
                            FontVariation.Setting("YTLC", 505f)
                        )
                    )
                ),
                fontSize = targetFontSize,
                lineHeight = targetFontSize,
                letterSpacing = targetLetterSpacing,
                color = primaryColor,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = verticalOffset)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = Pair(pageIndex, displayedTitle),
                    transitionSpec = {
                        val diff = targetState.first - initialState.first
                        val direction = when {
                            diff == 0 -> 0
                            abs(diff) > 1 -> diff.coerceIn(-1, 1)
                            else -> diff
                        }

                        val slideIn = slideInHorizontally { fullWidth ->
                            if (direction >= 0) fullWidth else -fullWidth
                        } + fadeIn(animationSpec = tween(220))

                        val slideOut = slideOutHorizontally { fullWidth ->
                            if (direction >= 0) -fullWidth else fullWidth
                        } + fadeOut(animationSpec = tween(220))

                        slideIn.togetherWith(slideOut)
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "LibraryHeaderTitleAnimation"
                ) { (_, animatedTitle) ->
                    Text(
                        text = animatedTitle,
                        modifier = Modifier
                            .graphicsLayer {
                                this.scaleX = animatedScaleX
                                this.transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .padding(horizontal = 6.dp),
                        style = finalTextStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private const val LibraryCompactTitleWidthMin = 1f
private const val LibraryCompactTitleWidthMax = 100f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTabSwitcherSheet(
    tabs: ImmutableList<String>,
    currentIndex: Int,
    onTabSelected: (Int) -> Unit,
    onEditClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_d_library_tabs_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = RoundedSans
            )
            Text(
                text = stringResource(R.string.presentation_batch_d_library_tabs_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                itemsIndexed(
                    items = tabs,
                    key = { index, tab -> "$tab-$index" },
                    contentType = { _, _ -> "library_tab_item" }
                ) { index, rawId ->
                    val tabId = rawId.toLibraryTabIdOrNull() ?: return@itemsIndexed
                    LibraryTabGridItem(
                        tabId = tabId,
                        isSelected = index == currentIndex,
                        onClick = { onTabSelected(index) }
                    )
                }

                item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "reorder_tabs_action"
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp, max = 60.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onEditClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.presentation_batch_d_reorder_tabs_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabGridItem(
    tabId: LibraryTabId,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconContainer = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = tabId.iconRes()),
                    contentDescription = stringResource(tabId.titleRes),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = tabId.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private fun positiveMod(value: Int, mod: Int): Int {
    if (mod <= 0) return 0
    return ((value % mod) + mod) % mod
}

private fun infinitePagerInitialPage(tabCount: Int, selectedTabIndex: Int): Int {
    if (tabCount <= 0) return 0
    val midpoint = Int.MAX_VALUE / 2
    val aligned = midpoint - positiveMod(midpoint, tabCount)
    return aligned + positiveMod(selectedTabIndex, tabCount)
}

private fun resolveTabIndex(page: Int, tabCount: Int, compactMode: Boolean): Int {
    if (tabCount <= 0) return 0
    return if (compactMode) positiveMod(page, tabCount) else page.coerceIn(0, tabCount - 1)
}

private fun targetPageForTabIndex(
    currentPage: Int,
    targetTabIndex: Int,
    tabCount: Int,
    compactMode: Boolean
): Int {
    if (tabCount <= 0) return 0
    val safeTarget = positiveMod(targetTabIndex, tabCount)
    if (!compactMode) return safeTarget

    val currentBase = currentPage - positiveMod(currentPage, tabCount)
    val candidate = currentBase + safeTarget
    val prevCandidate = candidate - tabCount
    val nextCandidate = candidate + tabCount

    return listOf(prevCandidate, candidate, nextCandidate)
        .minByOrNull { abs(it - currentPage) }
        ?: candidate
}

private fun LibraryTabId.iconRes(): Int = when (this) {
    LibraryTabId.SONGS -> R.drawable.rounded_music_note_24
    LibraryTabId.ALBUMS -> R.drawable.rounded_album_24
    LibraryTabId.ARTISTS -> R.drawable.rounded_artist_24
    LibraryTabId.PLAYLISTS -> R.drawable.rounded_playlist_play_24
    LibraryTabId.FOLDERS -> R.drawable.rounded_folder_24
    LibraryTabId.LIKED -> R.drawable.round_favorite_24
}

@Composable
private fun LibraryTabId.displayTitle(): String = stringResource(titleRes)

internal fun resolveFolderNavigationDirection(initialPath: String?, targetPath: String?): Int =
    when {
        initialPath == targetPath -> FOLDER_NAVIGATION_FORWARD
        initialPath == null && targetPath != null -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath == null -> FOLDER_NAVIGATION_BACKWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(initialPath, targetPath) -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(targetPath, initialPath) -> FOLDER_NAVIGATION_BACKWARD
        else -> FOLDER_NAVIGATION_FORWARD
    }

private fun isDescendantFolderPath(ancestorPath: String, candidatePath: String): Boolean {
    val normalizedAncestor = ancestorPath.trimEnd(File.separatorChar)
    val normalizedCandidate = candidatePath.trimEnd(File.separatorChar)
    if (normalizedAncestor == normalizedCandidate) return false
    return normalizedCandidate.startsWith("$normalizedAncestor${File.separatorChar}")
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFoldersTab(
    folders: ImmutableList<MusicFolder>,
    currentFolder: MusicFolder?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderAsPlaylistClick: (MusicFolder) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    stablePlayerState: StablePlayerState,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isPlaylistView: Boolean = false,
    currentSortOption: SortOption = SortOption.FolderNameAZ,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    pendingLocatePath: String? = null,
    onClearPendingLocate: () -> Unit = {},
    onRequestCrossFolderLocate: (String) -> Unit = {}
) {


    AnimatedContent(
        targetState = Pair(isPlaylistView, currentFolder?.path ?: FOLDER_NAVIGATION_ROOT_KEY),
        label = "FolderNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = resolveFolderNavigationDirection(
                initialPath = initialState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY },
                targetPath = targetState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY }
            )
            val slideIn = slideInHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) -width else width
            } + fadeOut()

            slideIn.togetherWith(slideOut)
        }
    ) { (playlistMode, targetPath) ->
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
        val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
        var lastHandledFolderSortKey by remember { mutableStateOf(currentSortOption.storageKey) }
        var pendingFolderSortScrollReset by remember { mutableStateOf(false) }

        val flattenedFolders = remember(folders, currentSortOption) {
            sortMusicFoldersByOption(flattenFolders(folders), currentSortOption)
        }

        val isRoot = targetPath == FOLDER_NAVIGATION_ROOT_KEY
        val activeFolder = if (isRoot) null else currentFolder
        val showPlaylistCards = playlistMode && activeFolder == null
        val itemsToShow = remember(activeFolder, folders, flattenedFolders, currentSortOption) {
            when {
                showPlaylistCards -> flattenedFolders
                activeFolder != null -> sortMusicFoldersByOption(activeFolder.subFolders, currentSortOption)
                else -> sortMusicFoldersByOption(folders, currentSortOption)
            }
        }.toImmutableList()

        val songsToShow = remember(activeFolder, currentSortOption) {
            sortSongsForFolderView(activeFolder?.songs ?: emptyList(), currentSortOption)
        }.toImmutableList()
        val currentSong = stablePlayerState.currentSong
        val currentSongId = currentSong?.id
        val currentSongIndexInSongs = remember(songsToShow, currentSongId) {
            currentSongId?.let { songId -> songsToShow.indexOfFirst { it.id == songId } } ?: -1
        }
        val currentSongListIndex = remember(itemsToShow.size, currentSongIndexInSongs) {
            if (currentSongIndexInSongs < 0) -1 else itemsToShow.size + currentSongIndexInSongs
        }
        val songInCurrentFolder = currentSongIndexInSongs >= 0
        val currentSongParentPath: String? = remember(currentSong?.path) {
            currentSong?.path
                ?.takeIf { it.startsWith("/") }
                ?.let { File(it).parentFile?.absolutePath }
        }
        val canCrossFolderLocate = remember(
            playlistMode,
            songInCurrentFolder,
            currentSongParentPath,
            currentFolder?.path
        ) {
            !playlistMode &&
                !songInCurrentFolder &&
                currentSongParentPath != null &&
                currentSongParentPath != currentFolder?.path
        }
        val locateCurrentSongAction: (() -> Unit)? = remember(
            songInCurrentFolder,
            canCrossFolderLocate,
            currentSongListIndex,
            listState,
            currentSongParentPath
        ) {
            when {
                songInCurrentFolder -> {
                    {
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentSongListIndex)
                        }
                    }
                }
                canCrossFolderLocate && currentSongParentPath != null -> {
                    { onRequestCrossFolderLocate(currentSongParentPath) }
                }
                else -> null
            }
        }

        LaunchedEffect(locateCurrentSongAction) {
            registerActionCallback(locateCurrentSongAction)
        }

        LaunchedEffect(currentSortOption) {
            val currentSortKey = currentSortOption.storageKey
            if (currentSortKey == lastHandledFolderSortKey) return@LaunchedEffect
            lastHandledFolderSortKey = currentSortKey
            pendingFolderSortScrollReset = true
            listState.scrollToItem(0)
        }

        LaunchedEffect(itemsToShow, songsToShow, pendingFolderSortScrollReset) {
            if (!pendingFolderSortScrollReset) return@LaunchedEffect
            listState.scrollToItem(0)
            pendingFolderSortScrollReset = false
        }

        LaunchedEffect(
            currentFolder?.path,
            pendingLocatePath,
            currentSongListIndex,
            songsToShow
        ) {
            val pending = pendingLocatePath ?: return@LaunchedEffect
            if (currentFolder?.path != pending) return@LaunchedEffect
            if (currentSongListIndex < 0) return@LaunchedEffect
            listState.animateScrollToItem(currentSongListIndex)
            onClearPendingLocate()
        }

        LaunchedEffect(currentSongListIndex, itemsToShow, songsToShow, listState, canCrossFolderLocate) {
            if (canCrossFolderLocate) {
                visibilityCallback(true)
                return@LaunchedEffect
            }
            if (currentSongListIndex < 0 || songsToShow.isEmpty()) {
                visibilityCallback(false)
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

        val shouldShowLoading = isLoading && itemsToShow.isEmpty() && songsToShow.isEmpty() && isRoot

        Column(modifier = Modifier.fillMaxSize()) {
            when {
                shouldShowLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                itemsToShow.isEmpty() && songsToShow.isEmpty() -> {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.FOLDERS,
                        storageFilter = StorageFilter.OFFLINE,
                        bottomBarHeight = bottomBarHeight
                    )
                }

                else -> {
                    val foldersPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = foldersPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = foldersPullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp)
                                    .fillMaxSize()
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 26.dp,
                                            topEnd = 26.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap,
                                    top = 0.dp                            )
                            ) {
                                if (showPlaylistCards) {
                                    items(itemsToShow, key = { it.path }, contentType = { "folder_card" }) { folder ->
                                        FolderPlaylistItem(
                                            folder = folder,
                                            onClick = { onFolderAsPlaylistClick(folder) }
                                        )
                                    }
                                } else {
                                    items(itemsToShow, key = { it.path }, contentType = { "folder_list" }) { folder ->
                                        FolderListItem(
                                            folder = folder,
                                            onClick = { onFolderClick(folder.path) }
                                        )
                                    }
                                }

                                items(songsToShow, key = { it.id }, contentType = { "song" }) { song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = stablePlayerState.currentSong?.id == song.id && stablePlayerState.isPlaying,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        onMoreOptionsClick = { onMoreOptionsClick(song) },
                                        isSelected = selectedSongIds.contains(song.id),
                                        selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                        isSelectionMode = isSelectionMode,
                                        onLongPress = { onSongLongPress(song) },
                                        onClick = {
                                            if (isSelectionMode) {
                                                onSongSelectionToggle(song)
                                            } else {
                                                onPlaySong(song, songsToShow)
                                            }
                                        }
                                    )
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
                                listState = listState
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun FolderPlaylistItem(folder: MusicFolder, onClick: () -> Unit) {
    val previewSongs = remember(folder) { folder.collectAllSongs().take(9).toImmutableList() }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistArtCollage(
                songs = previewSongs,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RoundedSans),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatSongCount(folder.totalSongCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FolderListItem(folder: MusicFolder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = stringResource(R.string.presentation_batch_d_cd_folder),
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, ShapeCache.expressiveClover)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatSongCount(folder.totalSongCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun flattenFolders(folders: List<MusicFolder>): List<MusicFolder> {
    return folders.flatMap { folder ->
        val current = if (folder.songs.isNotEmpty()) listOf(folder) else emptyList()
        current + flattenFolders(folder.subFolders)
    }
}

private fun sortMusicFoldersByOption(folders: List<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
    return when (sortOption) {
        SortOption.FolderNameAZ -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderNameZA -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        else -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
    }
}

private fun sortSongsForFolderView(songs: List<Song>, sortOption: SortOption): List<Song> {
    return when (sortOption) {
        SortOption.FolderNameZA -> songs.sortedWith(
            compareByDescending<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        else -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
    }
}

private fun MusicFolder.collectAllSongs(): List<Song> {
    return songs + subFolders.flatMap { it.collectAllSongs() }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelPlayerDarkTheme.current

    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 20.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.985f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = cardShape
                )
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(selectionScale)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box {
                Column(
                    modifier = Modifier.background(
                        color = gradientBaseColor,
                        shape = cardShape
                    )
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            targetSize = Size(256, 256),
                            modifier = Modifier
                                .aspectRatio(3f / 2f)
                                .fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(
                                modifier = Modifier
                                    .aspectRatio(3f / 2f)
                                    .fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(3f / 2f)
                                .background(
                                    remember(gradientBaseColor) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent, gradientBaseColor
                                            )
                                        )
                                    })
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onGradientColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSongCount(album.songCount), style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "✓",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ArtistListItem(
    artist: Artist,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val cardShape = RoundedCornerShape(18.dp)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.99f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "artistSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "artistSelectionBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .combinedClickable(
                enabled = !isLoading,
                onClick = {
                    if (isSelectionMode) onSelectionToggle() else onClick()
                },
                onLongClick = onLongPress
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        top = 12.dp,
                        end = if (isSelected) 54.dp else 12.dp,
                        bottom = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ShapeCache.expressiveAvatar)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!artist.effectiveImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artist.effectiveImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.rounded_artist_24),
                                contentDescription = stringResource(R.string.presentation_batch_d_cd_artist),
                                modifier = Modifier.padding(8.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatSongCount(artist.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumListItem(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelPlayerDarkTheme.current
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 16.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.99f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .scale(selectionScale)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                    ) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            targetSize = Size(256, 256),
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(modifier = Modifier.fillMaxSize())
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            gradientBaseColor
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(gradientBaseColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            val variableTextStyle = remember(album.id, album.title) {
                                GenreTypography.getGenreStyle(album.id.toString(), album.title)
                            }

                            Text(
                                album.title,
                                style = variableTextStyle.copy(fontSize = 22.sp),
                                color = onGradientColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatSongCount(album.songCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
