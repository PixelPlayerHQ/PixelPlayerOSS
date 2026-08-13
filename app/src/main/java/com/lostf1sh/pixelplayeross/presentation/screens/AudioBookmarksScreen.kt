@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import com.lostf1sh.pixelplayeross.data.model.Song
import androidx.compose.material3.ModalBottomSheet
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.ScreenWrapper
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.rememberModalSheetState
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.AudioBookmarksViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.ui.theme.GenreThemeUtils
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme
import com.lostf1sh.pixelplayeross.ui.theme.PixelPlayerStatusBarStyle
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import coil.size.Size
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBookmarksScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onOpenSidebar: () -> Unit,
    bookmarksViewModel: AudioBookmarksViewModel = hiltViewModel()
) {
    val bookmarks by bookmarksViewModel.allBookmarks.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val songIds = remember(bookmarks) { bookmarks.map { it.songId }.distinct() }
    val loadedSongs by remember(songIds) { bookmarksViewModel.observeSongs(songIds) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarkSongs = remember(bookmarks, loadedSongs) {
        buildBookmarkSongList(bookmarks, loadedSongs)
    }
    val rawFolders = remember(bookmarks, bookmarkSongs, query) {
        buildAudioBookmarkFolders(bookmarks, bookmarkSongs, query)
    }
    var sortMode by rememberSaveable { mutableStateOf(BookmarkFolderSortMode.MostMoments) }
    val folders = remember(rawFolders, sortMode) {
        sortAudioBookmarkFolders(rawFolders, sortMode)
    }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isMiniplayerVisible = stablePlayerState.currentSong != null

    ScreenWrapper(
        navController = navController,
        playerViewModel = playerViewModel
    ) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val motionScheme = remember { MotionScheme.expressive() }
        var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
        val floatingControlsBottomPadding by animateDpAsState(
            targetValue = if (isMiniplayerVisible) MiniPlayerHeight + 14.dp else 14.dp,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "BookmarkFloatingControlsBottomPadding"
        )
        val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topBarHeight = statusBarTopInset + 78.dp
        val isListScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        val topBarSurfaceFraction by animateFloatAsState(
            targetValue = if (isListScrolled || query.isNotBlank()) 1f else 0f,
            animationSpec = motionScheme.fastEffectsSpec(),
            label = "BookmarkKeepTopBarSurfaceFraction"
        )

        LaunchedEffect(query, sortMode) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + 12.dp,
                    bottom = 176.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (folders.isEmpty()) {
                    item {
                        BookmarksEmptyState(
                            title = if (query.isBlank()) "No bookmarked audio yet" else "No bookmark folders found",
                            subtitle = if (query.isBlank()) {
                                "Saved moments will appear here grouped by track."
                            } else {
                                "Try searching by song, artist, album, cue, or bookmark tag."
                            }
                        )
                    }
                } else {
                    itemsIndexed(
                        items = folders,
                        key = { _, folder -> folder.song.id },
                        contentType = { _, _ -> "bookmark_folder" }
                    ) { index, folder ->
                        BookmarkFolderCard(
                            folder = folder,
                            visualIndex = index,
                            playerViewModel = playerViewModel,
                            onClick = {
                                navController.navigateSafely(Screen.AudioBookmarkFolder.createRoute(folder.song.id))
                            },
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = motionScheme.fastSpatialSpec()
                                )
                                .padding(horizontal = 18.dp)
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(30.dp))
                }
            }

            BookmarkKeepSearchTopBar(
                query = query,
                onQueryChange = { query = it },
                surfaceFraction = topBarSurfaceFraction,
                onNavigationClick = {
                    if (!navController.popBackStack()) {
                        onOpenSidebar()
                    }
                },
                onClearQuery = { query = "" },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .height(topBarHeight)
            )

            if (fabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            fabMenuExpanded = false
                        }
                )
            }

            BookmarkActionFabMenu(
                expanded = fabMenuExpanded,
                sortMode = sortMode,
                hasQuery = query.isNotBlank(),
                canScrollToTop = isListScrolled,
                onExpandedChange = { fabMenuExpanded = it },
                onSortModeChange = { selectedMode ->
                    sortMode = selectedMode
                    fabMenuExpanded = false
                },
                onClearFilter = {
                    query = ""
                    fabMenuExpanded = false
                },
                onScrollToTop = {
                    fabMenuExpanded = false
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(11f)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = floatingControlsBottomPadding)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(48.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
//                            colorStops = arrayOf(
//                                0.0f to Color.Transparent,
//                                0.2f to Color.Transparent,
//                                0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
//                                1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
//                            )
                        )
                    )
            ) {

            }
        }
    }
}

