package com.lostf1sh.pixelplayeross.presentation.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.MostListenedType
import com.lostf1sh.pixelplayeross.presentation.components.BlurEffectCache
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.SongInfoBottomSheet
import kotlinx.collections.immutable.persistentListOf
import com.lostf1sh.pixelplayeross.presentation.components.ProgressiveArtworkBlend
import com.lostf1sh.pixelplayeross.presentation.components.buildMostListenedEntries
import com.lostf1sh.pixelplayeross.presentation.components.rememberArtworkColorScheme
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.PlayingEqIcon
import com.lostf1sh.pixelplayeross.presentation.components.MostListenedEntry
import com.lostf1sh.pixelplayeross.presentation.components.labelRes
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafelyReplacing
import com.lostf1sh.pixelplayeross.presentation.viewmodel.MostListenedViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MostListenedScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: MostListenedViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val songs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val artists by playerViewModel.artistsFlow.collectAsStateWithLifecycle()
    val engagements by viewModel.engagements.collectAsStateWithLifecycle()
    val selectedType by viewModel.mostListenedType.collectAsStateWithLifecycle()
    
    val entries = remember(songs, artists, engagements, selectedType) {
        buildMostListenedEntries(songs, engagements, selectedType, artists)
    }
    val blurEnabled = true
    val songQueue = remember(entries, selectedType) {
        if (selectedType == MostListenedType.SONGS) entries.map { it.representativeSong } else emptyList()
    }

    // Playback state tracking for visual playing indicator & highlights
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    val bottomBarHeightDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val statusBarHeight = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 150.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value, minTopBarHeightPx, maxTopBarHeightPx) {
        collapseFraction = 1f - (
            (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
            ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember(minTopBarHeightPx, maxTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (
                    !isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val contentTopPadding = currentTopBarHeightDp + 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentTopPadding,
                bottom = MiniPlayerHeight + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.most_listened_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val isCurrentSong = selectedType == MostListenedType.SONGS && currentSongId == entry.representativeSong.id
                MostListenedListItem(
                    index = index,
                    entry = entry,
                    isPlaying = isPlaying,
                    isCurrentSong = isCurrentSong,
                    blurEnabled = blurEnabled,
                    playerViewModel = playerViewModel,
                    onMoreOptionsClick = {
                        selectedSongForInfo = entry.representativeSong
                        showSongInfoBottomSheet = true
                    },
                    onClick = {
                        when (selectedType) {
                            MostListenedType.SONGS -> playerViewModel.playSongs(
                                songsToPlay = songQueue,
                                startSong = entry.representativeSong,
                                queueName = "Most Listened",
                            )
                            MostListenedType.ALBUMS -> navController.navigateSafelyReplacing(
                                Screen.AlbumDetail.createRoute(entry.destinationId),
                                Screen.AlbumDetail.route,
                            )
                            MostListenedType.ARTISTS -> navController.navigateSafelyReplacing(
                                Screen.ArtistDetail.createRoute(entry.destinationId),
                                Screen.ArtistDetail.route,
                            )
                        }
                    }
                )
            }
        }

        // Pinned Collapsible TopBar
        val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha))
                .zIndex(5f)
        ) {
            CollapsibleCommonTopBar(
                title = stringResource(R.string.most_listened_title),
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackClick = { navController.popBackStack() },
                containerColor = Color.Transparent,
                syncStatusBarWithContainer = true,
                containerHeightRange = 64.dp to 56.dp
            )
        }

        // Dimming overlay when FAB is expanded
        if (fabMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .zIndex(10f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        fabMenuExpanded = false
                    }
            )
        }

        // Floating Action Button Menu for filters
        val isMiniplayerVisible = currentSongId != null
        val motionScheme = remember { MotionScheme.expressive() }
        val floatingControlsBottomPadding by animateDpAsState(
            targetValue = if (isMiniplayerVisible) MiniPlayerHeight + 16.dp else 16.dp,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "MostListenedFloatingControlsBottomPadding"
        )
        
        val fabClosedContainer = MaterialTheme.colorScheme.primary
        val fabOpenContainer = MaterialTheme.colorScheme.primaryContainer
        val fabClosedContent = MaterialTheme.colorScheme.onPrimary
        val fabOpenContent = MaterialTheme.colorScheme.onPrimaryContainer

        FloatingActionButtonMenu(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(11f)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = floatingControlsBottomPadding),
            expanded = fabMenuExpanded,
            horizontalAlignment = Alignment.End,
            button = {
                ToggleFloatingActionButton(
                    checked = fabMenuExpanded,
                    onCheckedChange = { fabMenuExpanded = it },
                    containerSize = ToggleFloatingActionButtonDefaults.containerSize(80.dp),
                    containerColor = { progress ->
                        androidx.compose.ui.graphics.lerp(fabClosedContainer, fabOpenContainer, progress)
                    },
                    modifier = Modifier.animateFloatingActionButton(
                        visible = true,
                        alignment = Alignment.BottomEnd
                    )
                ) {
                    Icon(
                        imageVector = if (fabMenuExpanded) Icons.Rounded.Close else Icons.Rounded.FilterList,
                        contentDescription = if (fabMenuExpanded) "Close filter menu" else "Open filter menu",
                        tint = androidx.compose.ui.graphics.lerp(fabClosedContent, fabOpenContent, checkedProgress),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        ) {
            MostListenedType.entries.forEach { type ->
                val isSelected = selectedType == type
                FloatingActionButtonMenuItem(
                    onClick = {
                        viewModel.setMostListenedType(type)
                        fabMenuExpanded = false
                    },
                    icon = {
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.25f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "fabMenuItemIconScale$type"
                        )
                        Icon(
                            imageVector = type.localIcon(),
                            contentDescription = null,
                            modifier = Modifier
                                .scale(iconScale)
                                .size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(type.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // Bottom Sheets
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            val context = LocalContext.current
            SongInfoBottomSheet(
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(song)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = false
                },
                onPlaySong = {
                    playerViewModel.playSongs(listOf(song), song, "Most Listened Item")
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(song)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(song)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    song.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        song,
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
                        coverArtUpdate
                    )
                },
                removeFromListTrigger = {}
            )

            if (showPlaylistBottomSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = persistentListOf(song),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LazyItemScope.MostListenedListItem(
    index: Int,
    entry: MostListenedEntry,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    blurEnabled: Boolean,
    playerViewModel: PlayerViewModel,
    onMoreOptionsClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val scheme = rememberArtworkColorScheme(entry.artwork, playerViewModel.themeStateHolder)
    
    val transition = updateTransition(
        targetState = isCurrentSong,
        label = "MostListenedListItemHighlightTransition"
    )
    val highlightProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "highlightProgress"
    ) { highlighted ->
        if (highlighted) 1f else 0f
    }

    val animatedCornerRadius = lerpDp(24.dp, 50.dp, highlightProgress)
    val animatedAlbumCornerRadius = lerpDp(12.dp, 50.dp, highlightProgress)
    
    val surfaceShape = remember(animatedCornerRadius) {
        RoundedCornerShape(animatedCornerRadius)
    }

    val albumShape = remember(animatedAlbumCornerRadius) {
        RoundedCornerShape(animatedAlbumCornerRadius)
    }

    val highlightBorderWidth = lerpDp(0.dp, 2.dp, highlightProgress)
    val highlightBorderColor = scheme.primary.copy(alpha = highlightProgress)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .animateItem(placementSpec = motionScheme.fastSpatialSpec())
            .then(
                if (highlightProgress > 0.01f) {
                    Modifier.border(
                        width = highlightBorderWidth,
                        color = highlightBorderColor,
                        shape = surfaceShape
                    )
                } else {
                    Modifier
                }
            ),
        shape = surfaceShape,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize()) {
            val blendColor = scheme.primaryContainer
            val blendBrush = remember(blendColor) {
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.15f to Color.Transparent,
                    0.45f to blendColor.copy(alpha = 0.90f),
                    0.65f to blendColor,
                    1f to blendColor
                )
            }

            if (entry.artwork != null) {
                ProgressiveArtworkBlend(
                    model = entry.artwork,
                    blendColor = blendColor,
                    blurEnabled = blurEnabled,
                    horizontal = true,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(blendBrush))
            }

            // Foreground content Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank Badge - 3:4 Aspect Ratio vertical pill, wide enough for previous size
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(48.dp)
                        .background(scheme.primary, RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = scheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Artwork Thumbnail (unaffected by hazeEffect because it's drawn in the foreground Row!)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, albumShape)
                ) {
                    SmartImage(
                        model = entry.artwork,
                        contentDescription = entry.title,
                        shape = albumShape,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text details (Title & Subtitle)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Playing EQ Icon
                if (isCurrentSong) {
                    Spacer(modifier = Modifier.width(8.dp))
                    PlayingEqIcon(
                        modifier = Modifier.size(width = 18.dp, height = 16.dp),
                        color = scheme.primary,
                        isPlaying = isPlaying
                    )
                }

                // Options (three dots menu - updated to 3:4 aspect ratio vertical pill)
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onMoreOptionsClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.onPrimaryContainer.copy(alpha = 0.08f),
                        contentColor = scheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .width(30.dp)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.most_listened_cd_more_options, entry.title),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun MostListenedType.localIcon() = when (this) {
    MostListenedType.SONGS -> Icons.Rounded.MusicNote
    MostListenedType.ALBUMS -> Icons.Rounded.Album
    MostListenedType.ARTISTS -> Icons.Rounded.Person
}
