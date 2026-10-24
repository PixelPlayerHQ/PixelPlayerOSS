package com.lostf1sh.pixelplayeross.presentation.screens

import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.lostf1sh.pixelplayeross.ui.theme.ShapeCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SearchFilterType
import com.lostf1sh.pixelplayeross.data.model.SearchHistoryItem
import com.lostf1sh.pixelplayeross.data.model.SearchResultItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.SmartImageListTargetSize
import com.lostf1sh.pixelplayeross.presentation.components.SongInfoBottomSheet
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.sharedPlayerViewModel
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.diagnostics.AdvancedPerformanceDiagnostics
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistCover
import com.lostf1sh.pixelplayeross.presentation.components.resolveMainScreenBottomGradientHeight
import com.lostf1sh.pixelplayeross.presentation.components.resolveNavBarOccupiedHeight
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.screens.search.components.GenreCategoriesGrid
import com.lostf1sh.pixelplayeross.presentation.selection.dragToSelect
import com.lostf1sh.pixelplayeross.presentation.selection.rememberDragToSelectState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.lostf1sh.pixelplayeross.data.model.Genre
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.presentation.components.AlbumMultiSelectionOptionSheet
import com.lostf1sh.pixelplayeross.presentation.components.EditMultipleSongsSheet
import com.lostf1sh.pixelplayeross.presentation.components.GenreMultiSelectionOptionSheet
import com.lostf1sh.pixelplayeross.presentation.components.MultiSelectionBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.PlayerSheetAwareBackHandler
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistMultiSelectionBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.DeletePlaylistsConfirmationDialog
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionActionRow
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SelectionCountPill
import com.lostf1sh.pixelplayeross.presentation.viewmodel.CloudDownloadsViewModel
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf()
)

