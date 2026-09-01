package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.LyricsSearchResult
import com.lostf1sh.pixelplayeross.presentation.viewmodel.LyricsSearchUiState
import com.lostf1sh.pixelplayeross.utils.ProviderText
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList

@Composable
fun FetchLyricsDialog(
    uiState: LyricsSearchUiState,
    currentSong: Song?,
    onConfirm: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    if (uiState is LyricsSearchUiState.Success) return

    var forcePickResults by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (uiState) {
                    LyricsSearchUiState.Idle -> {
                        IdleContent(
                            currentSong = currentSong,
                            forcePickResults = forcePickResults,
                            onToggleForcePickResults = { forcePickResults = it },
                            onSearch = { onConfirm(forcePickResults) },
                            onImport = onImport,
                            onCancel = onDismiss
                        )
                    }
                    LyricsSearchUiState.Loading -> {
                        LoadingContent()
                    }
                    is LyricsSearchUiState.PickResult -> {
                        PickResultContent(
                            results = uiState.results,
                            onPickResult = onPickResult,
                            onCancel = onDismiss
                        )
                    }
                    is LyricsSearchUiState.NotFound -> {
                        NotFoundContent(
                            message = uiState.message,
                            initialTitle = currentSong?.title.orEmpty(),
                            initialArtist = currentSong?.displayArtist,
                            onManualSearch = { title, artist ->
                                onManualSearch(title, artist)
                            },
                            onCancel = onDismiss
                        )
                    }
                    is LyricsSearchUiState.Error -> {
                        ErrorContent(
                            message = uiState.message,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    currentSong: Song?,
    forcePickResults: Boolean,
    onToggleForcePickResults: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedStarShape(
                sides = 8,
                curve = 0.1,
                rotation = 0f,
            ))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    if (currentSong != null) {
        Text(
            text = currentSong.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = currentSong.displayArtist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = stringResource(R.string.lyrics_not_found),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.search_lyrics_online_prompt),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fetch_lyrics_show_options_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.fetch_lyrics_show_options_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
            Switch(
                checked = forcePickResults,
                onCheckedChange = onToggleForcePickResults,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.search), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_file))
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.searching_lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PickResultContent(
    results: ImmutableList<LyricsSearchResult>,
    onPickResult: (LyricsSearchResult) -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = stringResource(R.string.found_n_matches_format).format(results.size),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    LazyColumn(
        modifier = Modifier.heightIn(max = 350.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(results, key = { it.record.id }) { result ->
            ResultItemCard(result = result, onClick = { onPickResult(result) })
        }

        item {
            ProviderText(
                providerText = stringResource(R.string.lyrics_provided_by),
                uri = stringResource(R.string.lrclib_uri),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ResultItemCard(
    result: LyricsSearchResult,
    onClick: () -> Unit
) {
    val hasSyncedLyrics = result.record.hasSyncedContent
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasSyncedLyrics) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasSyncedLyrics) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.record.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasSyncedLyrics) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_synced_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.presentation_batch_g_list_song_artist_album,
                        result.record.artistName,
                        result.record.albumName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NotFoundContent(
    initialTitle: String,
    initialArtist: String?,
    message: String,
    onManualSearch: (String, String?) -> Unit,
    onCancel: () -> Unit
) {
    val normalizedArtist = rememberSaveable(initialArtist) {
        initialArtist
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it.equals("<unknown>", ignoreCase = true) }
            .orEmpty()
    }

    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var artist by rememberSaveable { mutableStateOf(normalizedArtist) }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.lyrics_not_found),
        style = MaterialTheme.typography.headlineSmall
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.lyrics_custom_search_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.song_field_title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.song_field_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    Spacer(Modifier.height(8.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.song_field_artist_optional),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it },
            placeholder = { Text(stringResource(R.string.song_field_artist_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    Spacer(Modifier.height(24.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = {
                onManualSearch(
                    title,
                    artist.takeIf { it.isNotBlank() }
                )
            },
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Search, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.search))
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.error),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(stringResource(R.string.ok), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
