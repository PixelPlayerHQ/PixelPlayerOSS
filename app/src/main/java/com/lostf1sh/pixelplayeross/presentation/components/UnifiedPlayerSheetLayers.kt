package com.lostf1sh.pixelplayeross.presentation.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.FullPlayerLoadingTweaks
import com.lostf1sh.pixelplayeross.presentation.components.player.FullPlayerContent
import com.lostf1sh.pixelplayeross.presentation.components.scoped.FullPlayerVisualState
import com.lostf1sh.pixelplayeross.presentation.components.scoped.miniPlayerLayerOffsetXPx
import com.lostf1sh.pixelplayeross.presentation.components.scoped.miniPlayerLayerWidthPx
import com.lostf1sh.pixelplayeross.presentation.components.scoped.rememberFullPlayerRuntimePolicy
import com.lostf1sh.pixelplayeross.presentation.components.scoped.sheetHorizontalPaddingPx
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    currentSong: Song?,
    miniPlayerScheme: ColorScheme?,
    overallSheetTopCornerRadiusProvider: () -> Dp,
    infrequentPlayerState: StablePlayerState,
    isOutputConnecting: Boolean,
    isPreparingPlayback: Boolean,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    albumColorScheme: ColorScheme,
    bottomSheetOpenFractionProvider: () -> Float,
    isQueueOccludingPlayer: Boolean,
    fullPlayerVisualState: FullPlayerVisualState,
    containerHeight: Dp,
    currentQueueSourceName: String,
    currentSheetContentState: PlayerSheetState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isFavorite: Boolean,
    shouldRenderFullPlayer: Boolean = true,
    currentHorizontalPaddingStartPxProvider: () -> Float,
    currentHorizontalPaddingEndPxProvider: () -> Float,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onSongInfoClick: (Song) -> Unit = {}
) {
    currentSong?.let { currentSongNonNull ->
        miniPlayerScheme?.let { readyScheme ->
            CompositionLocalProvider(
                LocalMaterialTheme provides readyScheme
            ) {
                val miniPlayerZIndex by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value < 0.5f) 1f else 0f
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(MiniPlayerHeight)
                        .graphicsLayer {
                            alpha = (1f - playerContentExpansionFraction.value * 2f)
                                .coerceIn(0f, 1f)
                        }
                        // The layers are measured at full sheet width while the player expands;
                        // inset the mini player back to the card so it stays aligned with it.
                        .layout { measurable, constraints ->
                            val fraction = playerContentExpansionFraction.value
                            val startPaddingPx =
                                sheetHorizontalPaddingPx(currentHorizontalPaddingStartPxProvider())
                            val endPaddingPx =
                                sheetHorizontalPaddingPx(currentHorizontalPaddingEndPxProvider())
                            val miniWidthPx = miniPlayerLayerWidthPx(
                                playerContentWidthPx = constraints.maxWidth,
                                startPaddingPx = startPaddingPx,
                                endPaddingPx = endPaddingPx,
                                expansionFraction = fraction
                            )
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = miniWidthPx, maxWidth = miniWidthPx)
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(
                                    miniPlayerLayerOffsetXPx(startPaddingPx, fraction),
                                    0
                                )
                            }
                        }
                        .zIndex(miniPlayerZIndex)
                ) {
                    val isMiniPlayerVisible by remember {
                        derivedStateOf { playerContentExpansionFraction.value < 0.01f }
                    }
                    MiniPlayerContentInternal(
                        song = currentSongNonNull,
                        isPlaying = infrequentPlayerState.isPlaying,
                        isOutputConnecting = isOutputConnecting,
                        isPreparingPlayback = isPreparingPlayback,
                        onPlayPause = { playerViewModel.playPause() },
                        onPrevious = { playerViewModel.previousSong() },
                        onNext = { playerViewModel.nextSong() },
                        canScroll = isMiniPlayerVisible && infrequentPlayerState.isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (shouldRenderFullPlayer) {
            CompositionLocalProvider(
                LocalMaterialTheme provides albumColorScheme
            ) {
                // Only read inside graphicsLayer: the queue's open fraction changes every frame.
                val fullPlayerScale by remember(bottomSheetOpenFractionProvider) {
                    derivedStateOf { lerp(1f, 0.972f, bottomSheetOpenFractionProvider()) }
                }

                val fullPlayerZIndex by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f
                    }
                }
                val fullPlayerOffset by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value <= 0.01f) IntOffset(0, 10000)
                        else IntOffset.Zero
                    }
                }
                val fullPlayerRuntimePolicy = rememberFullPlayerRuntimePolicy(
                    currentSheetState = currentSheetContentState,
                    expansionFraction = playerContentExpansionFraction,
                    isQueueOccludingPlayer = isQueueOccludingPlayer
                )

                val currentPlaybackQueue by playerViewModel.queueFlow
                    .collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(containerHeight)
                        .graphicsLayer {
                            alpha = fullPlayerVisualState.contentAlpha
                            translationY = fullPlayerVisualState.translationY
                            scaleX = fullPlayerScale
                            scaleY = fullPlayerScale
                        }
                        .zIndex(fullPlayerZIndex)
                        .offset { fullPlayerOffset }
                ) {
                    val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                    val latestIsFavorite = rememberUpdatedState(isFavorite)
                    val expansionFractionProvider = remember(playerContentExpansionFraction) {
                        { playerContentExpansionFraction.value }
                    }
                    val isPlayingProvider = remember {
                        { latestInfrequentPlayerState.value.isPlaying }
                    }
                    val playWhenReadyProvider = remember {
                        { latestInfrequentPlayerState.value.playWhenReady }
                    }
                    val repeatModeProvider = remember {
                        { latestInfrequentPlayerState.value.repeatMode }
                    }
                    val isShuffleEnabledProvider = remember {
                        { latestInfrequentPlayerState.value.isShuffleEnabled }
                    }
                    val totalDurationProvider = remember {
                        { latestInfrequentPlayerState.value.totalDuration }
                    }
                    val lyricsProvider = remember {
                        { latestInfrequentPlayerState.value.lyrics }
                    }
                    val isFavoriteProvider = remember {
                        { latestIsFavorite.value }
                    }
                    val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                    val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                    val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                    val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                    val onCollapse = remember(playerViewModel) {
                        { playerViewModel.collapsePlayerSheet() }
                    }
                    val onShuffleToggle = remember(playerViewModel) {
                        { playerViewModel.toggleShuffle() }
                    }
                    val onRepeatToggle = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
                    val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleFavorite }

                    FullPlayerContent(
                        currentSong = currentSongNonNull,
                        currentPlaybackQueue = currentPlaybackQueue,
                        currentQueueSourceName = currentQueueSourceName,
                        currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                        isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                        shuffleTransitionInProgress = infrequentPlayerState.isShuffleTransitionInProgress,
                        repeatMode = infrequentPlayerState.repeatMode,
                        allowRealtimeUpdates = fullPlayerRuntimePolicy.allowRealtimeUpdates,
                        expansionFractionProvider = expansionFractionProvider,
                        currentSheetState = currentSheetContentState,
                        carouselStyle = carouselStyle,
                        loadingTweaks = fullPlayerLoadingTweaks,
                        isSheetDragGestureActive = isSheetDragGestureActive,
                        playerViewModel = playerViewModel,
                        currentPositionProvider = currentPositionProvider,
                        isPlayingProvider = isPlayingProvider,
                        playWhenReadyProvider = playWhenReadyProvider,
                        repeatModeProvider = repeatModeProvider,
                        isShuffleEnabledProvider = isShuffleEnabledProvider,
                        totalDurationProvider = totalDurationProvider,
                        lyricsProvider = lyricsProvider,
                        isOutputConnecting = isOutputConnecting,
                        isFavoriteProvider = isFavoriteProvider,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onCollapse = onCollapse,
                        onShowQueueClicked = onShowQueueClicked,
                        onQueueDragStart = onQueueDragStart,
                        onQueueDrag = onQueueDrag,
                        onQueueRelease = onQueueRelease,
                        onShuffleToggle = onShuffleToggle,
                        onRepeatToggle = onRepeatToggle,
                        onFavoriteToggle = onFavoriteToggle,
                        onSongInfoClick = onSongInfoClick
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerPrewarmLayer(
    prewarmFullPlayer: Boolean,
    currentSong: Song?,
    containerHeight: Dp,
    albumColorScheme: ColorScheme,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isOutputConnecting: Boolean,
    isFavorite: Boolean,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    if (prewarmFullPlayer && currentSong != null) {
        val currentPlaybackQueue by playerViewModel.queueFlow
            .collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalMaterialTheme provides albumColorScheme
        ) {
            Box(
                modifier = Modifier
                    .height(containerHeight)
                    .fillMaxWidth()
                    .alpha(0f)
                    .clipToBounds()
            ) {
                val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                val latestIsFavorite = rememberUpdatedState(isFavorite)
                val isPlayingProvider = remember { { latestInfrequentPlayerState.value.isPlaying } }
                val playWhenReadyProvider = remember { { latestInfrequentPlayerState.value.playWhenReady } }
                val repeatModeProvider = remember { { latestInfrequentPlayerState.value.repeatMode } }
                val isShuffleEnabledProvider = remember { { latestInfrequentPlayerState.value.isShuffleEnabled } }
                val totalDurationProvider = remember { { latestInfrequentPlayerState.value.totalDuration } }
                val lyricsProvider = remember { { latestInfrequentPlayerState.value.lyrics } }
                val isFavoriteProvider = remember { { latestIsFavorite.value } }
                val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                val onShuffleToggle = remember(playerViewModel) { { playerViewModel.toggleShuffle() } }
                val onRepeatToggle = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
                val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleFavorite }

                FullPlayerContent(
                    currentSong = currentSong,
                    currentPlaybackQueue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                    isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                    shuffleTransitionInProgress = infrequentPlayerState.isShuffleTransitionInProgress,
                    repeatMode = infrequentPlayerState.repeatMode,
                    allowRealtimeUpdates = false,
                    expansionFractionProvider = { 1f },
                    currentSheetState = PlayerSheetState.EXPANDED,
                    carouselStyle = carouselStyle,
                    loadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = currentPositionProvider,
                    isPlayingProvider = isPlayingProvider,
                    playWhenReadyProvider = playWhenReadyProvider,
                    repeatModeProvider = repeatModeProvider,
                    isShuffleEnabledProvider = isShuffleEnabledProvider,
                    totalDurationProvider = totalDurationProvider,
                    lyricsProvider = lyricsProvider,
                    isOutputConnecting = isOutputConnecting,
                    isFavoriteProvider = isFavoriteProvider,
                    onShowQueueClicked = onShowQueueClicked,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCollapse = {},
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }
}
