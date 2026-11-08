package com.lostf1sh.pixelplayeross.presentation.components.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveScrollBar
import com.lostf1sh.pixelplayeross.presentation.components.QueueControlsToolbar
import com.lostf1sh.pixelplayeross.presentation.components.QueueHeader
import com.lostf1sh.pixelplayeross.presentation.components.QueuePlaylistSongItem
import com.lostf1sh.pixelplayeross.presentation.components.QueueToolbarMenuButton
import com.lostf1sh.pixelplayeross.presentation.components.SaveQueueAsPlaylistSheet
import com.lostf1sh.pixelplayeross.presentation.components.TimerOptionsBottomSheet
import com.lostf1sh.pixelplayeross.presentation.utils.LocalAppHapticsConfig
import com.lostf1sh.pixelplayeross.presentation.utils.performAppCompatHapticFeedback
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.sharedPlayerViewModel
import com.lostf1sh.pixelplayeross.utils.PlaybackSpeedUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedQueuePane(
    queue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    currentMediaItemIndex: Int = -1,
    isPlaying: Boolean,
    onPlaySong: (Song, Int) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onClose: (() -> Unit)? = null,
    isShuffleOn: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    playerViewModel: PlayerViewModel = sharedPlayerViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val currentSongIndex = remember(queue, currentSongId, currentMediaItemIndex) {
        if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == currentSongId) {
            currentMediaItemIndex
        } else {
            queue.indexOfFirst { it.id == currentSongId }
        }
    }

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val showQueueHistory = settingsState.showQueueHistory
    val queueIndexOffset = if (showQueueHistory || currentSongIndex < 0) 0 else currentSongIndex

    val displaySongs = remember(queue, queueIndexOffset) {
        if (queueIndexOffset == 0) {
            queue
        } else if (queue is RandomAccess) {
            queue.subList(queueIndexOffset, queue.size)
        } else {
            queue.drop(queueIndexOffset)
        }
    }

    val currentSongDisplayIndex = remember(currentSongIndex, queueIndexOffset) {
        if (currentSongIndex < 0) -1 else currentSongIndex - queueIndexOffset
    }

    val listState = rememberLazyListState()
    val queueCoroutineScope = rememberCoroutineScope()
    val displaySongCount = displaySongs.size

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var showClearQueueDialog by rememberSaveable { mutableStateOf(false) }
    var showSavePlaylistSheet by rememberSaveable { mutableStateOf(false) }

    val activeTimerValueDisplay = playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
    val activeTimerDurationMinutes = playerViewModel.activeTimerDurationMinutes.collectAsStateWithLifecycle()
    val playCount = playerViewModel.playCount.collectAsStateWithLifecycle()
    val isEndOfTrackTimerActive = playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()
    val playbackSpeed = settingsViewModel.playbackSpeed.collectAsStateWithLifecycle()

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    var reorderPreviewOrder by remember { mutableStateOf<List<Int>?>(null) }
    var reorderPreviewKeys by remember { mutableStateOf<List<Long>?>(null) }
    var reorderPreviewBaseQueue by remember { mutableStateOf<List<Song>?>(null) }
    var pendingReorderExpectedIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingReorderGraceUpdates by remember { mutableIntStateOf(0) }

    var committedDisplaySongIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var committedDisplayKeys by remember { mutableStateOf<List<Long>>(emptyList()) }
    var nextStableQueueItemKey by remember { mutableLongStateOf(0L) }

    var reorderPreviewQueueSignature by remember { mutableStateOf<Int?>(null) }
    val displaySongsSignature = remember(displaySongs, queueIndexOffset) {
        (queueIndexOffset * 31) + System.identityHashCode(displaySongs)
    }

    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (reorderPreviewOrder == null) {
                reorderPreviewBaseQueue = queue
            }
            val currentOrder = reorderPreviewOrder
                ?: List(displaySongCount) { queueIndexOffset + it }
            val currentKeys = reorderPreviewKeys
                ?: committedDisplayKeys.takeIf { it.size == displaySongCount }
                ?: List(displaySongCount) { (queueIndexOffset + it).toLong() }

            val keyToLocalIndex = HashMap<Long, Int>(displaySongCount).apply {
                for (index in 0 until displaySongCount) {
                    val stableKey = currentKeys.getOrNull(index) ?: (queueIndexOffset + index).toLong()
                    put(stableKey, index)
                }
            }

            fun resolveKeyToIndex(key: Any?): Int? {
                val stableKey = key as? Long ?: return null
                keyToLocalIndex[stableKey]?.let { return it }
                val defaultIndex = (stableKey - queueIndexOffset).toInt()
                return defaultIndex.takeIf { it in 0 until displaySongCount }
            }

            val fromLocalIndex = resolveKeyToIndex(from.key) ?: return@rememberReorderableLazyListState
            val toLocalIndex = resolveKeyToIndex(to.key) ?: return@rememberReorderableLazyListState
            if (fromLocalIndex == toLocalIndex) return@rememberReorderableLazyListState

            reorderPreviewOrder = currentOrder.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            reorderPreviewKeys = currentKeys.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromLocalIndex
            }
            lastMovedTo = toLocalIndex
        },
    )
    val isReordering by remember {
        derivedStateOf { reorderableState.isAnyItemDragging }
    }

    fun remapCommittedKeysForDisplay(newSongs: List<Song>) {
        if (committedDisplaySongIds.isNotEmpty() && newSongs.isNotEmpty()) {
            val firstNewId = newSongs.first().id
            val startIndex = committedDisplaySongIds.indexOf(firstNewId)
            if (startIndex >= 0 && startIndex + newSongs.size <= committedDisplaySongIds.size) {
                var suffixMatches = true
                for (i in newSongs.indices) {
                    if (committedDisplaySongIds[startIndex + i] != newSongs[i].id) {
                        suffixMatches = false
                        break
                    }
                }
                if (suffixMatches) {
                    committedDisplaySongIds = committedDisplaySongIds.subList(startIndex, startIndex + newSongs.size).toList()
                    committedDisplayKeys = committedDisplayKeys.subList(startIndex, startIndex + newSongs.size).toList()
                    return
                }
            }
        }

        val reusableKeysBySongId = mutableMapOf<String, ArrayDeque<Long>>()
        committedDisplaySongIds.forEachIndexed { index, songId ->
            val key = committedDisplayKeys.getOrNull(index) ?: return@forEachIndexed
            reusableKeysBySongId.getOrPut(songId) { ArrayDeque() }.addLast(key)
        }

        var nextKey = nextStableQueueItemKey
        if (committedDisplaySongIds.isEmpty() && committedDisplayKeys.isEmpty()) {
            nextKey = queueIndexOffset.toLong()
        }
        val remappedKeys = ArrayList<Long>(newSongs.size)
        newSongs.forEach { song ->
            val reused = reusableKeysBySongId[song.id]?.removeFirstOrNull()
            if (reused != null) {
                remappedKeys.add(reused)
            } else {
                remappedKeys.add(nextKey)
                nextKey++
            }
        }

        committedDisplaySongIds = newSongs.map { it.id }
        committedDisplayKeys = remappedKeys
        nextStableQueueItemKey = nextKey
    }

    LaunchedEffect(displaySongsSignature) {
        if (reorderPreviewOrder != null) {
            reorderPreviewQueueSignature = displaySongsSignature
            return@LaunchedEffect
        }
        remapCommittedKeysForDisplay(displaySongs)
    }

    val latestQueue by rememberUpdatedState(queue)
    val latestQueueIndexOffset by rememberUpdatedState(queueIndexOffset)
    val latestOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(isReordering) {
        if (!isReordering) {
            val finalPreview = reorderPreviewOrder
            val baseQueue = reorderPreviewBaseQueue
            val initialFrom = lastMovedFrom
            val finalTo = lastMovedTo
            val hasDistinctMove = initialFrom != null && finalTo != null && initialFrom != finalTo

            if (finalPreview != null && baseQueue != null && hasDistinctMove) {
                val fullPreview = baseQueue.indices.map { index ->
                    if (index < latestQueueIndexOffset) {
                        index
                    } else {
                        finalPreview.getOrNull(index - latestQueueIndexOffset) ?: index
                    }
                }
                val fromGlobal = initialFrom + latestQueueIndexOffset
                val toGlobal = finalTo + latestQueueIndexOffset

                val expectedIds = fullPreview.mapNotNull { baseQueue.getOrNull(it)?.id }
                pendingReorderExpectedIds = expectedIds
                pendingReorderGraceUpdates = 0

                val finalKeys = reorderPreviewKeys
                if (finalKeys != null && finalKeys.size == committedDisplayKeys.size) {
                    committedDisplayKeys = finalKeys
                    committedDisplaySongIds = finalPreview.mapNotNull { baseQueue.getOrNull(it)?.id }
                }

                latestOnReorder(fromGlobal, toGlobal)
            } else if (finalPreview != null && baseQueue != null) {
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                reorderPreviewQueueSignature = null
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                remapCommittedKeysForDisplay(displaySongs)
            }
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    LaunchedEffect(queue) {
        val expected = pendingReorderExpectedIds ?: return@LaunchedEffect
        val currentIds = queue.map { it.id }
        if (currentIds == expected) {
            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
            reorderPreviewQueueSignature = null
            remapCommittedKeysForDisplay(displaySongs)
        } else {
            pendingReorderGraceUpdates++
            if (pendingReorderGraceUpdates >= 3) {
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                reorderPreviewQueueSignature = null
                remapCommittedKeysForDisplay(displaySongs)
            }
        }
    }

    val activeSongSource = if (reorderPreviewOrder != null && reorderPreviewBaseQueue != null) {
        reorderPreviewBaseQueue!!
    } else {
        queue
    }

    fun activeQueueIndexAt(displayIndex: Int): Int {
        val preview = reorderPreviewOrder
        return if (preview != null && displayIndex in preview.indices) {
            preview[displayIndex]
        } else {
            queueIndexOffset + displayIndex
        }
    }

    fun activeKeyAt(displayIndex: Int): Long {
        val previewKeys = reorderPreviewKeys
        if (previewKeys != null && displayIndex in previewKeys.indices) {
            return previewKeys[displayIndex]
        }
        val committed = committedDisplayKeys
        if (displayIndex in committed.indices) {
            return committed[displayIndex]
        }
        return (queueIndexOffset + displayIndex).toLong()
    }

    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            QueueHeader(
                queueSourceName = currentQueueSourceName,
                queueCount = displaySongCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                onLocateCurrentSong = {
                    if (currentSongDisplayIndex in 0 until displaySongCount) {
                        queueCoroutineScope.launch {
                            if (abs(currentSongDisplayIndex - listState.firstVisibleItemIndex) > 20) {
                                listState.scrollToItem(currentSongDisplayIndex)
                            } else {
                                listState.animateScrollToItem(currentSongDisplayIndex)
                            }
                        }
                    }
                },
                onClose = onClose
            )

            if (displaySongCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_e_queue_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !(isReordering || reorderHandleInUse),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 10.dp,
                            bottom = 96.dp
                        )
                    ) {
                        items(
                            count = displaySongCount,
                            key = { index -> activeKeyAt(index) },
                            contentType = { "queue_song" }
                        ) { index ->
                            val queueIndex = activeQueueIndexAt(index)
                            if (queueIndex !in activeSongSource.indices) return@items
                            val itemStableKey = activeKeyAt(index)
                            val song = activeSongSource[queueIndex]
                            val canReorder = index > currentSongDisplayIndex
                            ReorderableItem(
                                state = reorderableState,
                                key = itemStableKey,
                                enabled = canReorder,
                                animateItemModifier = when {
                                    isReordering || reorderHandleInUse || reorderPreviewOrder != null -> Modifier.animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    else -> Modifier.animateItem(
                                        fadeInSpec = tween(durationMillis = 140),
                                        fadeOutSpec = tween(durationMillis = 120),
                                        placementSpec = tween(durationMillis = 180)
                                    )
                                }
                            ) { isDragging ->
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.015f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "scaleAnimation"
                                )

                                QueuePlaylistSongItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = scale
                                             scaleY = scale
                                        },
                                    onClick = { onPlaySong(song, queueIndex) },
                                    onLongClick = { onSongInfoClick(song) },
                                    song = song,
                                    isCurrentSong = index == currentSongDisplayIndex,
                                    isPlaying = isPlaying,
                                    isDragging = isDragging,
                                    onRemoveClick = { onRemoveSong(song.id) },
                                    isReorderModeEnabled = false,
                                    isDragHandleVisible = canReorder,
                                    isRemoveButtonVisible = false,
                                    enableSwipeToDismiss = canReorder,
                                    swipeStateIdentity = itemStableKey,
                                    onDismissSong = { onRemoveSong(song.id) },
                                    isFromPlaylist = false,
                                    onMoreOptionsClick = { onSongInfoClick(song) },
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        reorderHandleInUse = true
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        reorderHandleInUse = false
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_END
                                                        )
                                                    }
                                                )
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = stringResource(R.string.presentation_batch_b_reorder_song),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    ExpressiveScrollBar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 16.dp, end = 4.dp, bottom = 16.dp)
                    )

                    val isTimerActiveDerived = remember {
                        derivedStateOf { activeTimerValueDisplay.value != null }
                    }
                    val isSpeedModifiedDerived = remember {
                        derivedStateOf { !PlaybackSpeedUtils.isNormal(playbackSpeed.value) }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .height(70.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QueueControlsToolbar(
                            isShuffleOn = isShuffleOn,
                            repeatMode = repeatMode,
                            isTimerActive = isTimerActiveDerived,
                            isSpeedModified = isSpeedModifiedDerived,
                            onToggleShuffle = onToggleShuffle,
                            onToggleRepeat = onToggleRepeat,
                            onTimerClick = { showTimerOptions = true },
                            onSpeedClick = { showSpeedSheet = true },
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        MediumFloatingActionButton(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f),
                            onClick = { isFabExpanded = !isFabExpanded },
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTR = 50.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusTL = 8.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBR = 50.dp,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusBL = 8.dp,
                                smoothnessAsPercentBL = 60
                            ),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = stringResource(R.string.presentation_batch_e_cd_queue_actions),
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(20f)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isFabExpanded = false
                                }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        )
                                    )
                                )
                                .clickable {
                                    isFabExpanded = false
                                }
                                .zIndex(30f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                                    .padding(bottom = 12.dp + 70.dp + 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (currentSongDisplayIndex >= 0 && currentSongDisplayIndex < displaySongCount) {
                                    QueueToolbarMenuButton(
                                        text = stringResource(R.string.presentation_batch_e_action_locate_current_song),
                                        icon = Icons.Rounded.MyLocation,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        onClick = {
                                            isFabExpanded = false
                                            queueCoroutineScope.launch {
                                                val firstVisible = listState.firstVisibleItemIndex
                                                if (abs(currentSongDisplayIndex - firstVisible) > 20) {
                                                    listState.scrollToItem(currentSongDisplayIndex)
                                                } else {
                                                    listState.animateScrollToItem(currentSongDisplayIndex)
                                                }
                                            }
                                        }
                                    )
                                }
                                QueueToolbarMenuButton(
                                    text = stringResource(R.string.presentation_batch_e_action_clear_queue),
                                    icon = Icons.Filled.ClearAll,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    onClick = {
                                        isFabExpanded = false
                                        showClearQueueDialog = true
                                    }
                                )
                                QueueToolbarMenuButton(
                                    text = stringResource(R.string.presentation_batch_e_action_save_as_playlist),
                                    icon = Icons.Filled.LibraryAdd,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onClick = {
                                        isFabExpanded = false
                                        showSavePlaylistSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTimerOptions) {
            TimerOptionsBottomSheet(
                onPlayCounter = { count -> playerViewModel.playCounted(count) },
                activeTimerValueDisplay = activeTimerValueDisplay.value,
                activeTimerDurationMinutes = activeTimerDurationMinutes.value,
                playCount = playCount.value,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive.value,
                onDismiss = { showTimerOptions = false },
                onCancelCountedPlay = { playerViewModel.cancelCountedPlay() },
                onSetPredefinedTimer = { minutes -> playerViewModel.setSleepTimer(minutes) },
                onSetEndOfTrackTimer = { enable -> playerViewModel.setEndOfTrackTimer(enable) },
                onOpenCustomTimePicker = {},
                onCancelTimer = { playerViewModel.cancelSleepTimer() }
            )
        }

        if (showSpeedSheet) {
            PlaybackSpeedBottomSheet(
                speed = playbackSpeed.value,
                onSpeedChange = settingsViewModel::setPlaybackSpeed,
                onDismiss = { showSpeedSheet = false }
            )
        }

        if (showClearQueueDialog) {
            AlertDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = { Text(stringResource(R.string.presentation_batch_e_clear_queue_dialog_title)) },
                text = { Text(stringResource(R.string.presentation_batch_e_clear_queue_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            playerViewModel.clearQueueExceptCurrent()
                            showClearQueueDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.presentation_batch_b_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearQueueDialog = false }
                    ) {
                        Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        }

        if (showSavePlaylistSheet) {
            val context = LocalContext.current
            val defaultName = remember(currentQueueSourceName) {
                if (currentQueueSourceName.isNotBlank()) {
                    context.resources.getString(
                        R.string.presentation_batch_e_queue_named_suffix,
                        currentQueueSourceName
                    )
                } else {
                    context.resources.getString(R.string.presentation_batch_e_queue_current)
                }
            }
            SaveQueueAsPlaylistSheet(
                songs = queue,
                defaultName = defaultName,
                onDismiss = { showSavePlaylistSheet = false },
                onConfirm = { name, selectedIds ->
                    val orderedSelection = queue
                        .filter { selectedIds.contains(it.id) }
                        .map { it.id }
                    if (orderedSelection.isNotEmpty()) {
                        playlistViewModel.createPlaylist(
                            name = name,
                            songIds = orderedSelection,
                            isQueueGenerated = true
                        )
                    }
                    showSavePlaylistSheet = false
                }
            )
        }
    }
}
