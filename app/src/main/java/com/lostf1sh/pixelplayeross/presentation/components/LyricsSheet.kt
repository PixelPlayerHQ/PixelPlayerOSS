package com.lostf1sh.pixelplayeross.presentation.components

import android.animation.ValueAnimator
import android.content.ClipData
import android.os.Build
import android.widget.Toast
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.R
import androidx.activity.compose.BackHandler
import com.lostf1sh.pixelplayeross.presentation.components.scoped.LyricsPredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.AutoScrollingText
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.consumePositionChange
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import com.lostf1sh.pixelplayeross.data.repository.LyricsSearchResult
import com.lostf1sh.pixelplayeross.presentation.screens.TabAnimation
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.FetchLyricsDialog
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.PlayerSeekBar
import com.lostf1sh.pixelplayeross.presentation.viewmodel.LyricsSearchUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.StablePlayerState
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.utils.BubblesLine
import com.lostf1sh.pixelplayeross.utils.ProviderText
import com.lostf1sh.pixelplayeross.presentation.components.snapping.ExperimentalSnapperApi
import com.lostf1sh.pixelplayeross.presentation.components.snapping.SnapperLayoutInfo
import com.lostf1sh.pixelplayeross.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.lostf1sh.pixelplayeross.presentation.components.snapping.rememberSnapperFlingBehavior
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.LyricsMoreBottomSheet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lostf1sh.pixelplayeross.data.preferences.dataStore
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_LYRICS_DUET_LAYOUT_ENABLED
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_LYRICS_FONT_SIZE_SP
import com.lostf1sh.pixelplayeross.data.preferences.DEFAULT_SHOW_LYRICS_BACKGROUND_VOCALS
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_DUET_LAYOUT_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_FONT_SIZE_SP_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LYRICS_WORD_HIGHLIGHT_STYLE_KEY
import com.lostf1sh.pixelplayeross.data.preferences.LyricsWordHighlightStyle
import com.lostf1sh.pixelplayeross.data.preferences.SHOW_LYRICS_BACKGROUND_VOCALS_KEY
import com.lostf1sh.pixelplayeross.data.preferences.sanitizeLyricsFontSize

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.PlayingEqIcon
import com.lostf1sh.pixelplayeross.utils.MultiLangRomanizer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

internal data class LyricsSheetColors(
    val container: Color,
    val content: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val accent: Color,
    val accentContent: Color,
    val lyricHighlight: Color,
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val syncButtonContainer: Color,
    val syncButtonContent: Color
)

internal fun lyricsSheetColors(colorScheme: ColorScheme): LyricsSheetColors {
    val container = colorScheme.primaryContainer
    val content = colorScheme.onPrimaryContainer
    val accent = colorScheme.primary
    val accentContent = colorScheme.onPrimary

    return LyricsSheetColors(
        container = container,
        content = content,
        controlContainer = colorScheme.surfaceContainerLowest,
        controlContent = colorScheme.onSurface,
        accent = accent,
        accentContent = accentContent,
        lyricHighlight = preferredContrastColor(
            background = container,
            preferred = accent,
            fallback = content
        ),
        playPauseContainer = colorScheme.tertiaryFixedDim,
        playPauseContent = colorScheme.onTertiaryFixed,
        syncButtonContainer = colorScheme.secondaryFixedDim,
        syncButtonContent = colorScheme.onSecondaryFixed
    )
}

private fun preferredContrastColor(
    background: Color,
    preferred: Color,
    fallback: Color,
    minContrastRatio: Double = 4.5
): Color {
    if (contrastRatio(preferred, background) >= minContrastRatio) return preferred
    if (contrastRatio(fallback, background) >= minContrastRatio) return fallback

    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val argb = encodedSrgbArgb()
    val red = linearizedChannel((argb shr 16) and 0xFF)
    val green = linearizedChannel((argb shr 8) and 0xFF)
    val blue = linearizedChannel(argb and 0xFF)
    return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
}

private fun Color.encodedSrgbArgb(): Int = (value shr 32).toInt()

private fun linearizedChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

/**
 * Corner radius of the lyrics sheet for a predictive-back [progress] (0f = full-screen,
 * 1f = fully dismissed). Returns null at rest so the full-screen surface stays square.
 */
