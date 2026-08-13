package com.lostf1sh.pixelplayeross.presentation.components.player

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import java.util.Locale

private val BookmarkTitleWhitespaceRegex = Regex("\\s+")

@Composable
fun AddBookmarkDialog(
    currentProgressMs: Long,
    lyricTitle: String? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val formattedTime = remember(currentProgressMs) {
        val totalSeconds = currentProgressMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    val defaultTitle = remember(formattedTime) { "Bookmark @ $formattedTime" }
    val availableLyricTitle = remember(lyricTitle) { lyricTitle?.trim()?.takeIf { it.isNotBlank() } }
    var customTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var useLyricTitle by remember(availableLyricTitle) { mutableStateOf(false) }
    val resolvedTitle = if (useLyricTitle && availableLyricTitle != null) {
        availableLyricTitle
    } else {
        customTitle.trim()
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(useLyricTitle) {
        if (!useLyricTitle) {
            focusRequester.requestFocus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                text = "Save bookmark",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Saved playback point",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                if (availableLyricTitle != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useLyricTitle = !useLyricTitle },
                        shape = RoundedCornerShape(24.dp),
                        color = if (useLyricTitle) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Use lyric line",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (useLyricTitle) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = availableLyricTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (useLyricTitle) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Switch(
                                checked = useLyricTitle,
                                onCheckedChange = { useLyricTitle = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = if (useLyricTitle && availableLyricTitle != null) {
                        availableLyricTitle
                    } else {
                        customTitle
                    },
                    onValueChange = { customTitle = it },
                    enabled = !useLyricTitle,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (resolvedTitle.isNotBlank()) {
                        onSave(resolvedTitle)
                    }
                },
                enabled = resolvedTitle.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

internal fun resolveLyricBookmarkTitle(
    lyrics: Lyrics?,
    timestampMs: Long,
    lyricsSyncOffsetMs: Int
): String? {
    val syncedLines = lyrics?.synced.orEmpty()
    if (syncedLines.isEmpty()) return null

    val adjustedPositionMs = (timestampMs + lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)
    return syncedLines
        .lastOrNull { it.time.toLong() <= adjustedPositionMs }
        ?.line
        ?.trim()
        ?.replace(BookmarkTitleWhitespaceRegex, " ")
        ?.takeIf { it.isNotBlank() }
}
