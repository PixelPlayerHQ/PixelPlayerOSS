@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.library.LibraryTabId
import com.lostf1sh.pixelplayeross.presentation.utils.LocalAppHapticsConfig
import com.lostf1sh.pixelplayeross.presentation.utils.performAppCompatHapticFeedback
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The stored order *is* the list of visible tabs, so every known tab missing from it is hidden.
 */
internal fun hiddenLibraryTabs(visibleTabs: List<String>): List<LibraryTabId> =
    LibraryTabId.entries.filter { it.stableKey !in visibleTabs }

/**
 * Moves the visible tab keyed [fromKey] to the position of the one keyed [toKey].
 *
 * The editor list also carries section headers and hidden-tab rows, so list item indices are not
 * positions in [tabs]; both ends are resolved by key instead. Returns null, meaning "ignore this
 * move", when either key is not a visible tab or both point at the same tab.
 */
internal fun moveTabByKey(tabs: List<String>, fromKey: Any?, toKey: Any?): List<String>? {
    val fromIndex = tabs.indexOf(fromKey as? String ?: return null)
    val toIndex = tabs.indexOf(toKey as? String ?: return null)
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
    return tabs.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderTabsSheet(
    tabs: ImmutableList<String>,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var localTabs by remember { mutableStateOf(tabs) }

    LaunchedEffect(tabs) {
        localTabs = tabs
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reorder_tabs_reset_dialog_title)) },
            text = { Text(stringResource(R.string.reorder_tabs_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        localTabs = tabs
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val moved = moveTabByKey(localTabs, from.key, to.key)
                ?: return@rememberReorderableLazyListState
            localTabs = moved.toImmutableList()
            performAppCompatHapticFeedback(
                view,
                appHapticsConfig,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        },
        lazyListState = listState
    )
    var isLoading by remember { mutableStateOf(false) }

    val hiddenTabs = remember(localTabs) { hiddenLibraryTabs(localTabs) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.reorder_tabs_sheet_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                }
            },
            floatingActionButton = {
                FloatingToolBar(
                    modifier = Modifier,
                    onReset = { showResetDialog = true },
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(700)
                            onReorder(localTabs)
                            isLoading = false
                            onDismiss()
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContainedLoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reorder_tabs_reordering))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        // Leaves room for the last hidden row above the floating toolbar.
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "visible_header") {
                            TabsEditorSectionHeader(
                                title = stringResource(R.string.reorder_tabs_visible_title),
                                count = localTabs.size
                            )
                        }

                        items(localTabs, key = { it }) { tab ->
                            ReorderableItem(reorderableState, key = tab) { isDragging ->
                                VisibleTabRow(
                                    tabId = LibraryTabId.fromStableKey(tab),
                                    fallbackLabel = tab,
                                    isDragging = isDragging,
                                    // The library needs at least one page to show.
                                    canHide = localTabs.size > 1,
                                    dragHandle = {
                                        FilledIconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstants.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstants.GESTURE_END
                                                        )
                                                    }
                                                )
                                                .size(44.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = stringResource(R.string.cd_drag_handle)
                                            )
                                        }
                                    },
                                    onHide = {
                                        if (localTabs.size > 1) {
                                            localTabs = (localTabs - tab).toImmutableList()
                                        }
                                    }
                                )
                            }
                        }

                        if (hiddenTabs.isNotEmpty()) {
                            item(key = "hidden_header") {
                                TabsEditorSectionHeader(
                                    title = stringResource(R.string.reorder_tabs_hidden_title),
                                    count = hiddenTabs.size
                                )
                            }
                            items(hiddenTabs, key = { "hidden_${it.stableKey}" }) { tabId ->
                                HiddenTabRow(
                                    tabId = tabId,
                                    onShow = {
                                        localTabs = (localTabs + tabId.stableKey).toImmutableList()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Header for the visible and hidden groups, with the number of tabs in each. */
@Composable
private fun TabsEditorSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** A tab that shows in the library: drag to reorder, or hide it. */
@Composable
private fun VisibleTabRow(
    tabId: LibraryTabId?,
    fallbackLabel: String,
    isDragging: Boolean,
    canHide: Boolean,
    dragHandle: @Composable () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDragging) .88f else 1f),
        shadowElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            dragHandle()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (tabId != null) {
                    Icon(
                        painter = painterResource(id = tabId.iconRes()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                text = tabId?.let { stringResource(it.labelRes) } ?: fallbackLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick = onHide,
                enabled = canHide,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
                )
            ) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.reorder_tabs_hide_action))
            }
        }
    }
}

/** A tab the user has hidden: tap to bring it back at the end of the visible list. */
@Composable
private fun HiddenTabRow(
    tabId: LibraryTabId,
    onShow: () -> Unit
) {
    Surface(
        onClick = onShow,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = tabId.iconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = stringResource(tabId.labelRes),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick = onShow,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.reorder_tabs_show_action))
            }
        }
    }
}

@DrawableRes
private fun LibraryTabId.iconRes(): Int = when (this) {
    LibraryTabId.Songs -> R.drawable.rounded_music_note_24
    LibraryTabId.Albums -> R.drawable.rounded_album_24
    LibraryTabId.Artists -> R.drawable.rounded_artist_24
    LibraryTabId.Playlists -> R.drawable.rounded_playlist_play_24
    LibraryTabId.Folders -> R.drawable.rounded_folder_24
    LibraryTabId.Liked -> R.drawable.round_favorite_24
    LibraryTabId.Genres -> R.drawable.rounded_category_24
}

@Composable
fun FloatingToolBar(
    modifier: Modifier,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
){
    val backgroundShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 22.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = 22.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 22.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 22.dp,
        smoothnessAsPercentBR = 60
    )
    Box(
        modifier = modifier
            .padding(8.dp)
            .background(
                shape = backgroundShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    ){
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.align(Alignment.CenterVertically),
                onClick = onReset
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_restart_alt_24),
                    contentDescription = stringResource(R.string.cd_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                shape = CircleShape,
                onClick = onClick,
                icon = { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_done)) },
                text = { Text(stringResource(R.string.action_done)) }
            )
        }
    }
}