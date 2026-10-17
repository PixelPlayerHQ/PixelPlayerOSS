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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var showGeminiApiKeyDialog by remember { mutableStateOf(false) }
    var showGeminiModelDialog by remember { mutableStateOf(false) }

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
                "item_gemini_lyrics_toggle", "item_gemini_api_key" -> 1
                "item_lyrics_source_priority", "item_lyrics_reset_imported" -> 2
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

            item(key = "gemini_ai_lyrics") {
                LyricsSettingsSubsection(
                    title = stringResource(R.string.setcat_gemini_ai_lyrics_title)
                ) {
                    val hasApiKey = !uiState.geminiApiKey.isNullOrBlank()
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_gemini_lyrics_toggle_title),
                        subtitle = if (hasApiKey) {
                            stringResource(R.string.setcat_gemini_lyrics_toggle_subtitle)
                        } else {
                            stringResource(R.string.setcat_gemini_lyrics_toggle_subtitle_requires_key)
                        },
                        checked = uiState.geminiLyricsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasApiKey) {
                                showGeminiApiKeyDialog = true
                            } else {
                                settingsViewModel.setGeminiLyricsEnabled(enabled)
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_gemini_lyrics_toggle", highlightKey)
                    )

                    val keySubtitle = if (hasApiKey) {
                        val key = uiState.geminiApiKey.orEmpty()
                        val last4 = if (key.length > 4) key.takeLast(4) else key
                        stringResource(R.string.setcat_gemini_api_key_configured, last4)
                    } else {
                        stringResource(R.string.setcat_gemini_api_key_not_configured)
                    }

                    SettingsItem(
                        title = stringResource(R.string.setcat_gemini_api_key_title),
                        subtitle = keySubtitle,
                        leadingIcon = {
                            Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_gemini_api_key", highlightKey),
                        onClick = {
                            showGeminiApiKeyDialog = true
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.setcat_gemini_model_title),
                        subtitle = uiState.geminiModel,
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier.settingHighlight("item_gemini_model", highlightKey),
                        onClick = {
                            showGeminiModelDialog = true
                        }
                    )
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

    if (showGeminiApiKeyDialog) {
        var apiKeyText by remember { mutableStateOf(uiState.geminiApiKey.orEmpty()) }
        var isPasswordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            icon = { Icon(Icons.Outlined.Key, null) },
            title = { Text(stringResource(R.string.dialog_gemini_api_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_gemini_api_key_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.dialog_gemini_api_key_hint)) },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            },
            onDismissRequest = { showGeminiApiKeyDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = apiKeyText.trim()
                        settingsViewModel.setGeminiApiKey(trimmed.ifEmpty { null })
                        if (trimmed.isEmpty()) {
                            settingsViewModel.setGeminiLyricsEnabled(false)
                        } else {
                            settingsViewModel.setGeminiLyricsEnabled(true)
                        }
                        showGeminiApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiApiKeyDialog = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showGeminiModelDialog) {
        val presetModels = remember {
            listOf(
                "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (Fastest)",
                "gemini-3.5-flash" to "Gemini 3.5 Flash (Balanced)",
                "gemini-flash-lite-latest" to "Gemini Flash Lite Latest",
                "gemini-flash-latest" to "Gemini Flash Latest",
                "gemini-2.5-flash" to "Gemini 2.5 Flash",
                "gemini-2.5-pro" to "Gemini 2.5 Pro"
            )
        }
        var customModelText by remember {
            mutableStateOf(if (presetModels.none { it.first == uiState.geminiModel }) uiState.geminiModel else "")
        }
        var selectedModel by remember { mutableStateOf(uiState.geminiModel) }

        AlertDialog(
            icon = { Icon(Icons.Outlined.AutoAwesome, null) },
            title = { Text(stringResource(R.string.dialog_gemini_model_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    presetModels.forEach { (modelId, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedModel = modelId
                                    customModelText = ""
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModel == modelId && customModelText.isEmpty(),
                                onClick = {
                                    selectedModel = modelId
                                    customModelText = ""
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedModel == modelId && customModelText.isEmpty()) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customModelText,
                        onValueChange = {
                            customModelText = it
                            if (it.isNotBlank()) selectedModel = it.trim()
                        },
                        label = { Text(stringResource(R.string.dialog_gemini_model_custom_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            onDismissRequest = { showGeminiModelDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalModel = customModelText.trim().ifEmpty { selectedModel }
                        if (finalModel.isNotBlank()) {
                            settingsViewModel.setGeminiModel(finalModel)
                        }
                        showGeminiModelDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiModelDialog = false }) {
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