@Composable
fun AudioBookmarkFolderScreen(
    songId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    bookmarksViewModel: AudioBookmarksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val bookmarks by bookmarksViewModel.allBookmarks.collectAsStateWithLifecycle()
    val songBookmarks = remember(bookmarks, songId) {
        bookmarks.filter { it.songId == songId }.sortedByDescending { it.createdTime }
    }
    val loadedSongs by remember(songId) { bookmarksViewModel.observeSongs(listOf(songId)) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val song = remember(songBookmarks, loadedSongs, songId) {
        loadedSongs.firstOrNull() ?: songBookmarks.firstOrNull()?.toFallbackSong() ?: Song.emptySong().copy(id = songId)
    }
    var actionsBookmark by remember { mutableStateOf<AudioBookmarkEntity?>(null) }
    var renamingBookmark by remember { mutableStateOf<AudioBookmarkEntity?>(null) }

    ScreenWrapper(
        navController = navController,
        playerViewModel = playerViewModel
    ) {
        val topBarState = rememberBookmarksCollapsibleTopBarState(maxTopBarHeight = 300.dp)
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val headerImageRequestSize = remember(configuration.screenWidthDp, density.density) {
            Size(
                width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                height = with(density) { 300.dp.roundToPx() }
            )
        }
        val songScheme = rememberBookmarkCardColorScheme(song, playerViewModel)

        MaterialTheme(colorScheme = songScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarState.nestedScrollConnection)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn(
                    state = topBarState.lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topBarState.headerHeight + 14.dp,
                        bottom = 128.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (songBookmarks.isEmpty()) {
                        item(key = "empty_bookmark_folder") {
                            BookmarksEmptyState(
                                title = "No moments in this folder",
                                subtitle = "Bookmarks for this track may have been deleted."
                            )
                        }
                    } else {
                        item(key = "bookmark_folder_count") {
                            BookmarkFolderSectionHeader(
                                bookmarkCount = songBookmarks.size,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                            )
                        }

                        items(
                            items = songBookmarks,
                            key = { it.id },
                            contentType = { "bookmark_moment" }
                        ) { bookmark ->
                            BookmarkMomentCard(
                                bookmark = bookmark,
                                onPlay = {
                                    if (song.id.isNotBlank() && song.id != "-1") {
                                        playerViewModel.playSongAtPosition(song, bookmark.timestampMs)
                                    } else {
                                        Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onMoreClick = { actionsBookmark = bookmark },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }

                BookmarkFolderTopBar(
                    song = song,
                    bookmarkCount = songBookmarks.size,
                    collapseFraction = topBarState.collapseFraction,
                    headerHeight = topBarState.headerHeight,
                    headerImageRequestSize = headerImageRequestSize,
                    onBackClick = { navController.popBackStack() }
                )
            }

            actionsBookmark?.let { bookmark ->
                BookmarkMomentActionsBottomSheet(
                    bookmark = bookmark,
                    onDismiss = { actionsBookmark = null },
                    onRename = {
                        actionsBookmark = null
                        renamingBookmark = bookmark
                    },
                    onCopyCue = {
                        val cueText = "${bookmark.title} - ${formatBookmarkTime(bookmark.timestampMs)}"
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Bookmark cue", cueText)))
                        }
                        actionsBookmark = null
                        Toast.makeText(context, "Cue copied", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        actionsBookmark = null
                        bookmarksViewModel.deleteBookmark(bookmark.id)
                    }
                )
            }

            renamingBookmark?.let { bookmark ->
                RenameBookmarkDialog(
                    bookmark = bookmark,
                    onDismiss = { renamingBookmark = null },
                    onRename = { title ->
                        renamingBookmark = null
                        bookmarksViewModel.renameBookmark(bookmark.id, title)
                    }
                )
            }
        }
    }
}

@Composable
private fun BookmarkFolderTopBar(
    song: Song,
    bookmarkCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onBackClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayerDarkTheme.current) Color.Black.copy(alpha = 0.58f)
        else Color.White.copy(alpha = 0.42f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val subtitle = remember(song.displayArtist, bookmarkCount) {
        listOf(
            song.displayArtist.ifBlank { "Unknown artist" },
            formatBookmarkMomentCount(bookmarkCount)
        ).joinToString(" - ")
    }
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
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                targetSize = headerImageRequestSize,
                allowHardware = true,
                crossfadeDurationMillis = 0,
                alpha = expandedContentAlpha,
                placeHolderBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1f + 0.04f * (1f - collapseFraction)
                        scaleY = 1f + 0.04f * (1f - collapseFraction)
                    }
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

        CollapsibleCommonTopBar(
            title = song.title.ifBlank { "Bookmarks" },
            subtitle = subtitle,
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackClick,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 24.dp,
            expandedTitleEndPadding = 28.dp,
            containerHeightRange = 116.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = RoundedSans,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.04f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false
        )
    }
}

@Composable
private fun BookmarkFolderSectionHeader(
    bookmarkCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(
                text = "Saved moments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatBookmarkMomentCount(bookmarkCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = bookmarkCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

private fun formatBookmarkMomentCount(count: Int): String =
    if (count == 1) "1 moment" else "$count moments"

private enum class BookmarkFolderSortMode(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector
) {
    MostMoments(
        label = "Most moments",
        contentDescription = "Sort bookmarks by most moments",
        icon = Icons.Rounded.Bookmark
    ),
    Recent(
        label = "Recent",
        contentDescription = "Sort bookmarks by recent moments",
        icon = Icons.Rounded.AccessTime
    ),
    Title(
        label = "A-Z",
        contentDescription = "Sort bookmarks alphabetically",
        icon = Icons.Rounded.SortByAlpha
    );

    fun next(): BookmarkFolderSortMode =
        entries[(ordinal + 1) % entries.size]
}

private fun sortAudioBookmarkFolders(
    folders: List<AudioBookmarkFolder>,
    sortMode: BookmarkFolderSortMode
): List<AudioBookmarkFolder> =
    when (sortMode) {
        BookmarkFolderSortMode.MostMoments -> folders.sortedWith(
            compareByDescending<AudioBookmarkFolder> { it.bookmarkCount }
                .thenByDescending { it.latestBookmarkCreatedTime }
                .thenBy { it.song.title.lowercase(Locale.getDefault()) }
        )

        BookmarkFolderSortMode.Recent -> folders.sortedWith(
            compareByDescending<AudioBookmarkFolder> { it.latestBookmarkCreatedTime }
                .thenByDescending { it.bookmarkCount }
                .thenBy { it.song.title.lowercase(Locale.getDefault()) }
        )

        BookmarkFolderSortMode.Title -> folders.sortedWith(
            compareBy<AudioBookmarkFolder> { it.song.title.lowercase(Locale.getDefault()) }
                .thenByDescending { it.bookmarkCount }
                .thenByDescending { it.latestBookmarkCreatedTime }
        )
    }

private data class BookmarkTitleTreatment(
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val scaleX: Float,
    val fontFeatureSettings: String? = null,
    val drawStyle: DrawStyle? = null,
    val colorAlpha: Float = 1f
)

@Composable
private fun rememberBookmarkTitleTreatment(
    visualIndex: Int
): BookmarkTitleTreatment {
    val density = LocalDensity.current
    val outlineStroke = remember(density) {
        Stroke(width = with(density) { 1.35.dp.toPx() })
    }
    val roundedFlexFont = rememberBookmarkFlexFont(
        weight = 480,
        width = 112f,
        slant = 0f,
        rond = 100f
    )
    val flexItalicFont = rememberBookmarkFlexFont(
        weight = 800,
        width = 132f,
        slant = -9f,
        rond = 78f
    )
    val outlineFont = rememberBookmarkGenreFont(
        weight = 700,
        width = 110f,
        grade = 0
    )
    val condensedItalicFont = rememberBookmarkFlexFont(
        weight = 700,
        width = 96f,
        slant = -7f,
        rond = 24f
    )
    val variant = remember(visualIndex) { Math.floorMod(visualIndex, 4) }

    return when (variant) {
        0 -> BookmarkTitleTreatment(
            fontFamily = roundedFlexFont,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal,
            fontSizeSp = 21,
            lineHeightSp = 27,
            scaleX = 1f,
            fontFeatureSettings = "liga, calt"
        )

        1 -> BookmarkTitleTreatment(
            fontFamily = flexItalicFont,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            fontSizeSp = 25,
            lineHeightSp = 26,
            scaleX = 1.04f
        )

        2 -> BookmarkTitleTreatment(
            fontFamily = outlineFont,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Normal,
            fontSizeSp = 31,
            lineHeightSp = 31,
            scaleX = 1.08f,
            drawStyle = outlineStroke,
            colorAlpha = 0.82f
        )

        else -> BookmarkTitleTreatment(
            fontFamily = condensedItalicFont,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontSizeSp = 25,
            lineHeightSp = 26,
            scaleX = 0.98f
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberBookmarkFlexFont(
    weight: Int,
    width: Float,
    slant: Float,
    rond: Float
): FontFamily =
    remember(weight, width, slant, rond) {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.slant(slant),
                    FontVariation.Setting("ROND", rond),
                    FontVariation.Setting("XTRA", 520f),
                    FontVariation.Setting("YOPQ", 90f),
                    FontVariation.Setting("YTLC", 505f)
                )
            )
        )
    }

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberBookmarkGenreFont(
    weight: Int,
    width: Float,
    grade: Int
): FontFamily =
    remember(weight, width, grade) {
        FontFamily(
            Font(
                resId = R.font.genre_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.grade(grade)
                )
            )
        )
    }

private data class BookmarksCollapsibleTopBarState(
    val lazyListState: LazyListState,
    val nestedScrollConnection: NestedScrollConnection,
    val headerHeight: Dp,
    val collapseFraction: Float
)

@Composable
private fun rememberBookmarksCollapsibleTopBarState(
    maxTopBarHeight: Dp = 174.dp,
    minTopBarContentHeight: Dp = 64.dp
): BookmarksCollapsibleTopBarState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = minTopBarContentHeight + statusBarHeight
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
        .coerceAtLeast(minTopBarHeightPx + 1f)
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(topBarHeight.value, minTopBarHeightPx, maxTopBarHeightPx) {
        collapseFraction = 1f -
            ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx))
                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember(lazyListState, minTopBarHeightPx, maxTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress, minTopBarHeightPx, maxTopBarHeightPx) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2f
            val canExpand = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    return BookmarksCollapsibleTopBarState(
        lazyListState = lazyListState,
        nestedScrollConnection = nestedScrollConnection,
        headerHeight = with(density) { topBarHeight.value.toDp() },
        collapseFraction = collapseFraction
    )
}

private fun bookmarkExpandedFolderKey(
    listState: LazyListState,
    focusableKeys: Set<Any>,
    currentFocusedKey: Any?,
    switchThresholdPx: Int,
    collapsedFolderItemHeightPx: Int,
    lastFocusableIndex: Int?,
    endEdgeFocusThresholdPx: Int
): Any? {
    val layoutInfo = listState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val visibleFolders = layoutInfo.visibleItemsInfo
        .filter { itemInfo -> focusableKeys.contains(itemInfo.key) }
        .map { itemInfo ->
            BookmarkVisibleItemGeometry(
                key = itemInfo.key,
                index = itemInfo.index,
                itemCenterPx = itemInfo.offset + itemInfo.size / 2,
                itemSizePx = itemInfo.size,
                itemEndPx = itemInfo.offset + itemInfo.size
            )
        }
    val stableFocusFolders = bookmarkStableFocusGeometries(
        visibleItems = visibleFolders,
        collapsedItemSizePx = collapsedFolderItemHeightPx
    )

    return bookmarkFocusedItemKey(
        visibleItems = stableFocusFolders,
        totalItemsCount = layoutInfo.totalItemsCount,
        viewportCenterPx = viewportCenter,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        currentFocusedKey = currentFocusedKey,
        switchThresholdPx = switchThresholdPx,
        lastFocusableIndex = lastFocusableIndex,
        viewportEndPx = layoutInfo.viewportEndOffset,
        endEdgeFocusThresholdPx = endEdgeFocusThresholdPx
    )
}

@Composable
private fun BookmarkKeepSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    surfaceFraction: Float,
    onNavigationClick: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val containerColor = lerpColor(
        MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
        MaterialTheme.colorScheme.surfaceContainerHigh,
        surfaceFraction
    )
    val pillColor = lerpColor(
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
        MaterialTheme.colorScheme.surfaceContainerHighest,
        surfaceFraction
    )
    val contentColor = lerpColor(
        MaterialTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.onSurface,
        surfaceFraction
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = lerp(0f, 4f, surfaceFraction).dp,
        shadowElevation = lerp(0f, 2f, surfaceFraction).dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), end = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = onNavigationClick,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = AbsoluteSmoothCornerShape(28.dp, 60),
                color = pillColor,
                contentColor = contentColor,
                tonalElevation = 0.dp,
                //border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f + surfaceFraction * 0.14f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = RoundedSans,
                            color = contentColor,
                            lineHeight = 20.sp,
                            letterSpacing = 0.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        )
                    ) { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isBlank()) {
                                Text(
                                    text = "Search bookmarks",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = RoundedSans,
                                        lineHeight = 20.sp,
                                        letterSpacing = 0.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }

                }
            }

            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .then(
                        if (query.isNotBlank()) {
                            Modifier.clickable(onClick = onClearQuery)
                        } else {
                            Modifier
                        }
                    ),
                shape = CircleShape,
                color = if (query.isNotBlank()) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (query.isNotBlank()) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                //border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f + surfaceFraction * 0.12f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (query.isNotBlank()) Icons.Rounded.Close else Icons.Rounded.Bookmark,
                        contentDescription = if (query.isNotBlank()) "Clear bookmark search" else "Bookmarks",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkActionFabMenu(
    expanded: Boolean,
    sortMode: BookmarkFolderSortMode,
    hasQuery: Boolean,
    canScrollToTop: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortModeChange: (BookmarkFolderSortMode) -> Unit,
    onClearFilter: () -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fabClosedContainer = MaterialTheme.colorScheme.primary
    val fabOpenContainer = MaterialTheme.colorScheme.primaryContainer
    val fabClosedContent = MaterialTheme.colorScheme.onPrimary
    val fabOpenContent = MaterialTheme.colorScheme.onPrimaryContainer
    val disabledActionContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val disabledActionContent = MaterialTheme.colorScheme.onSurfaceVariant

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        horizontalAlignment = Alignment.End,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                containerSize = ToggleFloatingActionButtonDefaults.containerSize(86.dp),
                containerColor = { progress ->
                    lerpColor(fabClosedContainer, fabOpenContainer, progress)
                },
                modifier = Modifier.animateFloatingActionButton(
                    visible = true,
                    alignment = Alignment.BottomEnd
                )
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Sort,
                    contentDescription = if (expanded) "Close bookmark actions" else "Open bookmark actions",
                    tint = lerpColor(fabClosedContent, fabOpenContent, checkedProgress)
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onSortModeChange(BookmarkFolderSortMode.MostMoments) },
            icon = {
                Icon(
                    imageVector = if (sortMode == BookmarkFolderSortMode.MostMoments) {
                        Icons.Rounded.Check
                    } else {
                        BookmarkFolderSortMode.MostMoments.icon
                    },
                    contentDescription = null
                )
            },
            text = { Text(BookmarkFolderSortMode.MostMoments.label) },
            containerColor = if (sortMode == BookmarkFolderSortMode.MostMoments) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (sortMode == BookmarkFolderSortMode.MostMoments) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        FloatingActionButtonMenuItem(
            onClick = { onSortModeChange(BookmarkFolderSortMode.Recent) },
            icon = {
                Icon(
                    imageVector = if (sortMode == BookmarkFolderSortMode.Recent) {
                        Icons.Rounded.Check
                    } else {
                        BookmarkFolderSortMode.Recent.icon
                    },
                    contentDescription = null
                )
            },
            text = { Text(BookmarkFolderSortMode.Recent.label) },
            containerColor = if (sortMode == BookmarkFolderSortMode.Recent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (sortMode == BookmarkFolderSortMode.Recent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        FloatingActionButtonMenuItem(
            onClick = { onSortModeChange(BookmarkFolderSortMode.Title) },
            icon = {
                Icon(
                    imageVector = if (sortMode == BookmarkFolderSortMode.Title) {
                        Icons.Rounded.Check
                    } else {
                        BookmarkFolderSortMode.Title.icon
                    },
                    contentDescription = null
                )
            },
            text = { Text(BookmarkFolderSortMode.Title.label) },
            containerColor = if (sortMode == BookmarkFolderSortMode.Title) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (sortMode == BookmarkFolderSortMode.Title) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                if (hasQuery) {
                    onClearFilter()
                }
            },
            icon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            text = { Text(stringResource(R.string.audio_bookmarks_clear_filter)) },
            containerColor = if (hasQuery) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                disabledActionContainer
            },
            contentColor = if (hasQuery) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                disabledActionContent
            }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                if (canScrollToTop) {
                    onScrollToTop()
                }
            },
            icon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) },
            text = { Text(stringResource(R.string.audio_bookmarks_back_to_top)) },
            containerColor = if (canScrollToTop) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                disabledActionContainer
            },
            contentColor = if (canScrollToTop) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                disabledActionContent
            }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BookmarkFolderCard(
    folder: AudioBookmarkFolder,
    visualIndex: Int,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songScheme = rememberBookmarkCardColorScheme(folder.song, playerViewModel)
    val cardHeight = 154.dp
    val containerShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val songTitle = folder.song.title.ifBlank { "Unknown audio" }
    val titleTreatment = rememberBookmarkTitleTreatment(
        visualIndex = visualIndex
    )
    val artistAlbum = remember(folder.song.displayArtist, folder.song.album) {
        listOf(
            folder.song.displayArtist.ifBlank { "Unknown artist" },
            folder.song.album
        ).filter { it.isNotBlank() }.joinToString(" - ")
    }
    val supportingFont = rememberBookmarkFlexFont(
        weight = 560,
        width = 106f,
        slant = 0f,
        rond = 100f
    )
    val monoFont = rememberBookmarkGenreFont(
        weight = 680,
        width = 94f,
        grade = 30
    )
    val backgroundBrush = remember(songScheme) {
        Brush.linearGradient(
            listOf(
                songScheme.primaryContainer,
                songScheme.surfaceContainerLow,
                songScheme.tertiaryContainer
            )
        )
    }

    MaterialTheme(colorScheme = songScheme) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight),
            shape = containerShape,
            color = songScheme.surfaceContainerLow,
            contentColor = songScheme.onSurface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, songScheme.outlineVariant.copy(alpha = 0.28f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(containerShape)
                    .background(backgroundBrush)
            ) {
//                Text(
//                    text = folder.bookmarkCount.toString().padStart(2, '0'),
//                    style = TextStyle(
//                        fontFamily = monoFont,
//                        fontWeight = FontWeight.Bold,
//                        fontSize = 56.sp,
//                        lineHeight = 52.sp,
//                        letterSpacing = 0.sp
//                    ),
//                    color = songScheme.onSurface.copy(alpha = 0.11f),
//                    modifier = Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(horizontal = 16.dp, vertical = 10.dp)
//                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BookmarkFolderArtworkBlock(
                        folder = folder,
                        songScheme = songScheme,
                        monoFont = monoFont
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
//                        Text(
//                            text = "CUE FOLDER",
//                            style = MaterialTheme.typography.labelSmall.copy(
//                                fontFamily = monoFont,
//                                fontWeight = FontWeight.Bold,
//                                lineHeight = 13.sp,
//                                letterSpacing = 0.sp
//                            ),
//                            color = songScheme.primary,
//                            maxLines = 1
//                        )
                        Text(
                            text = songTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = titleTreatment.fontSizeSp.sp,
                                lineHeight = titleTreatment.lineHeightSp.sp,
                                fontStyle = titleTreatment.fontStyle,
                                letterSpacing = 0.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = titleTreatment.scaleX),
                                fontFeatureSettings = titleTreatment.fontFeatureSettings,
                                drawStyle = titleTreatment.drawStyle
                            ),
                            fontFamily = titleTreatment.fontFamily,
                            fontWeight = titleTreatment.fontWeight,
                            color = songScheme.onSurface.copy(alpha = titleTreatment.colorAlpha),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistAlbum,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp, letterSpacing = 0.sp),
                            fontFamily = supportingFont,
                            fontWeight = FontWeight.Medium,
                            color = songScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        BookmarkFolderCueChips(
                            bookmarks = folder.bookmarks,
                            songScheme = songScheme,
                            monoFont = monoFont,
                            horizontalAlignment = Alignment.Start
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkFolderArtworkBlock(
    folder: AudioBookmarkFolder,
    songScheme: ColorScheme,
    monoFont: FontFamily
) {
    val artworkShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 22.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 22.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 6.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 6.dp,
        smoothnessAsPercentBL = 60
    )
    val counterShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 6.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 6.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 18.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 18.dp,
        smoothnessAsPercentBL = 60
    )
    Column(
        modifier = Modifier
            .width(96.dp)
            .height(128.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SmartImage(
            model = folder.song.albumArtUriString,
            contentDescription = null,
            targetSize = Size(144, 144),
            shape = artworkShape,
            placeHolderBackgroundColor = songScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            shape = counterShape,
            color = songScheme.primary,
            contentColor = songScheme.onPrimary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    tint = songScheme.onPrimary,
                    contentDescription = null
                )
                Text(
                    text = folder.bookmarkCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        //fontFamily = monoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (folder.bookmarkCount > 999) 14.sp else 17.sp,
//                        lineHeight = 15.sp,
//                        letterSpacing = 0.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BookmarkFolderCueChips(
    bookmarks: List<AudioBookmarkEntity>,
    songScheme: ColorScheme,
    monoFont: FontFamily,
    horizontalAlignment: Alignment.Horizontal
) {
    val cueLabels = remember(bookmarks) {
        bookmarks
            .take(3)
            .map { formatBookmarkTime(it.timestampMs) }
    }
    if (cueLabels.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (horizontalAlignment == Alignment.End) {
            Spacer(modifier = Modifier.weight(1f))
        }
        cueLabels.forEach { cueLabel ->
            Surface(
                shape = CircleShape,
                color = songScheme.surface.copy(alpha = 0.72f),
                contentColor = songScheme.onSurface
            ) {
                Text(
                    text = cueLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = monoFont,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
        if (horizontalAlignment == Alignment.Start) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookmarkMomentCard(
    bookmark: AudioBookmarkEntity,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(bookmark.timestampMs) { formatBookmarkTime(bookmark.timestampMs) }
    val cueLabel = remember(formattedTime) { formattedTime }

    Surface(
        onClick = onPlay,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = cueLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .width(30.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Bookmark actions"
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkMomentActionsBottomSheet(
    bookmark: AudioBookmarkEntity,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCopyCue: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val formattedTime = remember(bookmark.timestampMs) { formatBookmarkTime(bookmark.timestampMs) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(26.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = bookmark.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Cue $formattedTime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            BookmarkMomentPrimaryActions(
                onRename = onRename,
                onCopyCue = onCopyCue
            )
            Spacer(modifier = Modifier.height(10.dp))
            BookmarkMomentDeleteAction(onDelete = onDelete)
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun BookmarkMomentPrimaryActions(
    onRename: () -> Unit,
    onCopyCue: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var clickPending by remember { mutableStateOf(false) }

    val renameInteractionSource = remember { MutableInteractionSource() }
    val isRenamePressed by renameInteractionSource.collectIsPressedAsState()
    var renameVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isRenamePressed) {
        if (isRenamePressed) {
            renameVisualPressed = true
        } else {
            delay(180)
            renameVisualPressed = false
        }
    }

    val copyInteractionSource = remember { MutableInteractionSource() }
    val isCopyPressed by copyInteractionSource.collectIsPressedAsState()
    var copyVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isCopyPressed) {
        if (isCopyPressed) {
            copyVisualPressed = true
        } else {
            delay(180)
            copyVisualPressed = false
        }
    }

    val pressSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
    val renamePressFraction by animateFloatAsState(
        targetValue = if (renameVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "BookmarkRenamePressFraction"
    )
    val copyPressFraction by animateFloatAsState(
        targetValue = if (copyVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "BookmarkCopyPressFraction"
    )

    val renameWeight = (0.5f + 0.08f * renamePressFraction - 0.08f * copyPressFraction).coerceAtLeast(0.1f)
    val copyWeight = (0.5f + 0.08f * copyPressFraction - 0.08f * renamePressFraction).coerceAtLeast(0.1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookmarkMomentExpressiveActionButton(
            modifier = Modifier
                .weight(renameWeight)
                .fillMaxHeight(),
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.action_rename),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            interactionSource = renameInteractionSource,
            onClick = {
                if (clickPending) return@BookmarkMomentExpressiveActionButton
                clickPending = true
                renameVisualPressed = true
                coroutineScope.launch {
                    delay(180)
                    renameVisualPressed = false
                    clickPending = false
                    onRename()
                }
            }
        )
        BookmarkMomentExpressiveActionButton(
            modifier = Modifier
                .weight(copyWeight)
                .fillMaxHeight(),
            icon = Icons.Rounded.ContentCopy,
            label = "Copy cue",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            interactionSource = copyInteractionSource,
            onClick = {
                if (clickPending) return@BookmarkMomentExpressiveActionButton
                clickPending = true
                copyVisualPressed = true
                coroutineScope.launch {
                    delay(180)
                    copyVisualPressed = false
                    clickPending = false
                    onCopyCue()
                }
            }
        )
    }
}

@Composable
private fun BookmarkMomentDeleteAction(
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var clickPending by remember { mutableStateOf(false) }
    val deleteInteractionSource = remember { MutableInteractionSource() }
    val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
    var deleteVisualPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isDeletePressed) {
        if (isDeletePressed) {
            deleteVisualPressed = true
        } else {
            delay(180)
            deleteVisualPressed = false
        }
    }
    val deletePressFraction by animateFloatAsState(
        targetValue = if (deleteVisualPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "BookmarkDeletePressFraction"
    )
    val deleteContainerColor = lerpColor(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.error,
        deletePressFraction * 0.18f
    )

    FilledTonalButton(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = deleteContainerColor,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        shape = CircleShape,
        interactionSource = deleteInteractionSource,
        onClick = {
            if (clickPending) return@FilledTonalButton
            clickPending = true
            deleteVisualPressed = true
            coroutineScope.launch {
                delay(180)
                deleteVisualPressed = false
                clickPending = false
                onDelete()
            }
        }
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete bookmark"
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Delete",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = RoundedSans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookmarkMomentExpressiveActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    FilledTonalButton(
        modifier = modifier.heightIn(min = 92.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        shape = CircleShape,
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RenameBookmarkDialog(
    bookmark: AudioBookmarkEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember(bookmark.id) { mutableStateOf(bookmark.title) }
    val trimmedTitle = title.trim()
    val canRename = trimmedTitle.isNotEmpty() && trimmedTitle != bookmark.title

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Rename bookmark",
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Update the label for this saved cue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.audio_bookmarks_name_label)) },
                    isError = title.isNotEmpty() && trimmedTitle.isEmpty(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(trimmedTitle) },
                enabled = canRename
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
private fun BookmarksEmptyState(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                modifier = Modifier
                    .padding(18.dp)
                    .size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rememberSongColorScheme(song: Song): ColorScheme {
    val isDark = LocalPixelPlayerDarkTheme.current
    val paletteKey = song.genre ?: song.album.ifBlank { song.id }
    return remember(paletteKey, isDark) {
        GenreThemeUtils.getGenreColorScheme(
            genreId = paletteKey,
            isDark = isDark
        )
    }
}

@Composable
private fun rememberBookmarkCardColorScheme(
    song: Song,
    playerViewModel: PlayerViewModel
): ColorScheme {
    val fallbackScheme = rememberSongColorScheme(song)
    val albumArtUri = song.albumArtUriString?.takeIf { it.isNotBlank() }.orEmpty()
    val albumColorSchemeFlow = remember(albumArtUri) {
        playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(albumArtUri)
    }
    val albumColorSchemePair by albumColorSchemeFlow.collectAsStateWithLifecycle()
    val isDark = LocalPixelPlayerDarkTheme.current

    return remember(albumColorSchemePair, isDark, fallbackScheme) {
        albumColorSchemePair?.let { pair ->
            if (isDark) pair.dark else pair.light
        } ?: fallbackScheme
    }
}

private fun buildBookmarkSongList(
    bookmarks: List<AudioBookmarkEntity>,
    loadedSongs: List<Song>
): List<Song> {
    val loadedById = loadedSongs.associateBy { it.id }
    val fallbackSongs = bookmarks
        .distinctBy { it.songId }
        .mapNotNull { bookmark ->
            if (loadedById.containsKey(bookmark.songId)) {
                null
            } else {
                bookmark.toFallbackSong()
            }
        }

    return loadedSongs + fallbackSongs
}

private fun AudioBookmarkEntity.toFallbackSong(): Song =
    Song.emptySong().copy(
        id = songId,
        title = songTitle,
        artist = artistName,
        albumArtUriString = albumArtUri
    )

private fun formatBookmarkTime(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
