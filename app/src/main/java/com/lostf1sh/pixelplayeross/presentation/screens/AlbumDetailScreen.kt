@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafelyReplacing
import com.lostf1sh.pixelplayeross.presentation.components.DetailHeroArtistChip
import com.lostf1sh.pixelplayeross.presentation.components.DetailHeroFloatingLayer
import com.lostf1sh.pixelplayeross.presentation.components.DetailHeroPinnedActionsHeight
import com.lostf1sh.pixelplayeross.presentation.components.DetailSongsSummary
import com.lostf1sh.pixelplayeross.presentation.components.HeroPlayState
import com.lostf1sh.pixelplayeross.presentation.components.MediaDetailHero
import com.lostf1sh.pixelplayeross.presentation.components.rememberDetailHeroDock
import com.lostf1sh.pixelplayeross.presentation.components.rememberSongCollectionMenuActions
import com.lostf1sh.pixelplayeross.presentation.screens.search.components.GenreTypography
import com.lostf1sh.pixelplayeross.presentation.utils.getSegmentedItemShape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.ui.theme.LocalShowScrollbar
import com.lostf1sh.pixelplayeross.ui.theme.PixelPlayerStatusBarStyle
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.SongInfoBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.resolveNavBarOccupiedHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.viewmodel.AlbumDetailViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.utils.GenreParser
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val UseSharedCollapsibleTopBarProbe = true

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isMiniPlayerVisible by remember {
        derivedStateOf { stablePlayerState.currentSong != null }
    }
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val completedOfflineUris by viewModel.completedOfflineUris.collectAsStateWithLifecycle()
    val allArtists by playerViewModel.artistsFlow.collectAsStateWithLifecycle()
    var showAlbumPlaylistSheet by remember { mutableStateOf(false) }

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val isDarkTheme = LocalPixelPlayerDarkTheme.current
    val baseColorScheme = MaterialTheme.colorScheme
    val albumArtUri = uiState.album?.albumArtUriString?.takeIf { it.isNotBlank() }
    val albumColorSchemeFlow = remember(albumArtUri) {
        albumArtUri?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val albumColorSchemePair = albumColorSchemeFlow?.collectAsStateWithLifecycle()?.value
    val albumColorScheme = remember(albumColorSchemePair, isDarkTheme, baseColorScheme) {
        albumColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
            ?: baseColorScheme
    }
    var headerArtworkLoaded by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    var themeRequestIssued by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    LaunchedEffect(albumArtUri, headerArtworkLoaded, themeRequestIssued) {
        if (!themeRequestIssued && headerArtworkLoaded && albumArtUri != null) {
            themeRequestIssued = true
            playerViewModel.themeStateHolder.ensureAlbumColorScheme(albumArtUri)
        }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = albumColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {

        val fabBottomPadding by animateDpAsState(
            targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 16.dp,
            label = "fabPadding"
        )

        when {
            uiState.isLoading && uiState.album == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }

            uiState.error != null && uiState.album == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        FilledTonalButton(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.library_retry))
                        }
                    }
                }
            }

            uiState.album != null -> {
                val album = uiState.album!!
                val songs = uiState.songs
                val immutableSongs = remember(songs) { songs.toImmutableList() }
                val cloudSongs = remember(songs) {
                    CloudOfflineRepository.downloadCandidates(songs)
                }
                val allCloudSongsDownloaded = remember(cloudSongs, completedOfflineUris) {
                    cloudSongs.isNotEmpty() && cloudSongs.all { it.contentUriString in completedOfflineUris }
                }
                val songsByDisc = remember(songs) {
                    songs.groupBy { it.discNumber ?: 1 }
                }
                val lazyListState = rememberLazyListState()
                val albumArtist = remember(songs, allArtists) {
                    val artistId = songs.firstOrNull()?.artistId
                    allArtists.firstOrNull { it.id == artistId }
                }
                // The genre most tracks share, split so "House; Electronic" counts as both.
                val albumGenre = remember(songs) {
                    GenreParser.dominantGenre(songs.map { it.genre })
                }
                val albumDurationMs = remember(songs) { songs.sumOf { it.duration } }
                val songIdsInScope = remember(songs) { songs.mapTo(HashSet()) { it.id } }
                val currentSongId = stablePlayerState.currentSong?.id
                val heroPlayState = when {
                    currentSongId == null || currentSongId !in songIdsInScope -> HeroPlayState.Idle
                    stablePlayerState.isPlaying -> HeroPlayState.Playing
                    else -> HeroPlayState.Paused
                }
                // The bar only needs a background once content actually slides under it.
                val barScrimFadePx = with(density) { 24.dp.toPx() }
                val barContainerAlpha by remember(barScrimFadePx) {
                    derivedStateOf {
                        if (lazyListState.firstVisibleItemIndex > 0) 1f
                        else (lazyListState.firstVisibleItemScrollOffset / barScrimFadePx).coerceIn(0f, 1f)
                    }
                }
                val onHeroShuffle: () -> Unit = {
                    if (songs.isNotEmpty()) {
                        playerViewModel.playSongsShuffled(songs, album.title, startAtZero = true)
                    }
                }
                val onHeroPlay: () -> Unit = {
                    if (heroPlayState == HeroPlayState.Idle) {
                        songs.firstOrNull()?.let { first -> playerViewModel.showAndPlaySong(first, songs) }
                    } else {
                        playerViewModel.playPause()
                    }
                }
                val albumMenuActions = rememberSongCollectionMenuActions(
                    onAddToPlaylist = { showAlbumPlaylistSheet = true },
                    onPlayNext = { if (songs.isNotEmpty()) playerViewModel.addSelectedAsNext(songs) },
                    onAddToQueue = { if (songs.isNotEmpty()) playerViewModel.addSelectedToQueue(songs) }
                )

                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val minTopBarHeight = 64.dp + statusBarHeight
                val maxTopBarHeight = 300.dp

                val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
                val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
                val headerImageRequestSize = remember(
                    configuration.screenWidthDp,
                    density.density,
                    maxTopBarHeightPx
                ) {
                    Size(
                        width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                        height = maxTopBarHeightPx.roundToInt()
                    )
                }

                val topBarHeight = remember { Animatable(maxTopBarHeightPx) }

                // Hiding the system bars drops the status-bar inset to zero, which shrinks
                // minTopBarHeightPx. Pull the animated height back into range when the inset
                // returns, otherwise the collapsed bar stays too short.
                LaunchedEffect(minTopBarHeightPx, maxTopBarHeightPx) {
                    val clamped = topBarHeight.value.coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                    if (clamped != topBarHeight.value) topBarHeight.snapTo(clamped)
                }
                val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
                    derivedStateOf {
                        1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(
                            0f,
                            1f
                        )
                    }
                }

                val heroOverlapPx = with(density) { HeroHeaderOverlap.toPx() }
                val listTopPadding = minTopBarHeight + 8.dp
                val listTopPaddingPx = with(density) { listTopPadding.toPx() }
                // The list rides down with the header, overlapping its artwork by the hero overlap.
                val listOffsetPx: () -> Int = {
                    (topBarHeight.value - minTopBarHeightPx - heroOverlapPx * (1f - collapseFraction))
                        .roundToInt()
                        .coerceAtLeast(0)
                }
                // Same per-album variable-font title as the album cards in the Library.
                val heroTitleStyle = remember(album.id, album.title) {
                    GenreTypography.getGenreStyle(album.id.toString(), album.title)
                }
                val heroMetaLine = listOfNotNull(
                    albumGenre,
                    album.year.takeIf { it > 0 }?.toString()
                ).joinToString(" · ").ifBlank { null }
                // Drawn by the floating layer, with a same-sized placeholder in the list.
                val heroChip: @Composable () -> Unit = {
                    val artistName = album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist
                    DetailHeroArtistChip(
                        name = artistName,
                        imageUrl = albumArtist?.effectiveImageUrl,
                        onClick = albumArtist?.let { artist ->
                            {
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRoute(artist.id),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                            }
                        }
                    )
                }
                val showDownloadAction = cloudSongs.isNotEmpty()
                val heroDock = rememberDetailHeroDock(
                    heroTopPx = {
                        if (lazyListState.firstVisibleItemIndex == 0) {
                            listOffsetPx() + listTopPaddingPx - lazyListState.firstVisibleItemScrollOffset
                        } else {
                            null
                        }
                    },
                    // Docking starts once the header is collapsed and the list itself scrolls, so a
                    // list too short to scroll never leaves the title half-way.
                    dockStartHeroTopPx = listTopPaddingPx,
                    heroStartPadding = 16.dp,
                    collapsedBarBottom = minTopBarHeight,
                    hasBarSubtitle = true,
                    // The bar keeps the download action on the right when the album has cloud songs.
                    collapsedTitleEndPadding = if (showDownloadAction) 88.dp else 24.dp,
                    titleStyle = heroTitleStyle
                )

                // Direction of the latest scroll delta. A gesture that ends while heading back to
                // the top (a fling landing there included) must leave the header fully expanded.
                var lastScrollTowardsTop by remember { mutableStateOf(true) }
                val nestedScrollConnection = remember(minTopBarHeightPx, maxTopBarHeightPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            val delta = available.y
                            val isScrollingDown = delta < 0
                            if (delta != 0f) lastScrollTowardsTop = !isScrollingDown

                            if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                                return Offset.Zero
                            }

                            val previousHeight = topBarHeight.value
                            val newHeight =
                                (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                            val consumed = newHeight - previousHeight

                            if (consumed.roundToInt() != 0) {
                                coroutineScope.launch {
                                    topBarHeight.snapTo(newHeight)
                                }
                            }

                            val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                            return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            return super.onPostFling(consumed, available)
                        }
                    }
                }

                LaunchedEffect(lazyListState.isScrollInProgress) {
                    if (!lazyListState.isScrollInProgress) {
                        val canExpand =
                            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
                        val shouldExpand = lastScrollTowardsTop ||
                            topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2

                        val targetValue = if (shouldExpand && canExpand) {
                            maxTopBarHeightPx
                        } else {
                            minTopBarHeightPx
                        }

                        if (topBarHeight.value != targetValue) {
                            coroutineScope.launch {
                                topBarHeight.animateTo(
                                    targetValue,
                                    spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface
                        )
                        .nestedScroll(nestedScrollConnection)
                ) {
                    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
                    val showScrollBar =
                        LocalShowScrollbar.current &&
                        collapseFraction > 0.95f &&
                            (lazyListState.canScrollForward || lazyListState.canScrollBackward)
                    val scrollbarInset by animateDpAsState(
                        targetValue = if (showScrollBar) 8.dp else 0.dp,
                        label = "albumScrollbarInset"
                    )
                    val onDownloadClick: () -> Unit = {
                        if (allCloudSongsDownloaded) viewModel.removeAlbumDownloads(cloudSongs)
                        else viewModel.downloadAlbum(cloudSongs)
                    }

                    // Artwork + gradient sit behind the list so the hero can ride up over the
                    // lower part of the image; only the bar (back button, collapsed title,
                    // download action) is drawn above the list.
                    if (UseSharedCollapsibleTopBarProbe) {
                        AlbumHeaderBackdrop(
                            album = album,
                            collapseFraction = collapseFraction,
                            headerHeight = currentTopBarHeightDp,
                            headerImageRequestSize = headerImageRequestSize,
                            onHeaderArtworkState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    headerArtworkLoaded = true
                                }
                            }
                        )
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, listOffsetPx()) },
                        contentPadding = PaddingValues(
                            top = listTopPadding,
                            start = 16.dp,
                            // Fixed, so the hero never shifts; only song rows make room for
                            // the scrollbar (scrollbarInset).
                            end = 16.dp,
                            bottom = fabBottomPadding + 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(key = "album_hero", contentType = "album_hero") {
                            MediaDetailHero(
                                title = album.title,
                                metaLine = heroMetaLine,
                                chip = heroChip,
                                onShuffle = onHeroShuffle,
                                onPlay = onHeroPlay,
                                playState = heroPlayState,
                                menuActions = albumMenuActions,
                                enabled = songs.isNotEmpty(),
                                dock = heroDock,
                                titleStyle = heroTitleStyle
                            )
                        }
                        if (songs.isNotEmpty()) {
                            item(key = "album_songs_summary", contentType = "detail_songs_summary") {
                                DetailSongsSummary(
                                    songCount = songs.size,
                                    durationMs = albumDurationMs,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                                )
                            }
                        }
                        songsByDisc.forEach { (discNumber, discSongs) ->
                            if (songsByDisc.size > 1) {
                                item(key = "disc_header_$discNumber") {
                                    Text(
                                        text = stringResource(R.string.disc_number_header, discNumber),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                                    )
                                }
                            }
                            itemsIndexed(
                                items = discSongs,
                                key = { _, song -> "album_song_${song.id}" },
                                contentType = { _, _ -> "album_song" }
                            ) { index, song ->
                                LibraryPlaybackAwareSongItem(
                                    modifier = Modifier.padding(end = scrollbarInset),
                                    song = song,
                                    playerViewModel = playerViewModel,
                                    customShape = getSegmentedItemShape(index, discSongs.size),
                                    showAlbumArt = false,
                                    onMoreOptionsClick = {
                                        playerViewModel.selectSongForInfo(song)
                                        showSongInfoBottomSheet = true
                                    },
                                    onClick = { playerViewModel.showAndPlaySong(song, songs) }
                                )
                            }
                        }
                    }

                    if (showScrollBar) {
                        ExpressiveScrollBar(
                            listState = lazyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    top = minTopBarHeight + DetailHeroPinnedActionsHeight + 4.dp,
                                    bottom = fabBottomPadding + 80.dp
                                )
                        )
                    }

                    if (UseSharedCollapsibleTopBarProbe) {
                        SharedAlbumTopBarProbe(
                            album = album,
                            songsCount = songs.size,
                            collapseFraction = collapseFraction,
                            titleAlpha = heroDock.barTitleAlpha,
                            subtitleAlpha = heroDock.barSubtitleAlpha,
                            containerAlpha = maxOf(barContainerAlpha, heroDock.barContainerAlpha),
                            headerHeight = currentTopBarHeightDp,
                            onBackPressed = { navController.popBackStack() },
                            showDownloadAction = showDownloadAction,
                            allDownloaded = allCloudSongsDownloaded,
                            onDownloadClick = onDownloadClick
                        )
                    } else {
                        CollapsingAlbumTopBar(
                            album = album,
                            songsCount = songs.size,
                            collapseFraction = collapseFraction,
                            headerHeight = currentTopBarHeightDp,
                            headerImageRequestSize = headerImageRequestSize,
                            onHeaderArtworkState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    headerArtworkLoaded = true
                                }
                            },
                            onBackPressed = { navController.popBackStack() },
                            showDownloadAction = showDownloadAction,
                            allDownloaded = allCloudSongsDownloaded,
                            onDownloadClick = onDownloadClick,
                            onPlayClick = {
                                if (songs.isNotEmpty()) {
                                    val randomSong = songs.random()
                                    playerViewModel.showAndPlaySong(randomSong, songs)
                                }
                            }
                        )
                    }

                    DetailHeroFloatingLayer(
                        title = album.title,
                        dock = heroDock,
                        listState = lazyListState,
                        onShuffle = onHeroShuffle,
                        onPlay = onHeroPlay,
                        menuActions = albumMenuActions,
                        playState = heroPlayState,
                        enabled = songs.isNotEmpty(),
                        titleStyle = heroTitleStyle,
                        metaLine = heroMetaLine,
                        chip = heroChip
                    )
                }

                if (showAlbumPlaylistSheet && immutableSongs.isNotEmpty()) {
                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = immutableSongs,
                        onDismiss = { showAlbumPlaylistSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo
            val isFavorite = remember(currentSong?.id, favoriteIds) {
                derivedStateOf { currentSong?.let { favoriteIds.contains(it.id) } }
            }.value ?: false

            if (currentSong != null) {
                val removeFromListTrigger = remember(uiState.songs) {
                    {
                        viewModel.update(uiState.songs.filterNot { it.id == currentSong.id })
                    }
                }
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
                    },
                    onAddNextToQueue = {
                        playerViewModel.addSongNextToQueue(currentSong)
                        showSongInfoBottomSheet = false
                    },
                    onAddToPlayList = {
                        showPlaylistBottomSheet = true;
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
                    removeFromListTrigger = removeFromListTrigger
                )
                if (showPlaylistBottomSheet) {
                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = persistentListOf(currentSong),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeaderBackdrop(
    album: Album,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val expandedContentAlpha = 1f - (collapseFraction * 2f).coerceIn(0f, 1f)
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    if (expandedContentAlpha <= 0.01f) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        SmartImage(
            model = album.albumArtUriString,
            contentDescription = stringResource(R.string.album_cover_for, album.title),
            contentScale = ContentScale.Crop,
            targetSize = headerImageRequestSize,
            allowHardware = true,
            crossfadeDurationMillis = 0,
            alpha = expandedContentAlpha,
            onState = onHeaderArtworkState,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(headerOverlayBrush)
        )
    }
}

/** How far the hero rides up over the bottom of the expanded artwork header. */
private val HeroHeaderOverlap = 120.dp

@Composable
private fun SharedAlbumTopBarProbe(
    album: Album,
    songsCount: Int,
    collapseFraction: Float,
    titleAlpha: Float,
    subtitleAlpha: Float,
    containerAlpha: Float,
    headerHeight: Dp,
    onBackPressed: () -> Unit,
    showDownloadAction: Boolean,
    allDownloaded: Boolean,
    onDownloadClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayerDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    PixelPlayerStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        val titleEndPadding = if (showDownloadAction) 88.dp else 24.dp
        CollapsibleCommonTopBar(
            title = album.title,
            subtitle = "${album.artist} • ${formatSongCount(songsCount)}",
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackPressed,
            // Transparent until something scrolls under the bar; the hero overlaps the
            // header, so a collapse-driven fill would dim it mid-gesture.
            containerColor = surfaceColor.copy(alpha = containerAlpha * solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = titleEndPadding,
            expandedTitleEndPadding = titleEndPadding,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = RoundedSans,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 1,
            // The hero title docks into this slot itself (DetailHeroFloatingLayer); the bar's
            // own title only shows for titles too long to dock, and the subtitle settles in
            // under the docked title.
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = subtitleAlpha),
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = {
                if (showDownloadAction) {
                    Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                        FilledIconButton(
                            onClick = onDownloadClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Icon(
                                imageVector = if (allDownloaded) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                                contentDescription = stringResource(
                                    if (allDownloaded) R.string.cloud_album_remove_downloads else R.string.cloud_album_download
                                )
                            )
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingAlbumTopBar(
    album: Album,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
    onBackPressed: () -> Unit,
    showDownloadAction: Boolean,
    allDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayerDarkTheme.current) Color.Black.copy(alpha = 0.6f) else Color.White.copy(
            alpha = 0.4f
        )

    val fabScale = 1f - collapseFraction
    val backgroundAlpha = collapseFraction
    val headerContentAlpha = 1f - (collapseFraction * 2).coerceAtMost(1f)
    val showExpandedArtwork = headerContentAlpha > 0.01f
    val headerOverlayBrush = remember(surfaceColor, headerContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.30f * headerContentAlpha),
                surfaceColor.copy(alpha = 0.90f * headerContentAlpha),
                surfaceColor.copy(alpha = headerContentAlpha)
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(
            colors = listOf(
                statusBarColor,
                Color.Transparent
            )
        )
    }
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }

    val titleScale = lerp(1f, 0.75f, collapseFraction)
    val titlePaddingStart = lerp(24.dp, 58.dp, collapseFraction)
    val titleMaxLines = if (collapseFraction < 0.5f) 2 else 1
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment =
        BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)
    val yOffsetCorrection = lerp((titleContainerHeight / 2) - 64.dp, 0.dp, collapseFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        PixelPlayerStatusBarStyle(color = fallbackStatusBarColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(surfaceColor.copy(alpha = backgroundAlpha))
        ) {
            if (showExpandedArtwork) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = stringResource(R.string.album_cover_for, album.title),
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = headerContentAlpha,
                    onState = onHeaderArtworkState,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(headerOverlayBrush)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(statusBarBrush)
                    .align(Alignment.TopCenter)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                FilledIconButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 4.dp),
                    onClick = onBackPressed,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
                }

                if (showDownloadAction) {
                    FilledIconButton(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 4.dp),
                        onClick = onDownloadClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Icon(
                            imageVector = if (allDownloaded) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                            contentDescription = stringResource(
                                if (allDownloaded) R.string.cloud_album_remove_downloads else R.string.cloud_album_download
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(animatedTitleAlignment)
                        .height(titleContainerHeight)
                        .fillMaxWidth()
                        .offset(y = yOffsetCorrection)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = titlePaddingStart, end = 120.dp)
                            .graphicsLayer {
                                scaleX = titleScale
                                scaleY = titleScale
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.album_detail_meta_line,
                                album.artist,
                                formatSongCount(songsCount)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LargeExtendedFloatingActionButton(
                    onClick = onPlayClick,
                    shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                            alpha = fabScale
                        }
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.cd_shuffle_play_album))
                }
            }
        }
    }
}
