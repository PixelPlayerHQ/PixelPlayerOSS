package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.preferences.LyricsWordHighlightStyle
import com.lostf1sh.pixelplayeross.data.preferences.MAX_LYRICS_FONT_SIZE_SP
import com.lostf1sh.pixelplayeross.data.preferences.MIN_LYRICS_FONT_SIZE_SP
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.settings.search.LocalSettingsHighlightTarget
import com.lostf1sh.pixelplayeross.presentation.settings.search.settingHighlight
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_LYRICS_BLUR_STRENGTH = 0.1f
private const val MAX_LYRICS_BLUR_STRENGTH = 2f

@Composable
fun LyricsSettingsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    highlightKey: String? = null
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var fontSizeDraft by remember(uiState.lyricsFontSizeSp) { mutableFloatStateOf(uiState.lyricsFontSizeSp) }
    var blurStrengthDraft by remember(uiState.animatedLyricsBlurStrength) {
        mutableFloatStateOf(uiState.animatedLyricsBlurStrength)
    }
    var showClearLyricsDialog by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }

    LaunchedEffect(minTopBarHeightPx, maxTopBarHeightPx) {
        val clamped = topBarHeight.value.coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
        if (clamped != topBarHeight.value) topBarHeight.snapTo(clamped)
    }
    val collapseFraction = 1f - (
        (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
        ).coerceIn(0f, 1f)

    val nestedScrollConnection = remember(minTopBarHeightPx, maxTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val scrollingUp = available.y < 0f
                if (!scrollingUp && (
                        lazyListState.firstVisibleItemIndex > 0 ||
                            lazyListState.firstVisibleItemScrollOffset > 0
                        )
                ) return Offset.Zero

                val previous = topBarHeight.value
                val next = (previous + available.y).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = next - previous
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(next) }
                }
                return if (scrollingUp && next == minTopBarHeightPx) Offset.Zero else Offset(0f, consumed)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val canExpand = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
            val expand = canExpand && topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2f
            topBarHeight.animateTo(
                targetValue = if (expand) maxTopBarHeightPx else minTopBarHeightPx,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        }
    }

    // The rows live in separate lazy items, so bring the target's item into composition
    // first; the row then scrolls itself clear of the bars. Skipped once the highlight was
    // consumed, so returning here or rotating does not scroll again.
    val highlightPending = LocalSettingsHighlightTarget.current != null
    LaunchedEffect(highlightKey) {
        if (highlightPending && !highlightKey.isNullOrBlank()) {
            delay(200)
            val targetIndex = when (highlightKey) {
                "item_lyrics_source_priority", "item_lyrics_reset_imported" -> 1
                else -> 0
            }
            lazyListState.animateScrollToItem(targetIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        val currentTopBarHeight = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeight + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            item(key = "lyrics_readability") {
                LyricsSettingsSubsection(title = stringResource(R.string.setcat_lyrics_readability_section)) {
                    LyricsSliderItem(
                        title = stringResource(R.string.setcat_lyrics_font_size_title),
                        subtitle = stringResource(R.string.setcat_lyrics_font_size_subtitle),
                        valueText = stringResource(R.string.setcat_lyrics_font_size_value, fontSizeDraft),
                        icon = Icons.Rounded.FormatSize,
                        value = fontSizeDraft,
                        valueRange = MIN_LYRICS_FONT_SIZE_SP..MAX_LYRICS_FONT_SIZE_SP,
                        steps = (MAX_LYRICS_FONT_SIZE_SP - MIN_LYRICS_FONT_SIZE_SP).toInt() - 1,
                        onValueChange = { fontSizeDraft = it },
                        onValueChangeFinished = { settingsViewModel.setLyricsFontSize(fontSizeDraft) },
                        modifier = Modifier.settingHighlight("item_lyrics_font_size", highlightKey)
                    )

                    ThemeSelectorItem(
                        label = stringResource(R.string.lyrics_highlight_style_title),
                        description = stringResource(R.string.lyrics_highlight_style_subtitle),
                        options = mapOf(
                            LyricsWordHighlightStyle.WIPE.storageValue to
                                stringResource(R.string.lyrics_highlight_style_wipe),
                            LyricsWordHighlightStyle.FADE.storageValue to
                                stringResource(R.string.lyrics_highlight_style_fade),
                            LyricsWordHighlightStyle.CLASSIC.storageValue to
                                stringResource(R.string.lyrics_highlight_style_classic)
                        ),
                        optionDescriptions = mapOf(
                            LyricsWordHighlightStyle.WIPE.storageValue to
                                stringResource(R.string.lyrics_highlight_style_wipe_desc),
                            LyricsWordHighlightStyle.FADE.storageValue to
                                stringResource(R.string.lyrics_highlight_style_fade_desc),
                            LyricsWordHighlightStyle.CLASSIC.storageValue to
                                stringResource(R.string.lyrics_highlight_style_classic_desc)
                        ),
                        selectedKey = uiState.lyricsWordHighlightStyle.storageValue,
                        onSelectionChanged = { key ->
                            settingsViewModel.setLyricsWordHighlightStyle(LyricsWordHighlightStyle.fromStorage(key))
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Gradient, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_word_highlight", highlightKey)
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.lyrics_duet_layout_title),
                        subtitle = stringResource(R.string.lyrics_duet_layout_subtitle),
                        checked = uiState.lyricsDuetLayoutEnabled,
                        onCheckedChange = settingsViewModel::setLyricsDuetLayoutEnabled,
                        leadingIcon = {
                            Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_duet_layout", highlightKey)
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.lyrics_background_vocals_title),
                        subtitle = stringResource(R.string.lyrics_background_vocals_subtitle),
                        checked = uiState.showLyricsBackgroundVocals,
                        onCheckedChange = settingsViewModel::setShowLyricsBackgroundVocals,
                        leadingIcon = {
                            Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_background_vocals", highlightKey)
                    )

                    val blurAvailable = uiState.useAnimatedLyrics
                    SwitchSettingItem(
                        title = stringResource(R.string.presentation_batch_f_exp_lyric_blur_title),
                        subtitle = if (blurAvailable) {
                            stringResource(R.string.presentation_batch_f_exp_lyric_blur_subtitle)
                        } else {
                            stringResource(R.string.setcat_lyrics_blur_requires_animated)
                        },
                        checked = uiState.animatedLyricsBlurEnabled,
                        onCheckedChange = settingsViewModel::setAnimatedLyricsBlurEnabled,
                        enabled = blurAvailable,
                        leadingIcon = {
                            Icon(Icons.Rounded.BlurOn, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_animated_blur", highlightKey)
                    )

                    AnimatedVisibility(
                        visible = blurAvailable && uiState.animatedLyricsBlurEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        LyricsSliderItem(
                            title = stringResource(R.string.presentation_batch_f_exp_blur_strength),
                            subtitle = stringResource(R.string.presentation_batch_f_exp_blur_strength_subtitle),
                            valueText = stringResource(
                                R.string.presentation_batch_f_exp_blur_strength_value,
                                blurStrengthDraft
                            ),
                            icon = Icons.Outlined.LinearScale,
                            value = blurStrengthDraft,
                            valueRange = MIN_LYRICS_BLUR_STRENGTH..MAX_LYRICS_BLUR_STRENGTH,
                            steps = 18,
                            onValueChange = { blurStrengthDraft = it },
                            onValueChangeFinished = {
                                settingsViewModel.setAnimatedLyricsBlurStrength(blurStrengthDraft)
                            }
                        )
                    }
                }
            }

            item(key = "lyrics_management") {
                LyricsSettingsSubsection(
                    title = stringResource(R.string.setcat_lyrics_management),
                    addBottomSpace = false
                ) {
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_lyrics_source_priority_label),
                        description = stringResource(R.string.setcat_lyrics_source_priority_desc),
                        options = mapOf(
                            LyricsSourcePreference.EMBEDDED_FIRST.name to stringResource(R.string.setcat_lyrics_embedded_first),
                            LyricsSourcePreference.API_FIRST.name to stringResource(R.string.setcat_lyrics_online_first),
                            LyricsSourcePreference.LOCAL_FIRST.name to stringResource(R.string.setcat_lyrics_local_first)
                        ),
                        selectedKey = uiState.lyricsSourcePreference.name,
                        onSelectionChanged = { key ->
                            settingsViewModel.setLyricsSourcePreference(LyricsSourcePreference.fromName(key))
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.rounded_lyrics_24),
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_source_priority", highlightKey)
                    )
                    SettingsItem(
                        title = stringResource(R.string.setcat_reset_imported_lyrics_title),
                        subtitle = stringResource(R.string.setcat_reset_imported_lyrics_subtitle),
                        leadingIcon = {
                            Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_lyrics_reset_imported", highlightKey),
                        onClick = { showClearLyricsDialog = true }
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.setcat_lyrics_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeight,
            onBackClick = { navController.popBackStack() }
        )
    }

    if (showClearLyricsDialog) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null) },
            title = { Text(stringResource(R.string.dialog_reset_lyrics_title)) },
            text = { Text(stringResource(R.string.dialog_cannot_undo)) },
            onDismissRequest = { showClearLyricsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLyricsDialog = false
                        playerViewModel.resetAllLyrics()
                    }
                ) {
                    Text(stringResource(R.string.confirm), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLyricsDialog = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}

@Composable
private fun LyricsSettingsSubsection(
    title: String,
    addBottomSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
    if (addBottomSpace) {
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun LyricsSliderItem(
    title: String,
    subtitle: String,
    valueText: String,
    icon: ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}