private const val MAX_ALBUM_MULTI_SELECTION = 6

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = sharedPlayerViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {},
    cloudDownloadsViewModel: CloudDownloadsViewModel = hiltViewModel()
) {
    val searchScreenEnteredAt = remember { android.os.SystemClock.elapsedRealtime() }
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Song selection is shared with the library through the player view model.
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSongSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var showBatchEditSheet by remember { mutableStateOf(false) }

    var selectedAlbums by remember { mutableStateOf<ImmutableList<Album>>(persistentListOf()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.mapTo(HashSet()) { it.id } }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by remember { mutableStateOf(false) }

    val playlistSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }
    var showDeletePlaylistConfirmation by remember { mutableStateOf(false) }
    var pendingDeletePlaylistIds by remember { mutableStateOf(emptyList<String>()) }

    var selectedGenres by remember { mutableStateOf<ImmutableList<Genre>>(persistentListOf()) }
    val selectedGenreIds = remember(selectedGenres) { selectedGenres.mapTo(HashSet()) { it.id } }
    val isGenreSelectionMode = selectedGenres.isNotEmpty()
    var showGenreMultiSelectionSheet by remember { mutableStateOf(false) }

    val anySelectionMode =
        isSongSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode || isGenreSelectionMode

    // Back clears the selection before leaving Search.
    PlayerSheetAwareBackHandler(
        coordinator = playerViewModel.playerSheetBackCoordinator,
        enabled = anySelectionMode
    ) {
        multiSelectionState.clearSelection()
        playlistSelectionState.clearSelection()
        selectedAlbums = persistentListOf()
        selectedGenres = persistentListOf()
    }

    val showMaxAlbumsToast: () -> Unit = {
        playerViewModel.sendToast(
            context.getString(R.string.presentation_batch_d_max_albums_selection, MAX_ALBUM_MULTI_SELECTION)
        )
    }
    val toggleAlbumSelection: (Album) -> Unit = { album ->
        when {
            album.id in selectedAlbumIds ->
                selectedAlbums = selectedAlbums.filterNot { it.id == album.id }.toImmutableList()
            selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION -> showMaxAlbumsToast()
            else -> selectedAlbums = (selectedAlbums + album).toImmutableList()
        }
    }
    val toggleGenreSelection: (Genre) -> Unit = { genre ->
        selectedGenres = if (genre.id in selectedGenreIds) {
            selectedGenres.filterNot { it.id == genre.id }.toImmutableList()
        } else {
            (selectedGenres + genre).toImmutableList()
        }
    }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
            name = "search_screen_entered",
        )
        onSearchBarActiveChange(false)
        withFrameNanos { }
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
            name = "search_screen_first_frame",
        ) {
            mapOf(
                "durationMs" to
                    (android.os.SystemClock.elapsedRealtime() - searchScreenEnteredAt).toString(),
                "genreCount" to genres.size.toString(),
            )
        }
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // A filter change re-runs the query inside the search holder, so only the query restarts it.
    LaunchedEffect(searchQuery) {
        playerViewModel.performSearch(searchQuery)
    }
    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelPlayerDarkTheme.current

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
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .weight(1f)
                        .background(color = Color.Transparent)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                modifier = Modifier.focusRequester(searchInputFocusRequester),
                                query = searchQuery,
                                onQueryChange = {
                                    searchQuery = it
                                    playerViewModel.updateSearchQuery(it)
                                },
                                onSearch = { query ->
                                    if (query.isNotBlank()) {
                                        playerViewModel.onSearchQuerySubmitted(query)
                                    }
                                    keyboardController?.hide()
                                },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.cd_search_icon),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                playerViewModel.updateSearchQuery("")
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_clear_search_query),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(searchbarCornerRadius)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                FilledIconButton(
                    modifier = Modifier.padding(bottom = 2.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.Settings.route) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd)
                    )
                }
            }

            val showGenreBrowse by remember(searchQuery) { derivedStateOf { searchQuery.isBlank() } }
            AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isGenreSelectionMode) {
                            SelectionActionRow(
                                selectedCount = selectedGenres.size,
                                onSelectAll = { selectedGenres = genres },
                                onDeselect = { selectedGenres = persistentListOf() },
                                onOptionsClick = { showGenreMultiSelectionSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            GenreCategoriesGrid(
                                genres = genres,
                                onGenreClick = { genre ->
                                    Timber.tag("SearchScreen")
                                        .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                                    val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenreId))
                                },
                                playerViewModel = playerViewModel,
                                modifier = Modifier.padding(top = 12.dp),
                                isSelectionMode = isGenreSelectionMode,
                                selectedGenreIds = selectedGenreIds,
                                onGenreLongPress = { genre ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleGenreSelection(genre)
                                },
                                onGenreSelectionToggle = toggleGenreSelection,
                                getSelectionIndex = { genreId ->
                                    val idx = selectedGenres.indexOfFirst { it.id == genreId }
                                    if (idx >= 0) idx + 1 else null
                                }
                            )

                            SelectionCountPill(
                                selectedCount = selectedGenres.size,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        val resultSelectionCount = when {
                            isSongSelectionMode -> selectedSongs.size
                            isPlaylistSelectionMode -> selectedPlaylists.size
                            isAlbumSelectionMode -> selectedAlbums.size
                            else -> 0
                        }
                        SearchResultsHeader(
                            showSelectionActions = resultSelectionCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                            filterChips = {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                                }
                            }
                        ) {
                            SelectionActionRow(
                                selectedCount = resultSelectionCount,
                                onSelectAll = {
                                    when {
                                        isSongSelectionMode -> multiSelectionState.selectAll(
                                            searchResults.filterIsInstance<SearchResultItem.SongItem>().map { it.song }
                                        )
                                        isPlaylistSelectionMode -> playlistSelectionState.selectAll(
                                            searchResults.filterIsInstance<SearchResultItem.PlaylistItem>().map { it.playlist }
                                        )
                                        isAlbumSelectionMode -> {
                                            val remaining = MAX_ALBUM_MULTI_SELECTION - selectedAlbums.size
                                            if (remaining <= 0) {
                                                showMaxAlbumsToast()
                                            } else {
                                                val toAdd = searchResults
                                                    .filterIsInstance<SearchResultItem.AlbumItem>()
                                                    .map { it.album }
                                                    .filterNot { it.id in selectedAlbumIds }
                                                    .take(remaining)
                                                selectedAlbums = (selectedAlbums + toAdd).toImmutableList()
                                            }
                                        }
                                    }
                                },
                                onDeselect = {
                                    multiSelectionState.clearSelection()
                                    playlistSelectionState.clearSelection()
                                    selectedAlbums = persistentListOf()
                                },
                                onOptionsClick = {
                                    when {
                                        isSongSelectionMode -> showMultiSelectionSheet = true
                                        isPlaylistSelectionMode -> showPlaylistMultiSelectionSheet = true
                                        isAlbumSelectionMode -> showAlbumMultiSelectionSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = searchResults.isEmpty(),
                                animationSpec = tween(durationMillis = 190),
                                label = "search_results_fade"
                            ) { isEmpty ->
                                if (isEmpty) {
                                    EmptySearchResults(
                                        searchQuery = searchQuery,
                                        colorScheme = colorScheme
                                    )
                                } else {
                                    SearchResultsList(
                                        results = searchResults,
                                        searchQuery = searchQuery,
                                        selectedFilter = currentFilter,
                                        playerViewModel = playerViewModel,
                                        onItemSelected = {
                                            if (searchQuery.isNotBlank()) {
                                                playerViewModel.onSearchQuerySubmitted(searchQuery)
                                            }
                                        },
                                        currentPlayingSongId = stablePlayerState.currentSong?.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                        navController = navController,
                                        isSongSelectionMode = isSongSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        getSongSelectionIndex = multiSelectionState::getSelectionIndex,
                                        onSongLongPress = { song ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            multiSelectionState.toggleSelection(song)
                                        },
                                        currentSelectedSongIds = multiSelectionState.selectedSongIds::value,
                                        onSongDragSelectionChange = multiSelectionState::applyDragSelection,
                                        bottomBarHeight = bottomBarHeightDp,
                                        isAlbumSelectionMode = isAlbumSelectionMode,
                                        selectedAlbumIds = selectedAlbumIds,
                                        getAlbumSelectionIndex = { albumId ->
                                            val idx = selectedAlbums.indexOfFirst { it.id == albumId }
                                            if (idx >= 0) idx + 1 else null
                                        },
                                        onAlbumLongPress = { album ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            toggleAlbumSelection(album)
                                        },
                                        onAlbumSelectionToggle = toggleAlbumSelection,
                                        isPlaylistSelectionMode = isPlaylistSelectionMode,
                                        selectedPlaylistIds = selectedPlaylistIds,
                                        getPlaylistSelectionIndex = playlistSelectionState::getSelectionIndex,
                                        onPlaylistLongPress = { playlist ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playlistSelectionState.toggleSelection(playlist)
                                        },
                                        onPlaylistSelectionToggle = playlistSelectionState::toggleSelection
                                    )
                                }
                            }

                            SelectionCountPill(
                                selectedCount = resultSelectionCount,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
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
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
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
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
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
            )
        }
    }

    if (showPlaylistBottomSheet && playlistSheetSongs.isNotEmpty()) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = {
                showPlaylistBottomSheet = false
                playlistSheetSongs = persistentListOf()
            },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteSongIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
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
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDownloadAll = {
                val cloudSongCount = CloudOfflineRepository.downloadCandidates(selectedSongs).size
                cloudDownloadsViewModel.downloadSelected(selectedSongs)
                multiSelectionState.clearSelection()
                playerViewModel.sendToast(
                    context.resources.getQuantityString(
                        R.plurals.cloud_download_selected_started,
                        cloudSongCount,
                        cloudSongCount
                    )
                )
                showMultiSelectionSheet = false
            },
            onDeleteAll = { deleteActivity, onComplete ->
                playerViewModel.deleteSelectedFromDevice(deleteActivity, selectedSongs) {
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
            }
        )
    }

    if (showAlbumMultiSelectionSheet && selectedAlbums.isNotEmpty()) {
        AlbumMultiSelectionOptionSheet(
            selectedAlbums = selectedAlbums,
            maxSelection = MAX_ALBUM_MULTI_SELECTION,
            onDismiss = { showAlbumMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedAlbums(selectedAlbums)
                selectedAlbums = persistentListOf()
                showAlbumMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAlbumsAsNext(selectedAlbums)
                selectedAlbums = persistentListOf()
                showAlbumMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedAlbumsToQueue(selectedAlbums)
                selectedAlbums = persistentListOf()
                showAlbumMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                val albums = selectedAlbums
                selectedAlbums = persistentListOf()
                showAlbumMultiSelectionSheet = false
                scope.launch {
                    val songs = playerViewModel.getSongsForAlbums(albums)
                    if (songs.isNotEmpty()) {
                        playlistSheetSongs = songs.toImmutableList()
                        showPlaylistBottomSheet = true
                    } else {
                        playerViewModel.sendToast(context.getString(R.string.player_no_playable_songs_in_albums))
                    }
                }
            }
        )
    }

    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = { showPlaylistMultiSelectionSheet = false },
            onDeleteAll = {
                // Keep the selection until the deletion is confirmed, so Cancel leaves it as it was.
                pendingDeletePlaylistIds = selectedPlaylistIds.toList()
                showPlaylistMultiSelectionSheet = false
                showDeletePlaylistConfirmation = true
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onMergeAll = {
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            }
        )
    }

    if (showDeletePlaylistConfirmation && pendingDeletePlaylistIds.isNotEmpty()) {
        DeletePlaylistsConfirmationDialog(
            count = pendingDeletePlaylistIds.size,
            onConfirm = {
                playlistViewModel.deletePlaylistsInBatch(pendingDeletePlaylistIds)
                playlistSelectionState.clearSelection()
                showDeletePlaylistConfirmation = false
                pendingDeletePlaylistIds = emptyList()
            },
            onDismiss = {
                showDeletePlaylistConfirmation = false
                pendingDeletePlaylistIds = emptyList()
            }
        )
    }

    if (showGenreMultiSelectionSheet && selectedGenres.isNotEmpty()) {
        GenreMultiSelectionOptionSheet(
            selectedGenres = selectedGenres,
            onDismiss = { showGenreMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedGenres(selectedGenres)
                selectedGenres = persistentListOf()
                showGenreMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedGenresAsNext(selectedGenres)
                selectedGenres = persistentListOf()
                showGenreMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedGenresToQueue(selectedGenres)
                selectedGenres = persistentListOf()
                showGenreMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                val genresToResolve = selectedGenres
                selectedGenres = persistentListOf()
                showGenreMultiSelectionSheet = false
                scope.launch {
                    val songs = playerViewModel.getSongsForGenres(genresToResolve)
                    if (songs.isNotEmpty()) {
                        playlistSheetSongs = songs.toImmutableList()
                        showPlaylistBottomSheet = true
                    } else {
                        playerViewModel.sendToast(context.getString(R.string.player_no_playable_songs_in_genres))
                    }
                }
            }
        )
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: ImmutableList<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_no_search_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: ImmutableList<SearchResultItem>,
    searchQuery: String,
    selectedFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController,
    isSongSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    getSongSelectionIndex: (String) -> Int? = { null },
    onSongLongPress: (Song) -> Unit = {},
    currentSelectedSongIds: () -> Set<String> = { emptySet() },
    onSongDragSelectionChange: (select: List<Song>, deselect: List<Song>) -> Unit = { _, _ -> },
    bottomBarHeight: Dp = 0.dp,
    isAlbumSelectionMode: Boolean = false,
    selectedAlbumIds: Set<Long> = emptySet(),
    getAlbumSelectionIndex: (Long) -> Int? = { null },
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectionToggle: (Album) -> Unit = {},
    isPlaylistSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    getPlaylistSelectionIndex: (String) -> Int? = { null },
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    // Another filter, or results with another set of sections, get a fresh scroll state and open
    // at the top. The old state would keep the previous first visible item in place: going from
    // Albums back to All opened at the Albums header, songs hidden above it. The filter is keyed
    // on its own because both filters can return the same sections, such as Songs and All for a
    // query that only matches song titles. The sections stay keyed too: the filter changes before
    // the query re-run with it delivers its results, and those can add sections above the old ones.
    val sectionTypes = remember(groupedResults) { groupedResults.keys }
    val listState = rememberSaveable(selectedFilter, sectionTypes, saver = LazyListState.Saver) {
        LazyListState()
    }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
            onItemSelected()
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    val dragSelectState = rememberDragToSelectState<Song>()
    // Section headers, albums, artists and playlists share the list with the songs but are not
    // selectable, so each list index maps to its song, if any.
    val dragSelectRows = remember(groupedResults) {
        buildList<Song?> {
            sectionOrder.forEach { filterType ->
                val itemsForSection = groupedResults[filterType].orEmpty()
                if (itemsForSection.isNotEmpty()) {
                    add(null)
                    itemsForSection.forEach { add((it as? SearchResultItem.SongItem)?.song) }
                }
            }
        }
    }
    val dragSelectSongAt: (Int) -> Song? = remember(dragSelectRows) {
        { index -> dragSelectRows.getOrNull(index) }
    }
    val hasCurrentSong = playerStableState.currentSong.let { it != null && it != Song.emptySong() }
    // The keyboard, when open, covers the navigation bar and the mini player.
    val dragSelectBottomInset = maxOf(
        with(localDensity) { WindowInsets.ime.getBottom(this).toDp() },
        if (hasCurrentSong) bottomBarHeight + MiniPlayerHeight else bottomBarHeight
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                )
            )
            .dragToSelect(
                state = dragSelectState,
                listState = listState,
                itemAt = dragSelectSongAt,
                itemKey = Song::id,
                selectedKeys = currentSelectedSongIds,
                onSelectionChange = onSongDragSelectionChange,
                bottomInset = dragSelectBottomInset
            ),
        state = listState,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(title = stringResource(searchFilterLabelRes(filterType)))
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) },
                                    isSelected = item.song.id in selectedSongIds,
                                    selectionIndex = getSongSelectionIndex(item.song.id),
                                    isSelectionMode = isSongSelectionMode,
                                    onLongPress = {
                                        onSongLongPress(item.song)
                                        dragSelectState.onItemLongPressed(item.song)
                                    }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick,
                                    isSelected = item.album.id in selectedAlbumIds,
                                    selectionIndex = getAlbumSelectionIndex(item.album.id),
                                    isSelectionMode = isAlbumSelectionMode,
                                    onLongPress = { onAlbumLongPress(item.album) },
                                    onSelectionToggle = { onAlbumSelectionToggle(item.album) }
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRoute(item.artist.id),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                        .map { it.toImmutableList() }
                                }.collectAsStateWithLifecycle(initialValue = persistentListOf())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick,
                                    isSelected = item.playlist.id in selectedPlaylistIds,
                                    selectionIndex = getPlaylistSelectionIndex(item.playlist.id),
                                    isSelectionMode = isPlaylistSelectionMode,
                                    onLongPress = { onPlaylistLongPress(item.playlist) },
                                    onSelectionToggle = { onPlaylistSelectionToggle(item.playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateSelectionScale(isSelected)
    val selectionBorderWidth by animateSelectionBorderWidth(isSelected)

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSelection(
                shape = itemShape,
                scale = selectionScale,
                borderWidth = selectionBorderWidth,
                borderColor = MaterialTheme.colorScheme.primary,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = onOpenClick,
                onLongPress = onLongPress,
                onSelectionToggle = onSelectionToggle
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = stringResource(R.string.cd_album_art_of, album.title),
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(itemShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album), modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                SearchSelectionBadge(
                    selectionIndex = selectionIndex,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = stringResource(R.string.cd_artist_format, artist.name),
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.expressiveAvatar)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = stringResource(R.string.cd_artist_icon),
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, ShapeCache.expressiveAvatar)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_artist), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: ImmutableList<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateSelectionScale(isSelected)
    val selectionBorderWidth by animateSelectionBorderWidth(isSelected)

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSelection(
                shape = itemShape,
                scale = selectionScale,
                borderWidth = selectionBorderWidth,
                borderColor = MaterialTheme.colorScheme.primary,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = onOpenClick,
                onLongPress = onLongPress,
                onSelectionToggle = onSelectionToggle
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCover(
                    playlist = playlist,
                    playlistSongs = playlistSongs,
                    size = 56.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatSongCount(playlist.songIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_playlist), modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                SearchSelectionBadge(
                    selectionIndex = selectionIndex,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun animateSelectionScale(isSelected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (isSelected) 0.98f else 1f,
    animationSpec = tween(durationMillis = 200),
    label = "searchSelectionScale"
)

@Composable
private fun animateSelectionBorderWidth(isSelected: Boolean): State<Dp> = animateDpAsState(
    targetValue = if (isSelected) 2.dp else 0.dp,
    animationSpec = tween(durationMillis = 200),
    label = "searchSelectionBorder"
)

private fun Modifier.searchResultSelection(
    shape: Shape,
    scale: Float,
    borderWidth: Dp,
    borderColor: Color,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit
): Modifier = this
    .scale(scale)
    .then(if (isSelected) Modifier.border(borderWidth, borderColor, shape) else Modifier)
    .clip(shape)
    .combinedClickable(
        onClick = if (isSelectionMode) onSelectionToggle else onClick,
        onLongClick = onLongPress
    )

@Composable
private fun SearchSelectionBadge(
    selectionIndex: Int?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    ToggleButton(
        checked = selected,
        onCheckedChange = { playerViewModel.updateSearchFilter(filterType) },
        modifier = modifier,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.rounded_check_circle_24),
                contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(searchFilterLabelRes(filterType)))
    }
}

/**
 * The filter chips above the search results, or the selection actions in their place while
 * results are selected.
 *
 * The header keeps the height of the taller of the two either way. The chips can wrap to two
 * rows while the actions take one, and a header that shrank as a long press started a selection
 * would pull the results up under the finger, so the drag that follows the long press would
 * start on the row below. Both stay composed so both can be measured; only the one shown is
 * placed, so the other is neither drawn nor reachable by touch, focus or accessibility.
 */
@Composable
internal fun SearchResultsHeader(
    showSelectionActions: Boolean,
    filterChips: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectionActions: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(filterChips, selectionActions),
        modifier = modifier,
    ) { (chipMeasurables, actionMeasurables), constraints ->
        val childConstraints = constraints.copy(minHeight = 0)
        val chips = chipMeasurables.map { it.measure(childConstraints) }
        val actions = actionMeasurables.map { it.measure(childConstraints) }
        val all = chips + actions
        val width = (all.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (all.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val shown = if (showSelectionActions) actions else chips
            shown.forEach { it.placeRelative(x = 0, y = (height - it.height) / 2) }
        }
    }
}

private fun searchFilterLabelRes(filterType: SearchFilterType): Int = when (filterType) {
    SearchFilterType.ALL -> R.string.search_filter_all
    SearchFilterType.SONGS -> R.string.library_tab_songs
    SearchFilterType.ALBUMS -> R.string.library_tab_albums
    SearchFilterType.ARTISTS -> R.string.library_tab_artists
    SearchFilterType.PLAYLISTS -> R.string.library_tab_playlists
}
