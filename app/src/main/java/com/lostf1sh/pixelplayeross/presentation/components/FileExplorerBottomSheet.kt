@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.presentation.screens.TabAnimation
import com.lostf1sh.pixelplayeross.presentation.viewmodel.DirectoryEntry
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.utils.StorageInfo
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileExplorerDialog(
    visible: Boolean,
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    availableStorages: List<StorageInfo>,
    selectedStorageIndex: Int,
    isLoading: Boolean,
    isPriming: Boolean = false,
    isReady: Boolean = true,
    isCurrentDirectoryResolved: Boolean = true,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onStorageSelected: (Int) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = {
                if (!isAtRoot) {
                    onNavigateUp()
                } else {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "file_explorer_dialog"
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    FileExplorerContent(
                        currentPath = currentPath,
                        directoryChildren = directoryChildren,
                        availableStorages = availableStorages,
                        selectedStorageIndex = selectedStorageIndex,
                        isLoading = isLoading,
                        isPriming = isPriming,
                        isReady = isReady,
                        isCurrentDirectoryResolved = isCurrentDirectoryResolved,
                        isAtRoot = isAtRoot,
                        rootDirectory = rootDirectory,
                        onNavigateTo = onNavigateTo,
                        onNavigateUp = onNavigateUp,
                        onNavigateHome = onNavigateHome,
                        onToggleAllowed = onToggleAllowed,
                        onRefresh = onRefresh,
                        onStorageSelected = onStorageSelected,
                        onDone = onDone,
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerContent(
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    availableStorages: List<StorageInfo>,
    selectedStorageIndex: Int,
    isLoading: Boolean,
    isPriming: Boolean,
    isReady: Boolean,
    isCurrentDirectoryResolved: Boolean,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onStorageSelected: (Int) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.setup_excluded_folders_title),
    leadingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val safeSelectedStorageIndex = remember(availableStorages, selectedStorageIndex) {
        if (availableStorages.isEmpty()) {
            0
        } else {
            selectedStorageIndex.coerceIn(0, availableStorages.lastIndex)
        }
    }
    val showLoadingState = remember(
        directoryChildren,
        isLoading,
        isPriming,
        isReady,
        isCurrentDirectoryResolved
    ) {
        directoryChildren.isEmpty() && (
            isLoading || isPriming || !isReady || !isCurrentDirectoryResolved
        )
    }
    // stringResource is @Composable, so resolve outside the remember{} lambdas and key on them.
    val preparingFoldersText = stringResource(R.string.presentation_batch_g_file_explorer_loading_preparing)
    val loadingFoldersText = stringResource(R.string.presentation_batch_g_file_explorer_loading)
    val loadingFoldersHintText = stringResource(R.string.presentation_batch_g_file_explorer_loading_hint)
    val loadingMessage = remember(isPriming, isReady, preparingFoldersText, loadingFoldersText) {
        if (isPriming || !isReady) {
            preparingFoldersText
        } else {
            loadingFoldersText
        }
    }
    val loadingHint = remember(isPriming, isReady, loadingFoldersHintText) {
        if (isPriming || !isReady) {
            loadingFoldersHintText
        } else {
            null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontFamily = RoundedSans,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 22.sp,
//                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_close)
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 6.dp),
                        onClick = onRefresh,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_refresh)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                modifier = Modifier.padding(end = 12.dp),
                onClick = onDone,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_done),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = stringResource(R.string.presentation_batch_g_file_explorer_done))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.2.dp)
        ) {
            // Only show storage tabs if there's more than one storage
            if (availableStorages.size > 1) {
                PrimaryTabRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    selectedTabIndex = safeSelectedStorageIndex,
                    containerColor = Color.Transparent,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                selectedTabIndex = safeSelectedStorageIndex,
                                matchContentSize = true
                            ),
                            height = 3.dp,
                            color = Color.Transparent
                        )
                    },
                    divider = {}
                ) {
                    availableStorages.forEachIndexed { index, storage ->
                        TabAnimation(
                            index = index,
                            title = storage.displayName,
                            selectedIndex = safeSelectedStorageIndex,
                            onClick = { onStorageSelected(index) }
                        ) {
                            Text(
                                text = storage.displayName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                fontFamily = RoundedSans
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.presentation_batch_g_file_explorer_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .padding(horizontal = 18.dp)
            )

            FileExplorerHeader(
                modifier = Modifier.padding(horizontal = 18.dp),
                currentPath = currentPath,
                rootDirectory = rootDirectory,
                isAtRoot = isAtRoot,
                onNavigateUp = onNavigateUp,
                onNavigateHome = onNavigateHome,
                onNavigateTo = onNavigateTo,
                navigationEnabled = true
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp)
            ) {
                AnimatedContent(
                    targetState = Triple(
                        currentPath,
                        directoryChildren,
                        listOf(isLoading, isPriming, isReady, isCurrentDirectoryResolved)
                    ),
                    label = "directory_content",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(200))
                    }
                ) { (_, children, _) ->
                    when {
                        showLoadingState -> ExplorerLoadingState(
                            message = loadingMessage,
                            supportingText = loadingHint
                        )

                        children.isEmpty() -> ExplorerEmptyState(text = stringResource(R.string.presentation_batch_g_file_explorer_empty_folders))

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Padding horizontal moved to parent Box
                                    .clip(
                                        RoundedCornerShape(
                                            topEnd = 20.dp,
                                            topStart = 20.dp
                                        )
                                    ),
                                contentPadding = PaddingValues(
                                    bottom = 24.dp,
                                    end = if (listState.canScrollForward || listState.canScrollBackward) 24.dp else 0.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                state = listState
                            ) {
                                items(children, key = { it.file.absolutePath }) { directoryEntry ->
                                    val displayCount = directoryEntry.totalAudioCount

                                    FileExplorerItem(
                                        file = directoryEntry.file,
                                        audioCount = displayCount,
                                        displayName = directoryEntry.displayName,
                                        isBlocked = directoryEntry.isBlocked,
                                        onNavigate = { onNavigateTo(directoryEntry.file) },
                                        onToggleAllowed = { onToggleAllowed(directoryEntry.file) },
                                        navigationEnabled = true
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(76.dp)) }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
                
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = 6.dp, 
                            bottom = 88.dp // Match bottom content padding roughly (24) + FAB height (56) + spacing (8)
                        )
                )
            }
        }
    }
}

