package com.lostf1sh.pixelplayeross.presentation.jellyfin.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.JellyfinPlaylistEntity
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinLibrary
import com.lostf1sh.pixelplayeross.data.jellyfin.selectedJellyfinLibraryIds
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.CloudLibrarySelectorChoice
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyfinDashboardScreen(
    viewModel: JellyfinDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val librariesLoadFailed by viewModel.librariesLoadFailed.collectAsStateWithLifecycle()
    val selectedLibraryIds by viewModel.selectedLibraryIds.collectAsStateWithLifecycle()
    val librarySelectionNeedsSync by viewModel.librarySelectionNeedsSync.collectAsStateWithLifecycle()
    val syncFavorites by viewModel.syncFavorites.collectAsStateWithLifecycle()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.auth_jellyfin_title),
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                actions = {}
            )
        }
    ) { paddingValues ->
        JellyfinDashboardContent(
            playlists = playlists,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            username = viewModel.username,
            libraries = libraries,
            librariesLoadFailed = librariesLoadFailed,
            selectedLibraryIds = selectedLibraryIds,
            librarySelectionNeedsSync = librarySelectionNeedsSync,
            syncFavorites = syncFavorites,
            onToggleSyncFavorites = { viewModel.setSyncFavorites(it) },
            onSelectLibraries = { viewModel.setSelectedLibraryIds(it) },
            onSyncAll = { viewModel.syncAllPlaylistsAndSongs() },
            onSyncPlaylist = { viewModel.syncPlaylistSongs(it) },
            onDeletePlaylist = { viewModel.deletePlaylist(it) },
            onLoadPlaylistSongs = { viewModel.loadPlaylistSongs(it) },
            onLogout = {
                viewModel.logout()
                onBack()
            },
            cardShape = cardShape,
            paddingValues = paddingValues
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JellyfinDashboardContent(
    playlists: ImmutableList<JellyfinPlaylistEntity>,
    isSyncing: Boolean,
    syncMessage: String?,
    username: String?,
    libraries: ImmutableList<JellyfinLibrary>,
    librariesLoadFailed: Boolean,
    selectedLibraryIds: Set<String>,
    librarySelectionNeedsSync: Boolean,
    syncFavorites: Boolean,
    onToggleSyncFavorites: (Boolean) -> Unit,
    onSelectLibraries: (Set<String>) -> Unit,
    onSyncAll: () -> Unit,
    onSyncPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onLoadPlaylistSongs: (String) -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        AnimatedVisibility(
            visible = syncMessage != null,
            enter = slideInVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            syncMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("failed"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncing) {
                            LoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = RoundedSans
                        )
                    }
                }
            }
        }

        username?.let { name ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00A4DC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_jellyfin),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.dash_playlists_synced_count, playlists.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = RoundedSans,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        JellyfinMenuCard(
            isSyncing = isSyncing,
            libraries = libraries,
            librariesLoadFailed = librariesLoadFailed,
            selectedLibraryIds = selectedLibraryIds,
            librarySelectionNeedsSync = librarySelectionNeedsSync,
            syncFavorites = syncFavorites,
            onToggleSyncFavorites = onToggleSyncFavorites,
            onSelectLibraries = onSelectLibraries,
            onSyncAll = onSyncAll,
            onLogout = onLogout,
            cardShape = cardShape
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dash_title_playlists),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold
            )
            if (playlists.isEmpty()) {
                TextButton(onClick = onSyncAll) {
                    Icon(
                        Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dash_action_sync), fontFamily = RoundedSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (playlists.isEmpty() && !isSyncing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_jellyfin),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.dash_playlists_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.dash_playlists_empty_hint_jellyfin),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    JellyfinPlaylistCard(
                        playlist = playlist,
                        onSyncClick = { onSyncPlaylist(playlist.id) },
                        onDeleteClick = { onDeletePlaylist(playlist.id) },
                        onClick = { onLoadPlaylistSongs(playlist.id) },
                        cardShape = cardShape,
                        isSyncing = isSyncing
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JellyfinMenuCard(
    isSyncing: Boolean,
    libraries: ImmutableList<JellyfinLibrary>,
    librariesLoadFailed: Boolean,
    selectedLibraryIds: Set<String>,
    librarySelectionNeedsSync: Boolean,
    syncFavorites: Boolean,
    onToggleSyncFavorites: (Boolean) -> Unit,
    onSelectLibraries: (Set<String>) -> Unit,
    onSyncAll: () -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape
) {
    var showLibrarySelector by remember { mutableStateOf(false) }
    val musicLibraries = remember(libraries) {
        libraries.filter { it.isMusic }.toImmutableList()
    }
    val effectiveSelectedIds = selectedJellyfinLibraryIds(musicLibraries, selectedLibraryIds)
    val selectedLibraryNames = remember(musicLibraries, effectiveSelectedIds) {
        musicLibraries.filter { it.id in effectiveSelectedIds }.map { it.name }.toImmutableList()
    }
    val librarySummary = when {
        librariesLoadFailed -> stringResource(R.string.dash_libraries_load_failed)
        musicLibraries.isEmpty() -> stringResource(R.string.dash_libraries_all)
        effectiveSelectedIds.size == musicLibraries.size -> stringResource(R.string.dash_libraries_all)
        else -> stringResource(
            R.string.dash_libraries_selected_count,
            effectiveSelectedIds.size,
            musicLibraries.size
        )
    }

    if (showLibrarySelector) {
        JellyfinLibrarySelectorSheet(
            libraries = libraries,
            musicLibraries = musicLibraries,
            selectedLibraryIds = selectedLibraryIds,
            onDismiss = { showLibrarySelector = false },
            onSelectionChange = onSelectLibraries
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.dash_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dash_quick_actions_jellyfin_subtitle),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = RoundedSans,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            JellyfinLibrarySummaryPanel(
                librarySummary = librarySummary,
                selectedLibraryNames = selectedLibraryNames,
                selectedCount = effectiveSelectedIds.size,
                totalCount = musicLibraries.size,
                loadFailed = librariesLoadFailed,
                needsSync = librarySelectionNeedsSync,
                enabled = !isSyncing && musicLibraries.size > 1,
                onClick = { showLibrarySelector = true }
            )
            Spacer(modifier = Modifier.height(10.dp))
            JellyfinFavoritesSyncPanel(
                checked = syncFavorites,
                enabled = !isSyncing,
                onCheckedChange = onToggleSyncFavorites
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onSyncAll,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isSyncing) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dash_status_syncing), fontFamily = RoundedSans)
                    } else {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dash_action_sync_library), fontFamily = RoundedSans)
                    }
                }

                FilledTonalButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dash_action_disconnect), fontFamily = RoundedSans)
                }
            }
        }
    }
}