internal fun lyricsSheetCornerRadius(progress: Float): Dp? =
    if (progress > 0f) 32.dp * progress else null

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    lyricsTextStyle: TextStyle,
    colorScheme: ColorScheme,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    onSaveLyricsToFile: (Song, Lyrics, Boolean) -> Unit,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Dp = 100.dp,
    highlightZoneFraction: Float = 0.08f,
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null
) {
    var backProgress by remember { mutableFloatStateOf(1f) }

    val backProgressProvider = rememberUpdatedState(backProgress)

    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    val stablePlayerState by stablePlayerStateFlow.collectAsStateWithLifecycle()
    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    val backgroundColor = sheetColors.controlContainer
    val onBackgroundColor = sheetColors.controlContent
    val containerColor = sheetColors.container
    val contentColor = sheetColors.content
    val accentColor = sheetColors.accent
    val onAccentColor = sheetColors.accentContent
    val lyricHighlightColor = sheetColors.lyricHighlight
    val playPauseColor = sheetColors.playPauseContainer
    val onPlayPauseColor = sheetColors.playPauseContent

    val isLoadingLyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSong by remember(stablePlayerState) { derivedStateOf { stablePlayerState.currentSong } }

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

    val context = LocalContext.current

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
        context.dataStore.data.map { it[androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
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
    // Copying follows what the synced view shows, backing vocals included only while they're shown.
    val canCopyLyrics = remember(lyrics, showLyricsBackgroundVocals) {
        LyricsClipboardText.hasCopyableText(lyrics, includeBackground = showLyricsBackgroundVocals)
    }

    val lyricsFontSizeFlow = remember(context) {
        context.dataStore.data.map {
            sanitizeLyricsFontSize(it[LYRICS_FONT_SIZE_SP_KEY] ?: DEFAULT_LYRICS_FONT_SIZE_SP)
        }
    }
    val lyricsFontSizeSp by lyricsFontSizeFlow.collectAsStateWithLifecycle(initialValue = DEFAULT_LYRICS_FONT_SIZE_SP)
    val sizedLyricsTextStyle = remember(lyricsTextStyle, lyricsFontSizeSp) {
        lyricsTextStyle.withLyricsFontSize(lyricsFontSizeSp)
    }

    // Plain lyrics are stored without romanization; derive it off the main thread only
    // while the romanization toggle is on.
    val shouldRomanizePlainLyrics = showLyricsRomanization && hasRomanizedLyrics
    val plainLyricsRomanization by produceState<List<String?>?>(
        initialValue = null,
        lyrics?.plain,
        shouldRomanizePlainLyrics
    ) {
        val plainLines = lyrics?.plain
        value = if (!shouldRomanizePlainLyrics || plainLines.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.Default) { romanizePlainLyrics(plainLines) }
        }
    }

    val keepScreenOnFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }
    val coroutineScope = rememberCoroutineScope()

    val clipboard = LocalClipboard.current
    val clipLabel = stringResource(R.string.lyrics)
    val lyricsCopiedMessage = stringResource(R.string.lyrics_copied)
    val lineCopiedMessage = stringResource(R.string.lyrics_line_copied)
    val copyLineActionLabel = stringResource(R.string.lyrics_copy_line_action)

    fun copyLyricsToClipboard(text: String, confirmation: String) {
        if (text.isBlank()) return
        coroutineScope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to copy lyrics to the clipboard")
                return@launch
            }
            // Android 13+ confirms clipboard writes with its own overlay.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedAutoscrollSpec = autoscrollAnimationSpec ?: if (useAnimatedLyrics) {
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        )
    } else {
        tween(durationMillis = 450, easing = FastOutSlowInEasing)
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var wasResetTriggered by remember { mutableStateOf(false) }
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    var previewSeekPositionMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf(
            when {
                !lyrics?.synced.isNullOrEmpty() -> true
                !lyrics?.plain.isNullOrEmpty() -> false
                else -> null
            }
        )
    }

    val hasSyncedLyrics = remember(lyrics) {
        !lyrics?.synced.isNullOrEmpty()
    }

    var immersiveMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalSheetState(
        skipPartiallyExpanded = true
    )

    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var hasTriggeredAction by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val overlayTranslation = remember { Animatable(0f) }
    val swipeProgress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(immersiveLyricsEnabled, lastInteractionTime, showSyncedLyrics, isImmersiveTemporarilyDisabled) {
        if (immersiveLyricsEnabled && showSyncedLyrics == true && !isImmersiveTemporarilyDisabled) {
            delay(immersiveLyricsTimeout)
            immersiveMode = true
        } else {
            immersiveMode = false
        }
    }

    val fontScale by animateFloatAsState(
        targetValue = if (immersiveMode) 1.4f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fontScale"
    )
    
    val scaledTextStyle = sizedLyricsTextStyle.copy(
        fontSize = sizedLyricsTextStyle.fontSize * fontScale,
        lineHeight = sizedLyricsTextStyle.lineHeight * fontScale
    )

    fun resetImmersiveTimer() {
        lastInteractionTime = System.currentTimeMillis()
        immersiveMode = false
    }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            if (!wasResetTriggered) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = onSearchLyrics,
                onPickResult = onPickResult,
                onManualSearch = onManualSearch,
                onDismiss = {
                    showFetchLyricsDialog = false
                    onDismissLyricsSearch()
                    if (lyrics == null && !isLoadingLyrics) {
                        onBackClick()
                    }
                },
                onImport = onImportLyrics
            )
        }
    }

    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
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
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    true
                                )
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
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    false
                                )
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

    

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = backProgressProvider.value
                val scale = lerp(1f, 0.92f, p)
                scaleX = scale
                scaleY = scale
                translationY = lerp(0f, size.height * 0.08f, p)
                // The 32dp radius belongs to the shrunken predictive-back card. Applying it
                // at rest rounded the corners of a surface that fills the screen, so the
                // layer behind showed through. Grow it with the gesture and stay square
                // while full-screen.
                lyricsSheetCornerRadius(p)?.let { radius ->
                    clip = true
                    shape = RoundedCornerShape(radius)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isSwipeActive = true
                        hasTriggeredAction = false
                        dragOffset = 0f
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            swipeProgress.snapTo(0f)
                        }
                    },
                    onDragEnd = {
                        isSwipeActive = false
                        val committed = abs(dragOffset) > swipeThresholdPx && !hasTriggeredAction 
                        
                        if (committed) {
                            if (dragOffset > 0) onPrev() else onNext()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        coroutineScope.launch {
                             swipeProgress.animateTo(0f, tween(200))
                             dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isSwipeActive = false
                        dragOffset = 0f
                        coroutineScope.launch {
                            swipeProgress.animateTo(0f, tween(200))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        resetImmersiveTimer()
                        
                        if (!hasTriggeredAction) {
                            dragOffset += dragAmount.x
                            val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            
                            coroutineScope.launch {
                                swipeProgress.snapTo(progress)
                            }
                        }
                    }
                )
            },
        containerColor = containerColor,
        contentColor = contentColor,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    resetImmersiveTimer()
                }
        ) {
            val initialSyncedLineIndex = remember(lyrics, playbackPositionFlow, lyricsSyncOffset) {
                resolveCurrentLineIndex(
                    lines = lyrics?.synced.orEmpty(),
                    position = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                ).coerceAtLeast(0)
            }
            val syncedListState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialSyncedLineIndex
            )
            val staticListState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + 
                         scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(2f)
                        .wrapContentWidth(),
                    label = "headerAnimation"
                ) { song ->
                    LyricsTrackInfo(
                        song = song,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                            )
                            .background(
                                color = backgroundColor,
                                shape = CircleShape
                            )
                            .wrapContentWidth()
                            .animateContentSize(),
                        backgroundColor = backgroundColor,
                        contentColor = onBackgroundColor,
                        isPlaying = isPlaying
                    )
                }

                when (showSyncedLyrics) {
                    null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 110.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                        ) {
                            item(key = "loader_or_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(top = 48.dp),
                                    contentAlignment = when (lyricsAlignment) {
                                        "center" -> Alignment.TopCenter
                                        "right" -> Alignment.TopEnd
                                        else -> Alignment.TopStart
                                    }
                                ) {
                                    if (isLoadingLyrics) {
                                        LyricsLoadingPlaceholder(
                                            contentColor = contentColor,
                                            accentColor = lyricHighlightColor,
                                            horizontalAlignment = when (lyricsAlignment) {
                                                "center" -> Alignment.CenterHorizontally
                                                "right" -> Alignment.End
                                                else -> Alignment.Start
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    true -> {
                        lyrics?.synced?.let { synced ->
                            val syncedLines = remember(synced) { synced.toImmutableList() }
                            SyncedLyricsList(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(top = 130.dp, bottom = 100.dp),
                                lines = syncedLines,
                                listState = syncedListState,
                                playbackPositionFlow = playbackPositionFlow,
                                isPlaying = isPlaying,
                                playbackSpeed = playbackSpeed,
                                lyricsSyncOffset = lyricsSyncOffset,
                                positionOverrideMs = previewSeekPositionMs,
                                accentColor = lyricHighlightColor,
                                textStyle = scaledTextStyle,
                                onLineClick = { syncedLine -> 
                                    onSeekTo(
                                        resolveSeekPositionMs(
                                            lineTimeMs = syncedLine.time.toLong(),
                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                        )
                                    )
                                    resetImmersiveTimer()
                                },
                                onLineLongClick = { syncedLine ->
                                    resetImmersiveTimer()
                                    copyLyricsToClipboard(
                                        LyricsClipboardText.syncedLine(
                                            syncedLine,
                                            includeBackground = showLyricsBackgroundVocals
                                        ),
                                        lineCopiedMessage
                                    )
                                },
                                lineLongClickLabel = copyLineActionLabel,
                                highlightZoneFraction = highlightZoneFraction,
                                highlightOffsetDp = highlightOffsetDp,
                                autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                useAnimatedLyrics = useAnimatedLyrics,
                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                immersiveMode = immersiveMode,
                                lyricsAlignment = lyricsAlignment,
                                showTranslation = showLyricsTranslation,
                                showRomanization = showLyricsRomanization,
                                wordHighlightStyle = lyricsWordHighlightStyle,
                                duetLayoutEnabled = lyricsDuetLayoutEnabled,
                                showBackgroundVocals = showLyricsBackgroundVocals,
                                footer = {
                                    if (lyrics?.areFromRemote == true) {
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
                    }

                    false -> {
                        lyrics?.plain?.let { plain ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = staticListState,
                                contentPadding = PaddingValues(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 130.dp,
                                    bottom = 24.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = plain,
                                    key = { index, line -> "$index-$line" }
                                ) { index, line ->
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
                                                LyricsClipboardText.plainLine(line),
                                                lineCopiedMessage
                                            )
                                        },
                                        onLongClickLabel = copyLineActionLabel
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(containerColor, Color.Transparent)
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, containerColor)
                            )
                        )
                )
            }

            AnimatedVisibility(
                visible = !immersiveMode,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerColor)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp, end = 16.dp, start = 16.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                         resetImmersiveTimer()
                                    }
                                }
                            }
                        }
                ) {
                                AnimatedVisibility(
                    visible = showSyncedLyrics == true && lyrics?.synced != null && showSyncControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LyricsSyncControls(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        offsetMillis = lyricsSyncOffset,
                        onOffsetChange = onLyricsSyncOffsetChange,
                        backgroundColor = backgroundColor,
                        accentColor = sheetColors.syncButtonContainer,
                        onAccentColor = sheetColors.syncButtonContent,
                        onBackgroundColor = onBackgroundColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val playPauseCornerRadius by animateDpAsState(
                        targetValue = if (isPlaying) 18.dp else 50.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "playPauseShape"
                    )

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(playPauseCornerRadius))
                            .background(playPauseColor)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            label = "playPauseIconAnimation"
                        ) { playing ->
                            if (playing) {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = stringResource(R.string.cd_pause),
                                    tint = onPlayPauseColor
                                )
                            } else {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play),
                                    tint = onPlayPauseColor
                                )
                            }
                        }
                    }

                    LyricsPlaybackSeekBar(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        playbackPositionFlow = playbackPositionFlow,
                        backgroundColor = backgroundColor,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        totalDuration = stablePlayerState.totalDuration,
                        onSeekTo = onSeekTo,
                        onSeekPreviewChange = { previewSeekPositionMs = it },
                        isPlaying = isPlaying
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                LyricsFloatingToolbar(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    showSyncedLyrics = showSyncedLyrics,
                    hasSyncedLyrics = hasSyncedLyrics,
                    onShowSyncedLyricsChange = { showSyncedLyrics = it },
                    onNavigateBack = {
                        onBackClick()
                    },
                    onMoreClick = { showMoreSheet = true },
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    backProgressProvider = { backProgressProvider.value },
                )
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
                    showSyncedLyrics = showSyncedLyrics == true,
                    isSyncControlsVisible = showSyncControls,
                    onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                    canCopyLyrics = canCopyLyrics,
                    onCopyLyrics = {
                        copyLyricsToClipboard(
                            LyricsClipboardText.fullLyrics(
                                lyrics,
                                includeBackground = showLyricsBackgroundVocals
                            ),
                            lyricsCopiedMessage
                        )
                    },
                    onResetImportedLyrics = {
                        wasResetTriggered = true
                        resetLyricsForCurrentSong()
                    },
                    onToggleSyncControls = {
                        resetImmersiveTimer()
                        showSyncControls = !showSyncControls
                    },
                    isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                    onSetImmersiveTemporarilyDisabled = {
                        resetImmersiveTimer()
                        onSetImmersiveTemporarilyDisabled(it)
                    },
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
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                            }
                        }
                    },
                    onShowRomanizationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                            }
                        }
                    },
                    immersiveLyricsEnabled = immersiveLyricsEnabled,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = {
                        resetImmersiveTimer()
                        onShuffleToggle()
                    },
                    onRepeatToggle = {
                        resetImmersiveTimer()
                        onRepeatToggle()
                    },
                    onFavoriteToggle = {
                        resetImmersiveTimer()
                        onFavoriteToggle()
                    },
                )
            }
        }

       AnimatedVisibility(
            visible = immersiveMode,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FilledIconButton(
                onClick = { resetImmersiveTimer() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_show_controls)
                )
            }
        }
       
       if (isSwipeActive || swipeProgress.value > 0f) {
           val isNext = dragOffset < 0
           val overlayAlignment = if (isNext) Alignment.CenterEnd else Alignment.CenterStart
           val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious
           
           Box(
               modifier = Modifier
                   .align(overlayAlignment)
                   .size(100.dp)
                   .padding(
                       start = if(isNext) 0.dp else 6.dp,
                       end = if(isNext) 6.dp else 0.dp
                   )
                   .graphicsLayer {
                        val widthPx = size.width
                        val initialOffset = if(isNext) widthPx else -widthPx
                        translationX = initialOffset * (1f - swipeProgress.value)

                        scaleX = 0.8f + (swipeProgress.value * 0.2f)
                        scaleY = 0.8f + (swipeProgress.value * 0.2f)
                   }
                   .background(
                        color = accentColor,
                        shape = RoundedCornerShape(
                            topStart = if(isNext) 360.dp else 8.dp,
                            bottomStart = if(isNext) 360.dp else 8.dp,
                            topEnd = if(isNext) 8.dp else 360.dp,
                            bottomEnd = if(isNext) 8.dp else 360.dp
                        )
                   ),
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = icon,
                   contentDescription = null,
                   modifier = Modifier.size(48.dp),
                   tint = onAccentColor
               )
           }
       }

      }
    }
}

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: ImmutableList<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    playbackSpeed: Float,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    onLineLongClick: ((SyncedLine) -> Unit)? = null,
    lineLongClickLabel: String? = null,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    wordHighlightStyle: LyricsWordHighlightStyle = LyricsWordHighlightStyle.DEFAULT,
    duetLayoutEnabled: Boolean = true,
    showBackgroundVocals: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: LazyListScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    // Kept as State and read only in effects, derived state and draw blocks: playback never
    // recomposes the list or its lines, only a line change or a new word being sung does.
    val sampledPlaybackPosition = playbackPositionFlow.collectAsStateWithLifecycle()
    val hasWordTimings = remember(lines) {
        lines.any { !it.words.isNullOrEmpty() || !it.background?.words.isNullOrEmpty() }
    }
    val interpolatedPosition = rememberInterpolatedPlaybackPosition(
        sampledPosition = sampledPlaybackPosition,
        isPlaying = isPlaying && hasWordTimings,
        playbackSpeed = playbackSpeed,
        positionOverrideMs = positionOverrideMs
    )
    val currentSyncOffset by rememberUpdatedState(lyricsSyncOffset)
    val currentPositionOverride by rememberUpdatedState(positionOverrideMs)
    val positionProvider: () -> Long = remember(interpolatedPosition) {
        {
            currentPositionOverride
                ?: (interpolatedPosition.value + currentSyncOffset).coerceAtLeast(0L)
        }
    }
    val isPreviewSeeking = positionOverrideMs != null
    // Hidden backing vocals must not keep their line lit.
    val lineEndTimesMs = remember(lines, showBackgroundVocals) {
        resolveLineEndTimesMs(lines, includeBackground = showBackgroundVocals)
    }
    val litLines by remember(lines, lineEndTimesMs, positionProvider) {
        derivedStateOf {
            resolveLitLines(lines = lines, lineEndTimesMs = lineEndTimesMs, position = positionProvider())
        }
    }
    val linesHaveMultipleVoices = remember(lines) { hasMultipleVoices(lines) }
    val useDuetLayout = duetLayoutEnabled && linesHaveMultipleVoices
    // Reduced motion keeps the highlight a plain per-syllable switch.
    val effectiveHighlightStyle = if (ValueAnimator.areAnimatorsEnabled()) {
        wordHighlightStyle
    } else {
        LyricsWordHighlightStyle.CLASSIC
    }
    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier = modifier) {
        // The list follows the last line being sung.
        val currentLineIndex = litLines.last
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        LaunchedEffect(currentLineIndex, lines.size, metrics, isPreviewSeeking) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (currentLineIndex !in lines.indices) return@LaunchedEffect
            if (listState.layoutInfo.totalItemsCount == 0) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            }

            if (!hasAlignedInitialLine) {
                listState.scrollToItem(currentLineIndex)
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = currentLineIndex
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            if (listState.isScrollInProgress && !isPreviewSeeking) return@LaunchedEffect

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(currentLineIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2) {
                    listState.scrollToItem(currentLineIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                val currentLineTime = lines.getOrNull(currentLineIndex)?.time ?: 0
                val nextLineTime = lines.getOrNull(currentLineIndex + 1)?.time ?: (currentLineTime + 1000)
                val timeDiff = (nextLineTime - currentLineTime).coerceIn(250, 2000)
                
                tween<Float>(
                    durationMillis = timeDiff,
                    easing = FastOutSlowInEasing
                )
            } else {
                autoscrollAnimationSpec
            }

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = currentLineIndex,
                animationSpec = dynamicAnimationSpec
            )
            lastAutoScrolledLineIndex = currentLineIndex
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val lineEndTimeMs = lineEndTimesMs.getOrElse(index) { line.time + 1L }
                    val distanceFromCurrent = litLines.distanceTo(index)
                    val showsBackgroundOnly = line.line.isBlank() &&
                        showBackgroundVocals &&
                        line.background?.text?.isNotBlank() == true

                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == index }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f
                            
                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f
                            
                            val maxTranslation = 40f 
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation 
                        }
                    } else Modifier

                    // A line that only holds backing vocals (after an instrumental marker, or a TTML
                    // paragraph sung entirely in the background) shows them instead of the bubbles.
                    if (line.line.isNotBlank() || showsBackgroundOnly) {
                        LyricLineRow(
                            line = line,
                            lineEndTimeMs = lineEndTimeMs,
                            positionProvider = positionProvider,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = resolveLineAlignment(
                                voice = line.voice,
                                userAlignment = lyricsAlignment,
                                duetLayout = useDuetLayout
                            ),
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            showBackgroundVocals = showBackgroundVocals,
                            wordHighlightStyle = effectiveHighlightStyle,
                            accentColor = accentColor,
                            style = textStyle,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onLongClick = if (onLineLongClick != null) {
                                { onLineLongClick(line) }
                            } else {
                                null
                            },
                            onLongClickLabel = lineLongClickLabel,
                            onClick = { onLineClick(line) }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            time = line.time,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            nextTime = lineEndTimeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                footer()
            }

        }
    }
}