@Composable
private fun ExplorerEmptyState(
    text: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExplorerLoadingState(
    message: String,
    supportingText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (supportingText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FileExplorerItem(
    file: File,
    audioCount: Int,
    displayName: String?,
    isBlocked: Boolean,
    onNavigate: () -> Unit,
    onToggleAllowed: () -> Unit,
    navigationEnabled: Boolean
) {
    val shape = RoundedCornerShape(18.dp)

    val isAllowed = !isBlocked

    val containerColor = if (isBlocked) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isBlocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val badgeColor = if (isBlocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = navigationEnabled) { onNavigate() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = contentColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: file.name.ifEmpty { file.path },
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )
            Spacer(
                modifier = Modifier
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        audioCount < 0 -> stringResource(R.string.presentation_batch_g_file_explorer_count_scanning)
                        audioCount > 99 -> stringResource(R.string.presentation_batch_g_file_explorer_count_99plus)
                        else -> pluralStringResource(
                            R.plurals.presentation_batch_g_file_explorer_song_count,
                            audioCount,
                            audioCount
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (navigationEnabled) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = contentColor
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isBlocked) stringResource(R.string.presentation_batch_g_file_explorer_badge_excluded) else stringResource(R.string.presentation_batch_g_file_explorer_badge_included),
                style = MaterialTheme.typography.labelMedium,
                color = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            RadioButton(
                selected = isBlocked,
                onClick = onToggleAllowed,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.onErrorContainer,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExplorerHeader(
    modifier: Modifier,
    currentPath: File,
    rootDirectory: File,
    isAtRoot: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateTo: (File) -> Unit,
    navigationEnabled: Boolean
) {
    // 1. Switch ScrollState for LazyListState to better handle the items and automatic scrolling
    val listState = rememberLazyListState()

    val breadcrumbs by remember(currentPath, rootDirectory) {
        mutableStateOf(buildList {
            var cursor: File? = currentPath
            val rootPath = rootDirectory.path
            while (cursor != null) {
                add(cursor)
                if (cursor.path == rootPath) break
                cursor = cursor.parentFile
            }
            reverse()
        })
    }

    val internalStorageLabel = stringResource(R.string.presentation_batch_g_file_explorer_internal_storage)
    val rootLabel = remember(rootDirectory, internalStorageLabel) {
        when (rootDirectory.name) {
            "0", "" -> internalStorageLabel
            else -> rootDirectory.name
        }
    }

    // 2. Logic to detect whether there is content hidden on the sides
    val showStartFade by remember { derivedStateOf { listState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { listState.canScrollForward } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // "Back" button (Back Arrow) - Stays the same outside the scroll
            if (!isAtRoot && navigationEnabled) {
                IconButton(
                    onClick = onNavigateUp,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_navigate_up),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!isAtRoot) {
                // 3. Auto-scroll to the end when the path changes
                LaunchedEffect(breadcrumbs.size) {
                    if (breadcrumbs.isNotEmpty()) {
                        listState.animateScrollToItem(breadcrumbs.lastIndex)
                    }
                }

                // 4. Replace the Row + horizontalScroll with LazyRow plus the graphic effect
                LazyRow(
                    state = listState,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        // APPLYING THE GRADIENT EFFECT
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidth = 24.dp.toPx()

                            // Left Fade
                            if (showStartFade) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        endX = gradientWidth
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }

                            // Right Fade
                            if (showEndFade) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = size.width - gradientWidth
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    // Initial spacer so the first item isn't stuck to the edge or under the fade
                    item { Spacer(modifier = Modifier.width(4.dp)) }

                    items(breadcrumbs.size, key = { breadcrumbs[it].path }) { index ->
                        val file = breadcrumbs[index]
                        val isRoot = file.path == rootDirectory.path
                        val isLast = index == breadcrumbs.lastIndex
                        val label = if (isRoot) rootLabel else file.name.ifEmpty { file.path }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Chip design (Keep your original visual style)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !isLast && navigationEnabled) {
                                        if (isRoot) onNavigateHome() else onNavigateTo(file)
                                    }
                                    .background(
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (isRoot) {
                                    Icon(
                                        imageVector = Icons.Rounded.Home,
                                        contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_go_root),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(16.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isLast) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Separator (Chevron)
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, end = 2.dp)
                                )
                            }
                        }
                    }

                    // Final spacer to give the last element some breathing room
                    item { Spacer(modifier = Modifier.width(12.dp)) }
                }
            }
        }
    }
}
