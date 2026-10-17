package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.presentation.components.ToggleSegmentButton
import com.lostf1sh.pixelplayeross.presentation.components.player.BottomToggleRow
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onSaveLyricsAsLrc: () -> Unit,
    onTranslateViaAi: () -> Unit = {},
    onResetImportedLyrics: () -> Unit,
    onToggleSyncControls: () -> Unit,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    immersiveLyricsEnabled: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showResetDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // A tall sheet (landscape, small screens, many control rows) reaches the top of the
        // window; keep its handle and first rows below the status bar.
        contentWindowInsets = { WindowInsets.statusBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            val itemBackgroundColor = contentColor.copy(alpha = 0.08f)

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                if (lyrics != null) {
                    ListItem(
                        content = { Text(stringResource(R.string.save_lyrics_dialog_title).substringBefore("?")) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_save_24),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )

                    ListItem(
                        content = { Text(stringResource(R.string.lyrics_translate_via_ai)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onTranslateViaAi()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                val resetShape = if (lyrics != null) {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                } else {
                    RoundedCornerShape(24.dp)
                }

                ListItem(
                    content = { Text(stringResource(R.string.reset_imported_lyrics)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(resetShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            showResetDialog = true
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )
            }

            if (showResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.lyrics_more_dialog_reset_title)) },
                    text = { Text(stringResource(R.string.lyrics_more_dialog_reset_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text(stringResource(R.string.action_reset), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics_more_appearance),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                 )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(itemBackgroundColor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_more_alignment),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "left",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("left") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                            contentDesc = stringResource(R.string.cd_lyrics_align_left)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "center",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("center") },
                            imageVector = Icons.Rounded.FormatAlignCenter,
                            contentDesc = stringResource(R.string.cd_lyrics_align_center)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "right",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("right") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignRight,
                            contentDesc = stringResource(R.string.cd_lyrics_align_right)
                        )
                    }
                }
            }

            val isSyncVisible = showSyncedLyrics
            val isRomanizationVisible = hasRomanizedLyrics
            val isTranslationVisible = hasTranslatedLyrics
            val isImmersiveVisible = showSyncedLyrics && immersiveLyricsEnabled
            val isKeepScreenOnVisible = true

            if (isSyncVisible || isRomanizationVisible || isTranslationVisible || isKeepScreenOnVisible) {
                val isRomanizationFirst = isRomanizationVisible && !isSyncVisible
                val isTranslationFirst = isTranslationVisible && !isSyncVisible && !isRomanizationVisible

                val isSyncLast = isSyncVisible && !isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isRomanizationLast = isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isTranslationLast = isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isImmersiveLast = isImmersiveVisible && !isKeepScreenOnVisible
                val isKeepScreenOnFirst = isKeepScreenOnVisible && !isSyncVisible &&
                    !isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(start = 6.dp, bottom = 6.dp),
                        text = stringResource(R.string.lyrics_more_controls),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )

                    if (isSyncVisible) {
                        ListItem(
                            content = {
                                Text(
                                    if (isSyncControlsVisible) {
                                        stringResource(R.string.lyrics_more_hide_sync_controls)
                                    } else {
                                        stringResource(R.string.lyrics_more_adjust_sync)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isSyncLast) 24.dp else 8.dp,
                                        bottomEnd = if (isSyncLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable {
                                    onDismissRequest()
                                    onToggleSyncControls()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isRomanizationVisible) {
                        ListItem(
                            content = { Text(stringResource(R.string.lyrics_more_show_romanization)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Abc,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isRomanizationFirst) 18.dp else 8.dp,
                                        topEnd = if (isRomanizationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isRomanizationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isRomanizationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowRomanizationChange(!showRomanization) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isTranslationVisible) {
                        ListItem(
                            content = { Text(stringResource(R.string.lyrics_more_show_translations)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isTranslationFirst) 18.dp else 8.dp,
                                        topEnd = if (isTranslationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isTranslationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isTranslationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowTranslationChange(!showTranslation) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isImmersiveVisible) {
                        ListItem(
                            content = { Text(stringResource(R.string.lyrics_more_disable_immersive_once)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    modifier = Modifier,
                                    checked = isImmersiveTemporarilyDisabled,
                                    onCheckedChange = {
                                        onSetImmersiveTemporarilyDisabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = if (isImmersiveLast) 24.dp else 8.dp,
                                        bottomEnd = if (isImmersiveLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor),
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isKeepScreenOnVisible) {
                        ListItem(
                            content = { Text(stringResource(R.string.lyrics_more_keep_screen_on)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.BrightnessHigh,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        // Plain lyrics leave this as the only control:
                                        // round it as a standalone card, not a group tail.
                                        topStart = if (isKeepScreenOnFirst) 24.dp else 8.dp,
                                        topEnd = if (isKeepScreenOnFirst) 24.dp else 8.dp,
                                        bottomStart = 24.dp,
                                        bottomEnd = 24.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onKeepScreenOnChange(!keepScreenOn) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                 BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 20.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }

            // Bottom inset lives inside the scroll content so the last row can scroll
            // clear of the navigation bar.
            Spacer(modifier = Modifier.height(16.dp + navigationBarsPadding))
        }
    }
}