@Composable
private fun JellyfinFavoritesSyncPanel(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dash_jellyfin_sync_favorites_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.dash_jellyfin_sync_favorites_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RoundedSans,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun JellyfinLibrarySummaryPanel(
    librarySummary: String,
    selectedLibraryNames: ImmutableList<String>,
    selectedCount: Int,
    totalCount: Int,
    loadFailed: Boolean,
    needsSync: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (needsSync) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val iconContainerColor = if (loadFailed) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconColor = if (loadFailed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = if (needsSync) 3.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (loadFailed) Icons.Rounded.Warning else Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dash_libraries_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = librarySummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (enabled) {
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                stringResource(R.string.dash_libraries_change),
                                fontFamily = RoundedSans
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }

            if (needsSync || selectedLibraryNames.isNotEmpty() || totalCount > 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (needsSync) {
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    stringResource(R.string.dash_libraries_sync_needed),
                                    fontFamily = RoundedSans
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(SuggestionChipDefaults.IconSize)
                                )
                            }
                        )
                    }

                    if (totalCount > 0) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    stringResource(
                                        R.string.dash_libraries_selected_count,
                                        selectedCount,
                                        totalCount
                                    ),
                                    fontFamily = RoundedSans
                                )
                            }
                        )
                    }

                    selectedLibraryNames.take(3).forEach { libraryName ->
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(
                                    libraryName,
                                    fontFamily = RoundedSans,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        )
                    }

                    val hiddenCount = (selectedLibraryNames.size - 3).coerceAtLeast(0)
                    if (hiddenCount > 0) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    stringResource(R.string.dash_libraries_more_count, hiddenCount),
                                    fontFamily = RoundedSans
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JellyfinLibrarySelectorSheet(
    libraries: ImmutableList<JellyfinLibrary>,
    musicLibraries: ImmutableList<JellyfinLibrary>,
    selectedLibraryIds: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val availableMusicIds = remember(musicLibraries) { musicLibraries.map { it.id }.toSet() }
    val effectiveSelectedIds = selectedJellyfinLibraryIds(musicLibraries, selectedLibraryIds)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.dash_libraries_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dash_libraries_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = RoundedSans,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            CloudLibrarySelectorChoice(
                icon = Icons.Rounded.SelectAll,
                title = stringResource(R.string.dash_libraries_all),
                subtitle = stringResource(R.string.dash_libraries_all_subtitle),
                checked = effectiveSelectedIds.size == musicLibraries.size,
                enabled = true,
                onClick = { onSelectionChange(availableMusicIds) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            libraries.forEach { library ->
                if (library.isMusic) {
                    CloudLibrarySelectorChoice(
                        icon = Icons.Rounded.LibraryMusic,
                        title = library.name,
                        subtitle = stringResource(R.string.dash_libraries_folder_subtitle),
                        checked = library.id in effectiveSelectedIds,
                        enabled = true,
                        onClick = {
                            val nextIds = if (library.id in effectiveSelectedIds) {
                                effectiveSelectedIds - library.id
                            } else {
                                effectiveSelectedIds + library.id
                            }
                            onSelectionChange(nextIds)
                        }
                    )
                } else {
                    CloudLibrarySelectorChoice(
                        icon = Icons.Rounded.Folder,
                        title = library.name,
                        subtitle = stringResource(R.string.dash_libraries_not_music),
                        checked = false,
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinPlaylistCard(
    playlist: JellyfinPlaylistEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dash_song_count, playlist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RoundedSans,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalIconButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = stringResource(R.string.cd_sync),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            FilledTonalIconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete_action),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
