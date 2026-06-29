package com.lostf1sh.pixelplayeross.presentation.navidrome.dashboard

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
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.NavidromePlaylistEntity
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeMusicFolder
import com.lostf1sh.pixelplayeross.data.navidrome.selectedNavidromeMusicFolderIds
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.utils.formatTimeAgo
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavidromeDashboardScreen(
    viewModel: NavidromeDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val selectedPlaylistSongs by viewModel.selectedPlaylistSongs.collectAsStateWithLifecycle()
    val selectedPlaylistName by viewModel.selectedPlaylistName.collectAsStateWithLifecycle()
    val musicFolders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val musicFoldersLoadFailed by viewModel.musicFoldersLoadFailed.collectAsStateWithLifecycle()
    val selectedMusicFolderIds by viewModel.selectedMusicFolderIds.collectAsStateWithLifecycle()
    val librarySelectionNeedsSync by viewModel.librarySelectionNeedsSync.collectAsStateWithLifecycle()

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
                        stringResource(R.string.screen_subsonic_dashboard_title),
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
        DashboardContent(
            playlists = playlists,
            isSyncing = isSyncing,
            syncProgress = syncProgress,
            syncMessage = syncMessage,
            selectedPlaylistSongs = selectedPlaylistSongs,
            selectedPlaylistName = selectedPlaylistName,
            musicFolders = musicFolders,
            musicFoldersLoadFailed = musicFoldersLoadFailed,
            selectedMusicFolderIds = selectedMusicFolderIds,
            librarySelectionNeedsSync = librarySelectionNeedsSync,
            username = viewModel.username,
            lastSyncTime = viewModel.lastSyncTime,
            onSyncAll = { viewModel.syncAllPlaylistsAndSongs() },
            onSelectMusicFolders = { viewModel.setSelectedMusicFolderIds(it) },
            onSyncPlaylist = { viewModel.syncPlaylistSongs(it) },
            onDeletePlaylist = { viewModel.deletePlaylist(it) },
            onLoadPlaylistSongs = { playlist -> viewModel.loadPlaylistSongs(playlist.id, playlist.name) },
            onLogout = {
                viewModel.logout()
                onBack()
            },
            cardShape = cardShape,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun DashboardContent(
    playlists: List<NavidromePlaylistEntity>,
    isSyncing: Boolean,
    syncProgress: Float?,
    syncMessage: String?,
    selectedPlaylistSongs: List<Song>,
    selectedPlaylistName: String?,
    musicFolders: List<NavidromeMusicFolder>,
    musicFoldersLoadFailed: Boolean,
    selectedMusicFolderIds: Set<String>,
    librarySelectionNeedsSync: Boolean,
    username: String?,
    lastSyncTime: Long,
    onSyncAll: () -> Unit,
    onSelectMusicFolders: (Set<String>) -> Unit,
    onSyncPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onLoadPlaylistSongs: (NavidromePlaylistEntity) -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Sync status banner
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSyncing && syncProgress == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = RoundedSans,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSyncing && syncProgress != null) {
                                Text(
                                    text = "${(syncProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = RoundedSans,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        if (isSyncing && syncProgress != null) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { syncProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                        }
                    }
                }
            }
        }

        // User info header
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
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_navidrome),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
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
                        Text(
                            text = "Last synced: ${formatTimeAgo(lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = RoundedSans,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        SubsonicMenuCard(
            isSyncing = isSyncing,
            musicFolders = musicFolders,
            musicFoldersLoadFailed = musicFoldersLoadFailed,
            selectedMusicFolderIds = selectedMusicFolderIds,
            librarySelectionNeedsSync = librarySelectionNeedsSync,
            onSelectMusicFolders = onSelectMusicFolders,
            onSyncAll = onSyncAll,
            onLogout = onLogout,
            cardShape = cardShape
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Playlists header
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

        // Playlist list
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
                        Icons.Rounded.CloudQueue,
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
                        text = stringResource(R.string.dash_playlists_empty_hint_subsonic),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = playlists,
                    key = { "playlist_${it.id}" }
                ) { playlist ->
                    val isLibraryFallback = playlist.id == NavidromeRepository.LIBRARY_PLAYLIST_ID
                    PlaylistCard(
                        playlist = playlist,
                        onSyncClick = { onSyncPlaylist(playlist.id) },
                        onDeleteClick = { if (!isLibraryFallback) onDeletePlaylist(playlist.id) },
                        onClick = { onLoadPlaylistSongs(playlist) },
                        cardShape = cardShape,
                        isSyncing = isSyncing,
                        canDelete = !isLibraryFallback
                    )
                }

                if (selectedPlaylistName != null && selectedPlaylistSongs.isNotEmpty()) {
                    item(key = "selected_playlist_header") {
                        Text(
                            text = selectedPlaylistName,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = selectedPlaylistSongs,
                        key = { "song_${it.id}" }
                    ) { song ->
                        SongCard(
                            song = song,
                            cardShape = cardShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubsonicMenuCard(
    isSyncing: Boolean,
    musicFolders: List<NavidromeMusicFolder>,
    musicFoldersLoadFailed: Boolean,
    selectedMusicFolderIds: Set<String>,
    librarySelectionNeedsSync: Boolean,
    onSelectMusicFolders: (Set<String>) -> Unit,
    onSyncAll: () -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape
) {
    var showLibrarySelector by remember { mutableStateOf(false) }
    val effectiveSelectedIds = selectedNavidromeMusicFolderIds(musicFolders, selectedMusicFolderIds)
    val selectedFolderNames = remember(musicFolders, effectiveSelectedIds) {
        musicFolders.filter { it.id in effectiveSelectedIds }.map { it.name }
    }
    val librarySummary = when {
        musicFoldersLoadFailed -> stringResource(R.string.dash_libraries_load_failed)
        musicFolders.isEmpty() -> stringResource(R.string.dash_libraries_all)
        effectiveSelectedIds.size == musicFolders.size -> stringResource(R.string.dash_libraries_all)
        else -> stringResource(
            R.string.dash_libraries_selected_count,
            effectiveSelectedIds.size,
            musicFolders.size
        )
    }

    if (showLibrarySelector) {
        LibrarySelectorSheet(
            musicFolders = musicFolders,
            selectedMusicFolderIds = selectedMusicFolderIds,
            onDismiss = { showLibrarySelector = false },
            onSelectionChange = onSelectMusicFolders
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
                text = stringResource(R.string.dash_quick_actions_subsonic_subtitle),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = RoundedSans,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            NavidromeLibrarySummaryPanel(
                librarySummary = librarySummary,
                selectedFolderNames = selectedFolderNames,
                selectedCount = effectiveSelectedIds.size,
                totalCount = musicFolders.size,
                loadFailed = musicFoldersLoadFailed,
                needsSync = librarySelectionNeedsSync,
                enabled = !isSyncing && musicFolders.size > 1,
                onClick = { showLibrarySelector = true }
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
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
private fun NavidromeLibrarySummaryPanel(
    librarySummary: String,
    selectedFolderNames: List<String>,
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

            if (needsSync || selectedFolderNames.isNotEmpty() || totalCount > 0) {
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

                    selectedFolderNames.take(3).forEach { folderName ->
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(
                                    folderName,
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

                    val hiddenCount = (selectedFolderNames.size - 3).coerceAtLeast(0)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibrarySelectorSheet(
    musicFolders: List<NavidromeMusicFolder>,
    selectedMusicFolderIds: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val availableIds = remember(musicFolders) { musicFolders.map { it.id }.toSet() }
    val effectiveSelectedIds = selectedNavidromeMusicFolderIds(musicFolders, selectedMusicFolderIds)

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

            LibrarySelectorChoice(
                icon = Icons.Rounded.SelectAll,
                title = stringResource(R.string.dash_libraries_all),
                subtitle = stringResource(R.string.dash_libraries_all_subtitle),
                checked = effectiveSelectedIds.size == musicFolders.size,
                onClick = { onSelectionChange(availableIds) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            musicFolders.forEach { folder ->
                LibrarySelectorChoice(
                    icon = Icons.Rounded.Folder,
                    title = folder.name,
                    subtitle = stringResource(R.string.dash_libraries_folder_subtitle),
                    checked = folder.id in effectiveSelectedIds,
                    onClick = {
                        val nextIds = if (folder.id in effectiveSelectedIds) {
                            effectiveSelectedIds - folder.id
                        } else {
                            effectiveSelectedIds + folder.id
                        }
                        onSelectionChange(nextIds)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibrarySelectorChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = if (checked) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RoundedSans,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    cardShape: AbsoluteSmoothCornerShape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Album art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUriString != null) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RoundedSans,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: NavidromePlaylistEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean,
    canDelete: Boolean
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
            // Playlist cover
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverArtId != null) {
                    SmartImage(
                        model = "navidrome_cover://${playlist.coverArtId}",
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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

            if (canDelete) {
                Spacer(Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = onDeleteClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.cd_remove),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
