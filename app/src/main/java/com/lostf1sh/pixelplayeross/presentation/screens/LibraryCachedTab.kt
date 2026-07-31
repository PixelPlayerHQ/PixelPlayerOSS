@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.offline.CachedCollection
import com.lostf1sh.pixelplayeross.data.offline.CachedCollectionType
import com.lostf1sh.pixelplayeross.data.offline.OfflineItemStatus
import com.lostf1sh.pixelplayeross.data.offline.OfflineMediaUiState
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage

@Composable
fun LibraryCachedTab(
    state: OfflineMediaUiState,
    bottomBarHeight: Dp,
    onPlay: (CachedCollection) -> Unit,
    onRemove: (CachedCollection) -> Unit,
) {
    var filter by remember { mutableStateOf<CachedCollectionType?>(null) }
    var pendingRemoval by remember { mutableStateOf<CachedCollection?>(null) }
    val context = LocalContext.current
    val visible = remember(state.collections, filter) {
        state.collections.filter { filter == null || it.type == filter }
    }

    pendingRemoval?.let { collection ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.offline_cache_remove_title)) },
            text = { Text(stringResource(R.string.offline_cache_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(collection)
                    pendingRemoval = null
                }) { Text(stringResource(R.string.offline_cache_remove_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomBarHeight + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (state.limitBytes == 0L) {
                            stringResource(
                                R.string.offline_cache_used_unlimited,
                                Formatter.formatShortFileSize(context, state.offlineBytes),
                            )
                        } else {
                            stringResource(
                                R.string.offline_cache_used,
                                Formatter.formatShortFileSize(context, state.offlineBytes),
                                Formatter.formatShortFileSize(context, state.limitBytes),
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.limitBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { (state.offlineBytes.toFloat() / state.limitBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        stringResource(
                            R.string.offline_cache_streaming_used,
                            Formatter.formatShortFileSize(context, state.transientBytes),
                            Formatter.formatShortFileSize(context, state.transientLimitBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.offline_cache_filter_all)) },
                )
                FilterChip(
                    selected = filter == CachedCollectionType.ALBUM,
                    onClick = { filter = CachedCollectionType.ALBUM },
                    label = { Text(stringResource(R.string.offline_cache_filter_albums)) },
                )
                FilterChip(
                    selected = filter == CachedCollectionType.PLAYLIST,
                    onClick = { filter = CachedCollectionType.PLAYLIST },
                    label = { Text(stringResource(R.string.offline_cache_filter_playlists)) },
                )
                FilterChip(
                    selected = filter == CachedCollectionType.SONG,
                    onClick = { filter = CachedCollectionType.SONG },
                    label = { Text(stringResource(R.string.offline_cache_filter_songs)) },
                )
            }
        }

        if (visible.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.offline_cache_empty_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.offline_cache_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(visible, key = CachedCollection::id) { collection ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(collection) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmartImage(
                        model = collection.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(collection.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            collection.subtitle ?: pluralStringResource(
                                R.plurals.offline_cache_song_count,
                                collection.songs.size,
                                collection.songs.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            collection.statusLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (collection.status != OfflineItemStatus.COMPLETE) {
                            LinearProgressIndicator(
                                progress = { collection.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    IconButton(onClick = { onPlay(collection) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                    IconButton(onClick = { pendingRemoval = collection }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.offline_cache_remove_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedCollection.statusLabel(): String = when (status) {
    OfflineItemStatus.QUEUED -> stringResource(R.string.offline_cache_status_queued)
    OfflineItemStatus.DOWNLOADING -> stringResource(R.string.offline_cache_status_downloading, (progress * 100).toInt())
    OfflineItemStatus.COMPLETE -> stringResource(R.string.offline_cache_status_complete)
    OfflineItemStatus.PAUSED -> stringResource(R.string.offline_cache_status_paused)
    OfflineItemStatus.FAILED -> stringResource(R.string.offline_cache_status_failed)
}