@Composable
fun LyricLineRow(
    line: SyncedLine,
    lineEndTimeMs: Long,
    positionProvider: () -> Long,
    distanceFromCurrent: Int = 100,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    showBackgroundVocals: Boolean = true,
    wordHighlightStyle: LyricsWordHighlightStyle = LyricsWordHighlightStyle.DEFAULT,
    accentColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit
) {
    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val wordClusters = remember(line.words) { buildLyricClusters(line.words) }
    val background = line.background?.takeIf { showBackgroundVocals }
    val backgroundClusters = remember(background) { buildLyricClusters(background?.words) }
    val backgroundText = remember(background) { background?.text?.let(::sanitizeLyricLineText).orEmpty() }
    val lineStartTime = line.time.toLong()
    val isCurrentLine by remember(lineStartTime, lineEndTimeMs, positionProvider) {
        derivedStateOf { positionProvider() in lineStartTime..<lineEndTimeMs }
    }
    // Every line being sung keeps full focus, also when it is not the one the list follows.
    val focusDistance = if (isCurrentLine) 0 else distanceFromCurrent.coerceAtLeast(1)
    val decorativeMotionEnabled = useAnimatedLyrics && ValueAnimator.areAnimatorsEnabled()
    val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.45f)
    // Words not sung yet sit a little dimmer when decorative motion is on.
    val unsungWordColor = if (decorativeMotionEnabled) {
        unhighlightedColor.copy(alpha = unhighlightedColor.alpha * 0.55f)
    } else {
        unhighlightedColor
    }
    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unhighlightedColor,
        animationSpec = tween(
            durationMillis = if (decorativeMotionEnabled) 90 else 160,
            easing = FastOutSlowInEasing
        ),
        label = "lineColor"
    )

    val targetScale = if (decorativeMotionEnabled) when (focusDistance) {
        0 -> if (immersiveMode) 1.02f else 1.07f; 1 -> 0.97f; else -> 0.92f
    } else 1f
    val targetAlpha = if (decorativeMotionEnabled) when (focusDistance) {
        0 -> 1.0f; 1 -> 0.6f; else -> 0.3f
    } else 1f

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "lineScale"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "lineAlpha"
    )

    val targetBlur = if (decorativeMotionEnabled && animatedLyricsBlurEnabled && focusDistance > 0) {
        (focusDistance * animatedLyricsBlurStrength).coerceAtMost(10f).dp
    } else 0.dp

    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lineBlur"
    )
    val lineBlurEffectCache = remember { BlurEffectCache() }

    // Keep line height stable while the visual focus moves. Scaling happens in a graphics layer,
    // so neither the word pulse nor the active-line transition can make the lazy list jump.
    val verticalPadding = if (useAnimatedLyrics) 16.dp else 12.dp

    val baseModifier = if (useAnimatedLyrics && !immersiveMode) {
        when (lyricsAlignment) {
            "center" -> modifier.padding(horizontal = 36.dp)
            "right" -> modifier.padding(start = 36.dp)
            else -> modifier.padding(end = 36.dp)
        }
    } else {
        modifier
    }
    val animatedModifier = if (useAnimatedLyrics) {
        baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (lyricsAlignment) {
                        "center" -> 0.5f
                        "right" -> 1f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
                // Read the animated blur here, in the draw phase, so the transition doesn't
                // recompose every visible line each frame. The radius is quantized to whole
                // pixels and the RenderEffect (API 31+) is cached between frames.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radiusPx = blurRadius.toPx()
                    renderEffect = if (radiusPx > 0.5f) {
                        lineBlurEffectCache.get(radiusPx.roundToInt().toFloat())
                    } else {
                        null
                    }
                }
            }
    } else baseModifier

    val translationText = line.translation
    val romanizationText = line.romanization

    val secondaryStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val backgroundStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * BACKGROUND_VOCALS_FONT_SCALE).sp,
            lineHeight = (style.lineHeight.value * BACKGROUND_VOCALS_FONT_SCALE).sp,
            fontWeight = FontWeight.Normal
        )
    }

    val romanizationColor = lineColor.copy(alpha = lineColor.alpha * 0.85f)
    val translationColor = lineColor.copy(alpha = lineColor.alpha * 0.55f)

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val boxAlignment = when (lyricsAlignment) {
        "center" -> Alignment.TopCenter
        "right" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    Column(
        modifier = animatedModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(vertical = verticalPadding, horizontal = 2.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        if (!wordClusters.isNullOrEmpty()) {
            // One child per visual word: syllables with no space between them stay together.
            SyncedWordFlow(
                clusters = wordClusters,
                positionProvider = positionProvider,
                rangeStartMs = lineStartTime,
                rangeEndMs = lineEndTimeMs,
                style = style,
                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                reserveBoldWidth = true,
                highlightedColor = accentColor,
                unhighlightedColor = unsungWordColor,
                highlightStyle = wordHighlightStyle,
                emphasisEnabled = decorativeMotionEnabled,
                lyricsAlignment = lyricsAlignment
            )
        } else if (sanitizedLine.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = boxAlignment) {
                Text(
                    text = sanitizedLine,
                    style = style,
                    color = Color.Transparent,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign
                )
                Text(
                    text = sanitizedLine,
                    style = style,
                    color = lineColor,
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    textAlign = textAlign
                )
            }
        }

        if (showRomanization && !romanizationText.isNullOrBlank()) {
            Text(
                text = romanizationText,
                style = secondaryStyle,
                color = romanizationColor,
                textAlign = textAlign,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!backgroundClusters.isNullOrEmpty()) {
            SyncedWordFlow(
                clusters = backgroundClusters,
                positionProvider = positionProvider,
                rangeStartMs = lineStartTime,
                rangeEndMs = lineEndTimeMs,
                style = backgroundStyle,
                fontWeight = FontWeight.Normal,
                reserveBoldWidth = false,
                highlightedColor = accentColor.copy(alpha = accentColor.alpha * BACKGROUND_VOCALS_HIGHLIGHT_ALPHA),
                unhighlightedColor = unsungWordColor,
                highlightStyle = wordHighlightStyle,
                emphasisEnabled = false,
                lyricsAlignment = lyricsAlignment,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (backgroundText.isNotBlank()) {
            Text(
                text = backgroundText,
                style = backgroundStyle,
                color = lineColor.copy(alpha = lineColor.alpha * BACKGROUND_VOCALS_HIGHLIGHT_ALPHA),
                textAlign = textAlign,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (showTranslation && !translationText.isNullOrBlank()) {
            Text(
                text = translationText,
                style = secondaryStyle,
                color = translationColor,
                textAlign = textAlign,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Backing vocals are drawn smaller and a little softer than the line they accompany. */
private const val BACKGROUND_VOCALS_FONT_SCALE = 0.72f
private const val BACKGROUND_VOCALS_HIGHLIGHT_ALPHA = 0.8f

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    romanization: String? = null,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
) {
    val sanitizedLines = remember(line) { line.split("\n") }
    val primaryText = remember(sanitizedLines) { if (sanitizedLines.isNotEmpty()) sanitizeLyricLineText(sanitizedLines[0]) else "" }

    val isRomanizedScript = remember(primaryText) {
        MultiLangRomanizer.isScriptThatNeedsRomanization(primaryText)
    }

    val translationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
        if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val rest = if (sanitizedLines.size > 2) sanitizedLines.drop(2).joinToString("\n") { sanitizeLyricLineText(it) } else ""
            
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin

            if (isFirstRomanization) rest else sanitizedLines.drop(1).joinToString("\n") { sanitizeLyricLineText(it) }
        } else ""
    }

    val embeddedRomanizationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
         if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin
            
            if (isFirstRomanization) sanitizeLyricLineText(firstExtra) else ""
        } else ""
    }
    val romanizationText = romanization?.takeIf { it.isNotBlank() } ?: embeddedRomanizationText
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val translationStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val translationColor = LocalContentColor.current.copy(alpha = 0.45f)

    val hapticFeedback = LocalHapticFeedback.current
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    // Plain lines have no tap action, so a bare long-press detector stands in for
    // combinedClickable and its tap ripple.
    val longClickModifier = if (onLongClick != null && primaryText.isNotBlank()) {
        Modifier
            .pointerInput(hapticFeedback) {
                detectTapGestures(
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongClick?.invoke()
                    }
                )
            }
            .semantics(mergeDescendants = true) {
                onLongClick(label = onLongClickLabel) {
                    currentOnLongClick?.invoke()
                    true
                }
            }
    } else {
        Modifier
    }

    Column(modifier = modifier.then(longClickModifier), horizontalAlignment = horizontalAlignment) {
        if (primaryText.isNotBlank()) {
            Text(
                text = primaryText,
                style = style,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )

            if (showRomanization && romanizationText.isNotBlank()) {
                Text(
                    text = romanizationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            if (showTranslation && translationText.isNotBlank()) {
                Text(
                    text = translationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (showRomanization && romanizationText.isNotBlank()) 2.dp else 4.dp)
                )
            }
        }
    }
}

private val KanaRange = '\u3040'..'\u30FF'

/** Romanizes each plain lyric line; null entries mean the line needs no romanization. */
private fun romanizePlainLyrics(lines: List<String>): List<String?> {
    val entireLyricsHasKana = lines.any { line -> line.any { it in KanaRange } }
    return lines.map { line ->
        val primary = sanitizeLyricLineText(line.substringBefore('\n'))
        if (MultiLangRomanizer.isScriptThatNeedsRomanization(primary)) {
            MultiLangRomanizer.romanizeLine(primary, entireLyricsHasKana)
        } else {
            null
        }
    }
}

private fun TextStyle.withLyricsFontSize(fontSizeSp: Float): TextStyle {
    val lineHeightRatio = if (fontSize.isSp && lineHeight.isSp && fontSize.value > 0f) {
        lineHeight.value / fontSize.value
    } else {
        DEFAULT_LYRICS_LINE_HEIGHT_RATIO
    }
    return copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightRatio).sp
    )
}

private const val DEFAULT_LYRICS_LINE_HEIGHT_RATIO = 1.18f

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

@Composable
private fun rememberInterpolatedPlaybackPosition(
    sampledPosition: State<Long>,
    isPlaying: Boolean,
    playbackSpeed: Float,
    positionOverrideMs: Long?
): State<Long> {
    val interpolatedPosition = remember {
        mutableLongStateOf(positionOverrideMs ?: sampledPosition.value)
    }

    LaunchedEffect(sampledPosition, isPlaying, playbackSpeed, positionOverrideMs) {
        positionOverrideMs?.let {
            interpolatedPosition.longValue = it.coerceAtLeast(0L)
            return@LaunchedEffect
        }

        val safePlaybackSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        snapshotFlow { sampledPosition.value }.collectLatest { sampledPositionMs ->
            val anchorPositionMs = sampledPositionMs.coerceAtLeast(0L)
            interpolatedPosition.longValue = anchorPositionMs
            if (!isPlaying) return@collectLatest

            val anchorFrameNanos = withFrameNanos { it }
            while (true) {
                val frameNanos = withFrameNanos { it }
                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000L).coerceAtLeast(0L)
                interpolatedPosition.longValue = anchorPositionMs + (elapsedMs * safePlaybackSpeed).roundToLong()
            }
        }
    }

    return interpolatedPosition
}

/**
 * A line stays current until the next one starts, or until its last syllable (main or, when
 * shown, backing vocals) has been sung when that runs later, so overlapping duet parts both
 * stay lit.
 */
internal fun resolveLineEndTimeMs(
    line: SyncedLine,
    nextLineStartMs: Int,
    includeBackground: Boolean = true
): Long {
    val words = line.words.orEmpty()
    val backgroundWords = if (includeBackground) line.background?.words.orEmpty() else emptyList()
    if (words.isEmpty() && backgroundWords.isEmpty()) {
        return maxOf(nextLineStartMs.toLong(), line.time + 1L)
    }
    var lastWordStart = Long.MIN_VALUE
    var lastWordEnd = Long.MIN_VALUE
    for (word in words) {
        lastWordStart = maxOf(lastWordStart, word.time.toLong())
        lastWordEnd = maxOf(lastWordEnd, (word.endTime ?: word.time).toLong())
    }
    for (word in backgroundWords) {
        lastWordStart = maxOf(lastWordStart, word.time.toLong())
        lastWordEnd = maxOf(lastWordEnd, (word.endTime ?: word.time).toLong())
    }
    return maxOf(nextLineStartMs.toLong(), lastWordStart + 1L, lastWordEnd)
}

/**
 * When each line stops being current, computed once per lyrics rather than every frame. A line
 * runs until the next line with a different start: lines that share a start (two singers at
 * once) end together instead of the first ending as soon as it begins.
 */
internal fun resolveLineEndTimesMs(
    lines: List<SyncedLine>,
    includeBackground: Boolean = true
): LongArray {
    val endTimes = LongArray(lines.size)
    var nextStartMs = Int.MAX_VALUE
    for (index in lines.indices.reversed()) {
        val line = lines[index]
        val following = lines.getOrNull(index + 1)
        if (following != null && following.time != line.time) nextStartMs = following.time
        endTimes[index] = resolveLineEndTimeMs(line, nextStartMs, includeBackground)
    }
    return endTimes
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long
): Int = resolveLitLines(lines, resolveLineEndTimesMs(lines), position).last

/**
 * The lines being sung: more than one while duet parts overlap or two singers start together.
 * [last] is the line the list follows; every lit line keeps full focus.
 */
internal data class LitLyricLines(val first: Int, val last: Int) {
    /** How many lines [index] is away from the lit ones; 0 inside them, large when none is lit. */
    fun distanceTo(index: Int): Int = when {
        last < 0 -> NOTHING_LIT_DISTANCE
        index < first -> first - index
        index > last -> index - last
        else -> 0
    }

    companion object {
        val None = LitLyricLines(first = -1, last = -1)
        private const val NOTHING_LIT_DISTANCE = 100
    }
}

/** Runs every frame while word-synced lyrics play, so it reads the precomputed [lineEndTimesMs]. */
internal fun resolveLitLines(
    lines: List<SyncedLine>,
    lineEndTimesMs: LongArray,
    position: Long
): LitLyricLines {
    var first = -1
    var last = -1
    for (index in lines.indices) {
        val endTimeMs = lineEndTimesMs.getOrNull(index) ?: break
        if (position >= lines[index].time && position < endTimeMs) {
            if (first < 0) first = index
            last = index
        }
    }
    return if (last < 0) LitLyricLines.None else LitLyricLines(first, last)
}

@Composable
private fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    isPlaying: Boolean
) {
    if (song == null) return

    val albumShape = CircleShape

    val currentRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        } else {
             currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = albumShape,
            contentDescription = stringResource(R.string.cd_cover_art),
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(albumShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.7f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )
    }
}
