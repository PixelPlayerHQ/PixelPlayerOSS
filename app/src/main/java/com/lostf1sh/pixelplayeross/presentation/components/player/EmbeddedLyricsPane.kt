package com.lostf1sh.pixelplayeross.presentation.components.player

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.utils.ScreenAwakeCoordinator
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_LYRICS_DUET_LAYOUT_ENABLED
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_LYRICS_FONT_SIZE_SP
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_SHOW_LYRICS_BACKGROUND_VOCALS
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_DUET_LAYOUT_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_FONT_SIZE_SP_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_WORD_HIGHLIGHT_STYLE_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LyricsWordHighlightStyle
import com.lostf1sh.pixelplayeross.data.preferences.SHOW_LYRICS_BACKGROUND_VOCALS_KEY
import com.lostf1sh.pixelplayeross.data.preferences.dataStore
import com.lostf1sh.pixelplayeross.data.preferences.sanitizeLyricsFontSize
import com.lostf1sh.pixelplayeross.presentation.components.LyricsClipboardText
import com.lostf1sh.pixelplayeross.presentation.components.LyricsLoadingPlaceholder
import com.lostf1sh.pixelplayeross.presentation.components.LyricsSyncControls
import com.lostf1sh.pixelplayeross.presentation.components.PlainLyricsLine
import com.lostf1sh.pixelplayeross.presentation.components.SyncedLyricsList
import com.lostf1sh.pixelplayeross.presentation.components.lyricsSheetColors
import com.lostf1sh.pixelplayeross.presentation.components.resolveCurrentLineIndex
import com.lostf1sh.pixelplayeross.presentation.components.resolveSeekPositionMs
import com.lostf1sh.pixelplayeross.presentation.components.romanizePlainLyrics
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.LyricsMoreBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.withLyricsFontSize
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState
import com.lostf1sh.pixelplayeross.utils.MultiLangRomanizer
import com.lostf1sh.pixelplayeross.utils.ProviderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedLyricsPane(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: ((Int) -> Unit)? = null,
    lyricsTextStyle: TextStyle,
    colorScheme: ColorScheme,
    onSeekTo: (Long) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onSearchLyrics: (() -> Unit)? = null,
    onImportLyrics: (() -> Unit)? = null,
    onResetLyrics: (() -> Unit)? = null,
    onSaveLyricsToFile: ((Song, Lyrics, Boolean) -> Unit)? = null,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    isFavoriteProvider: () -> Boolean = { false },
    onShuffleToggle: () -> Unit = {},
    onRepeatToggle: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    isPlayerExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    highlightZoneFraction: Float = 0.08f,
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null
) {
    val stablePlayerState by stablePlayerStateFlow.collectAsStateWithLifecycle()
    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    val lyricHighlightColor = sheetColors.lyricHighlight
    val contentColor = sheetColors.content

    val isLoadingLyrics = stablePlayerState.isLoadingLyrics
    val lyrics: Lyrics? = stablePlayerState.lyrics
    val isPlaying = stablePlayerState.isPlaying

    val hasSyncedLyrics = lyrics?.synced?.isNotEmpty() == true
    val hasPlainLyrics = lyrics?.plain?.isNotEmpty() == true
    val hasAnyLyrics = hasSyncedLyrics || hasPlainLyrics

    val hasTranslatedLyrics = remember(lyrics) {
        lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
    }

    val hasRomanizedLyrics = remember(lyrics) {
        val hasSynced = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true
        val hasPlain = lyrics?.plain?.any { line ->
            MultiLangRomanizer.isScriptThatNeedsRomanization(line)
        } == true
        hasSynced || hasPlain
    }

    var showSyncedLyrics by remember(hasSyncedLyrics, hasPlainLyrics) {
        mutableStateOf(hasSyncedLyrics)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreSheet by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    val keepScreenOnFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }

    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val shouldKeepAwake = keepScreenOn && isPlayerExpanded

    DisposableEffect(shouldKeepAwake, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        ScreenAwakeCoordinator.request("EmbeddedLyricsPane", shouldKeepAwake, view)

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ScreenAwakeCoordinator.request("EmbeddedLyricsPane", false, view)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    ScreenAwakeCoordinator.reset(view)
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val lyricsAlignmentFlow = remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }
    val lyricsAlignment by lyricsAlignmentFlow.collectAsStateWithLifecycle(initialValue = "left")

    val showLyricsTranslationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }
    val showLyricsTranslation by showLyricsTranslationFlow.collectAsStateWithLifecycle(initialValue = true)

    val showLyricsRomanizationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_romanization")] ?: true }
    }
    val showLyricsRomanization by showLyricsRomanizationFlow.collectAsStateWithLifecycle(initialValue = true)

    val useAnimatedLyricsFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: false }
    }
    val useAnimatedLyrics by useAnimatedLyricsFlow.collectAsStateWithLifecycle(initialValue = false)

    val playbackSpeedFlow = remember(context) {
        context.dataStore.data.map { it[floatPreferencesKey("playback_speed")] ?: 1f }
    }
    val playbackSpeed by playbackSpeedFlow.collectAsStateWithLifecycle(initialValue = 1f)

    val animatedLyricsBlurEnabledFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
    }
    val animatedLyricsBlurEnabled by animatedLyricsBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    val animatedLyricsBlurStrengthFlow = remember(context) {
        context.dataStore.data.map { it[floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
    }
    val animatedLyricsBlurStrength by animatedLyricsBlurStrengthFlow.collectAsStateWithLifecycle(initialValue = 2.5f)

    val lyricsWordHighlightStyleFlow = remember(context) {
        context.dataStore.data.map { LyricsWordHighlightStyle.fromStorage(it[LYRICS_WORD_HIGHLIGHT_STYLE_KEY]) }
    }
    val lyricsWordHighlightStyle by lyricsWordHighlightStyleFlow.collectAsStateWithLifecycle(
        initialValue = LyricsWordHighlightStyle.DEFAULT
    )

    val lyricsDuetLayoutEnabledFlow = remember(context) {
        context.dataStore.data.map { it[LYRICS_DUET_LAYOUT_KEY] ?: DEFAULT_LYRICS_DUET_LAYOUT_ENABLED }
    }
    val lyricsDuetLayoutEnabled by lyricsDuetLayoutEnabledFlow.collectAsStateWithLifecycle(
        initialValue = DEFAULT_LYRICS_DUET_LAYOUT_ENABLED
    )

    val showLyricsBackgroundVocalsFlow = remember(context) {
        context.dataStore.data.map { it[SHOW_LYRICS_BACKGROUND_VOCALS_KEY] ?: DEFAULT_SHOW_LYRICS_BACKGROUND_VOCALS }
    }
    val showLyricsBackgroundVocals by showLyricsBackgroundVocalsFlow.collectAsStateWithLifecycle(
        initialValue = DEFAULT_SHOW_LYRICS_BACKGROUND_VOCALS
    )

    val lyricsFontSizeFlow = remember(context) {
        context.dataStore.data.map {
            sanitizeLyricsFontSize(it[LYRICS_FONT_SIZE_SP_KEY] ?: DEFAULT_LYRICS_FONT_SIZE_SP)
        }
    }
    val lyricsFontSizeSp by lyricsFontSizeFlow.collectAsStateWithLifecycle(initialValue = DEFAULT_LYRICS_FONT_SIZE_SP)
    val sizedLyricsTextStyle = remember(lyricsTextStyle, lyricsFontSizeSp) {
        lyricsTextStyle.withLyricsFontSize(lyricsFontSizeSp)
    }

    val shouldRomanizePlainLyrics = showLyricsRomanization && hasRomanizedLyrics
    val plainLyricsRomanization by produceState<List<String?>?>(
        initialValue = null,
        lyrics?.plain,
        shouldRomanizePlainLyrics
    ) {
        value = if (shouldRomanizePlainLyrics && lyrics?.plain != null) {
            withContext(Dispatchers.Default) {
                romanizePlainLyrics(lyrics.plain)
            }
        } else {
            null
        }
    }

    val currentLineIndex = remember(lyrics, playbackPositionFlow, lyricsSyncOffset) {
        resolveCurrentLineIndex(
            lines = lyrics?.synced.orEmpty(),
            position = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
        ).coerceAtLeast(0)
    }

    val syncedListState = rememberLazyListState(initialFirstVisibleItemIndex = currentLineIndex)
    val staticListState = rememberLazyListState()

    val resolvedAutoscrollSpec: AnimationSpec<Float> = remember(autoscrollAnimationSpec) {
        autoscrollAnimationSpec ?: tween(durationMillis = 350, easing = FastOutSlowInEasing)
    }

    val clipboardManager = LocalClipboard.current
    val copyLineActionLabel = stringResource(R.string.lyrics_copy_line_action)
    val lyricsCopiedMessage = stringResource(R.string.lyrics_copied)

    val copyLyricsToClipboard: (String) -> Unit = remember(clipboardManager, context) {
        { textToCopy ->
            coroutineScope.launch {
                val clipData = ClipData.newPlainText("Lyrics", textToCopy)
                clipboardManager.setClipEntry(ClipEntry(clipData))
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Expand to full-screen lyrics sheet
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: Mode switcher (Synced vs Static) - Material 3 Expressive connected toggle buttons
                    if (hasSyncedLyrics && hasPlainLyrics) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToggleButton(
                                checked = showSyncedLyrics,
                                onCheckedChange = { showSyncedLyrics = true },
                                modifier = Modifier
                                    .height(38.dp)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                elevation = null,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.presentation_batch_g_lyrics_mode_synced),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (showSyncedLyrics) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            ToggleButton(
                                checked = !showSyncedLyrics,
                                onCheckedChange = { showSyncedLyrics = false },
                                modifier = Modifier
                                    .height(38.dp)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                elevation = null,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.presentation_batch_g_lyrics_mode_static),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (!showSyncedLyrics) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.lyrics),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Right: Actions (More options / Close button)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasAnyLyrics) {
                            IconButton(
                                onClick = {
                                    if (onMoreClick != null) onMoreClick() else showMoreSheet = true
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.presentation_batch_g_lyrics_cd_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lyrics Sync Controls (shown when toggled from settings)
                AnimatedVisibility(
                    visible = showSyncedLyrics && hasSyncedLyrics && showSyncControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LyricsSyncControls(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        offsetMillis = lyricsSyncOffset,
                        onOffsetChange = { onLyricsSyncOffsetChange?.invoke(it) },
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentColor = sheetColors.syncButtonContainer,
                        onAccentColor = sheetColors.syncButtonContent,
                        onBackgroundColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Lyrics Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoadingLyrics) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LyricsLoadingPlaceholder(
                                contentColor = contentColor,
                                accentColor = lyricHighlightColor,
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                        }
                    } else if (!hasAnyLyrics) {
                        // Empty state: no lyrics
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_lyrics_24),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.lyrics_not_found),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.search_lyrics_online_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (onSearchLyrics != null) {
                                    FilledTonalButton(
                                        onClick = onSearchLyrics,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.search))
                                    }
                                }
                                if (onImportLyrics != null) {
                                    OutlinedButton(
                                        onClick = onImportLyrics,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.import_file))
                                    }
                                }
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = showSyncedLyrics && hasSyncedLyrics,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                            },
                            label = "lyricsModeSwitch"
                        ) { isSyncedMode ->
                            if (isSyncedMode) {
                                lyrics?.synced?.let { synced: List<SyncedLine> ->
                                    val syncedLines: ImmutableList<SyncedLine> = remember(synced) { synced.toImmutableList() }
                                    SyncedLyricsList(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp),
                                        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
                                        lines = syncedLines,
                                        listState = syncedListState,
                                        playbackPositionFlow = playbackPositionFlow,
                                        isPlaying = isPlaying,
                                        playbackSpeed = playbackSpeed,
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        accentColor = lyricHighlightColor,
                                        textStyle = sizedLyricsTextStyle,
                                        onLineClick = { syncedLine ->
                                            onSeekTo(
                                                resolveSeekPositionMs(
                                                    lineTimeMs = syncedLine.time.toLong(),
                                                    lyricsSyncOffsetMs = lyricsSyncOffset
                                                )
                                            )
                                        },
                                        onLineLongClick = { syncedLine ->
                                            copyLyricsToClipboard(
                                                LyricsClipboardText.syncedLine(
                                                    syncedLine,
                                                    includeBackground = showLyricsBackgroundVocals
                                                )
                                            )
                                        },
                                        lineLongClickLabel = copyLineActionLabel,
                                        highlightZoneFraction = highlightZoneFraction,
                                        highlightOffsetDp = highlightOffsetDp,
                                        autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                        useAnimatedLyrics = useAnimatedLyrics,
                                        animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                                        animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                        immersiveMode = false,
                                        lyricsAlignment = lyricsAlignment,
                                        showTranslation = showLyricsTranslation,
                                        showRomanization = showLyricsRomanization,
                                        wordHighlightStyle = lyricsWordHighlightStyle,
                                        duetLayoutEnabled = lyricsDuetLayoutEnabled,
                                        showBackgroundVocals = showLyricsBackgroundVocals,
                                        footer = {
                                            if (lyrics.areFromRemote) {
                                                item(key = "provider_text") {
                                                    ProviderText(
                                                        providerText = context.resources.getString(R.string.lyrics_provided_by),
                                                        uri = context.resources.getString(R.string.lrclib_uri),
                                                        textAlign = TextAlign.Center,
                                                        accentColor = lyricHighlightColor,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                lyrics?.plain?.let { plain: List<String> ->
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp),
                                        state = staticListState,
                                        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp)
                                    ) {
                                        itemsIndexed(
                                            items = plain,
                                            key = { index: Int, line: String -> "$index-$line" }
                                        ) { index: Int, line: String ->
                                            PlainLyricsLine(
                                                line = line,
                                                style = sizedLyricsTextStyle,
                                                romanization = plainLyricsRomanization?.getOrNull(index),
                                                lyricsAlignment = lyricsAlignment,
                                                showTranslation = if (hasTranslatedLyrics) showLyricsTranslation else true,
                                                showRomanization = if (hasRomanizedLyrics) showLyricsRomanization else true,
                                                modifier = Modifier.fillMaxWidth(),
                                                onLongClick = {
                                                    copyLyricsToClipboard(
                                                        LyricsClipboardText.plainLine(line)
                                                    )
                                                },
                                                onLongClickLabel = copyLineActionLabel
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Top subtle gradient mask
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Bottom subtle gradient mask
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    if (showMoreSheet) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            LyricsMoreBottomSheet(
                onDismissRequest = { showMoreSheet = false },
                sheetState = moreSheetState,
                lyrics = lyrics,
                showSyncedLyrics = showSyncedLyrics,
                isSyncControlsVisible = showSyncControls,
                onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                canCopyLyrics = hasAnyLyrics,
                onCopyLyrics = {
                    copyLyricsToClipboard(
                        LyricsClipboardText.fullLyrics(
                            lyrics,
                            includeBackground = showLyricsBackgroundVocals
                        )
                    )
                },
                onResetImportedLyrics = {
                    onResetLyrics?.invoke()
                },
                onToggleSyncControls = {
                    showSyncControls = !showSyncControls
                },
                isImmersiveTemporarilyDisabled = false,
                onSetImmersiveTemporarilyDisabled = {},
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = { enabled ->
                    keepScreenOn = enabled
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = enabled
                        }
                    }
                },
                lyricsAlignment = lyricsAlignment,
                onLyricsAlignmentChange = { newAlignment ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[stringPreferencesKey("lyrics_alignment")] = newAlignment
                        }
                    }
                },
                hasTranslatedLyrics = hasTranslatedLyrics,
                hasRomanizedLyrics = hasRomanizedLyrics,
                showTranslation = showLyricsTranslation,
                showRomanization = showLyricsRomanization,
                onShowTranslationChange = { enabled ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                        }
                    }
                },
                onShowRomanizationChange = { enabled ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                        }
                    }
                },
                immersiveLyricsEnabled = false,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }

    if (showSaveLyricsDialog && lyrics != null && stablePlayerState.currentSong != null) {
        val hasSynced = !lyrics.synced.isNullOrEmpty()
        val hasPlain = !lyrics.plain.isNullOrEmpty()
        val songToSave = stablePlayerState.currentSong!!
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.save_lyrics_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.save_lyrics_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile?.invoke(songToSave, lyrics, true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_synced_lyrics))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile?.invoke(songToSave, lyrics, false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_plain_lyrics))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}
