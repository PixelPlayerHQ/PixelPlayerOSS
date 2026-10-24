@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.recap

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.RecapViewModel
import com.lostf1sh.pixelplayeross.ui.theme.ShapeCache
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** How long a slide stays before the story moves on by itself. */
private const val SLIDE_DURATION_MS = 7_000

/** Taps on this share of the width, on the reading-start side, go back a slide. */
private const val PREVIOUS_TAP_ZONE = 0.3f

/** Space the progress bar and buttons take above the slides, below the status bar. */
private val ChromeHeight = 72.dp

/** Runs the slide timer at its real length whatever the system animator duration scale. */
private object UnscaledMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** Keeps each slide's scroll position across configuration changes, as rememberScrollState does. */
private val SlideScrollStatesSaver = listSaver<List<ScrollState>, Int>(
    save = { states -> states.map { it.value } },
    restore = { positions -> positions.map { ScrollState(initial = it) } }
)

/**
 * The year's listening as a full-screen story: tap the end side (or swipe) for the next slide,
 * the start side for the previous one, and hold to pause. Slides advance by themselves unless
 * animations are off or touch exploration is on, where only explicit navigation moves on.
 */
@Composable
fun RecapScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    recapViewModel: RecapViewModel = hiltViewModel()
) {
    val uiState by recapViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onClose: () -> Unit = { navController.popBackStack() }

    when (val state = uiState) {
        RecapUiState.Loading -> RecapStatusPage(onClose = onClose) {
            ContainedLoadingIndicator()
        }

        is RecapUiState.NotEnoughData -> RecapStatusPage(onClose = onClose) {
            RecapNotEnoughData(year = state.year, onClose = onClose)
        }

        is RecapUiState.Ready -> {
            val queueName = stringResource(R.string.recap_top_songs_queue_name, state.model.year)
            val shareFailed = stringResource(R.string.recap_share_failed)
            val scope = rememberCoroutineScope()
            RecapStory(
                model = state.model,
                onClose = onClose,
                onShare = { layer ->
                    scope.launch {
                        try {
                            if (!layer.hasContent) error("Summary card has not been drawn yet")
                            shareRecapImage(context, layer, state.model.year)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Exception) {
                            Timber.e(throwable, "Failed to share the %d recap", state.model.year)
                            Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onPlayTopSongs = {
                    val queue = recapViewModel.topSongsQueue
                    if (queue.isNotEmpty()) {
                        playerViewModel.playSongs(queue, queue.first(), queueName = queueName)
                        // The mini player is hidden while the story is open; closing it shows
                        // what just started.
                        onClose()
                    }
                }
            )
        }
    }
}

@Composable
private fun RecapStory(
    model: RecapUiModel,
    onClose: () -> Unit,
    onShare: (GraphicsLayer) -> Unit,
    onPlayTopSongs: () -> Unit
) {
    val slides = model.slides
    val lastIndex = slides.lastIndex
    val pagerState = rememberPagerState { slides.size }
    val palettes = rememberRecapPalettes()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val motionEnabled = rememberRecapMotionEnabled()
    val touchExploration = rememberTouchExplorationEnabled()
    val autoAdvance = motionEnabled && !touchExploration
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var isHeld by remember { mutableStateOf(false) }
    var isPausedByUser by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    // The timer's move to the next slide, kept so pausing or leaving the app can stop it mid-way.
    // Only read in coroutines, so setting it never recomposes the story.
    val autoMove = remember { mutableStateOf<Job?>(null) }
    // Hoisted out of the pages so the timer can tell when a slide that does not fit (landscape,
    // large fonts) is being scrolled.
    val slideScrollStates = rememberSaveable(slides.size, saver = SlideScrollStatesSaver) {
        List(slides.size) { ScrollState(initial = 0) }
    }

    // Each settled slide restarts the timer; pausing (holding, the pause button, leaving the
    // app, dragging or scrolling a slide) freezes it where it is, and resuming runs only the
    // time that is left. The flags are read in snapshotFlow, so holding a slide never
    // recomposes the story.
    LaunchedEffect(pagerState, autoAdvance) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            if (!autoAdvance) {
                progress.snapTo(1f)
                return@collectLatest
            }
            progress.snapTo(0f)
            snapshotFlow {
                isHeld ||
                    isPausedByUser ||
                    !lifecycleState.isAtLeast(Lifecycle.State.RESUMED) ||
                    pagerState.isScrollInProgress ||
                    slideScrollStates[page].isScrollInProgress
            }
                .distinctUntilChanged()
                .collectLatest { blocked ->
                    if (blocked) return@collectLatest
                    // A full bar while this slide is still the settled one means its move to the
                    // next slide was undone (tapped or dragged back mid-way): give it its time
                    // again rather than moving straight on.
                    if (progress.value >= 1f && page < lastIndex) progress.snapTo(0f)
                    val remaining = ((1f - progress.value) * SLIDE_DURATION_MS).roundToInt()
                    if (remaining > 0) {
                        // Reading time, not decoration: the system animation speed must not
                        // stretch or shorten it.
                        withContext(UnscaledMotion) {
                            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        }
                    }
                    // Scrolling flips isScrollInProgress, which would cancel this collector.
                    if (page < lastIndex) {
                        autoMove.value = scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                }
        }
    }

    // The move runs outside the timer so it is not cancelled by its own scrolling; stop it here
    // when the story is paused or the app is left, and settle on the nearer slide rather than
    // leaving the story between two. A slide that stays gets its time again (see above).
    LaunchedEffect(pagerState) {
        snapshotFlow { isPausedByUser || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED) }
            .filter { it }
            .collect {
                val move = autoMove.value?.takeIf { it.isActive } ?: return@collect
                move.cancelAndJoin()
                pagerState.scrollToPage(pagerState.currentPage)
            }
    }

    fun goTo(page: Int) {
        if (page !in slides.indices) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val nextLabel = stringResource(R.string.recap_action_next_slide)
    val previousLabel = stringResource(R.string.recap_action_previous_slide)
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val slidePadding = PaddingValues(
        top = safeDrawing.calculateTopPadding() + ChromeHeight,
        bottom = safeDrawing.calculateBottomPadding() + 24.dp
    )
    val summaryActions = remember(onShare, onPlayTopSongs) {
        RecapSummaryActions(
            onShare = onShare,
            onPlayTopSongs = onPlayTopSongs,
            onReplay = { scope.launch { pagerState.scrollToPage(0) } }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pager offsets are only read here, in the draw phase: the background blends
            // between neighbouring slides' colours as they are swiped.
            .drawBehind {
                val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, lastIndex.toFloat())
                val from = position.toInt()
                val to = (from + 1).coerceAtMost(lastIndex)
                drawRect(lerp(palettes.forPage(from).background, palettes.forPage(to).background, position - from))
            }
    ) {
        HorizontalPager(
            state = pagerState,
            key = { slides[it].key },
            beyondViewportPageCount = 0,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) {
                            goTo(pagerState.currentPage + 1)
                            true
                        },
                        CustomAccessibilityAction(previousLabel) {
                            goTo(pagerState.currentPage - 1)
                            true
                        }
                    )
                }
                // Buttons inside slides consume their own taps first, so these only see taps
                // on the slide itself.
                .pointerInput(slides.size, isRtl) {
                    detectTapGestures(
                        onPress = {
                            isHeld = true
                            tryAwaitRelease()
                            isHeld = false
                        },
                        // Holding pauses; letting go must not also skip the slide.
                        onLongPress = { },
                        onTap = { offset ->
                            val startZone = size.width * PREVIOUS_TAP_ZONE
                            val onStartSide = if (isRtl) {
                                offset.x > size.width - startZone
                            } else {
                                offset.x < startZone
                            }
                            val from = pagerState.targetPage
                            goTo(if (onStartSide) from - 1 else from + 1)
                        }
                    )
                }
        ) { page ->
            val palette = palettes.forPage(page)
            val isCurrent = pagerState.currentPage == page
            val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
            LaunchedEffect(isCurrent, motionEnabled) {
                when {
                    !motionEnabled -> reveal.snapTo(1f)
                    isCurrent -> reveal.animateTo(1f, tween(RECAP_REVEAL_DURATION_MS, easing = LinearEasing))
                }
            }
            val isActive = motionEnabled && isResumed && isCurrent && pagerState.settledPage == page
            CompositionLocalProvider(LocalContentColor provides palette.content) {
                RecapSlideContent(
                    slide = slides[page],
                    palette = palette,
                    reveal = { reveal.value },
                    isActive = isActive,
                    contentPadding = slidePadding,
                    scrollState = slideScrollStates[page],
                    summaryActions = summaryActions
                )
            }
        }

        RecapChrome(
            pagerState = pagerState,
            slideCount = slides.size,
            palettes = palettes,
            progress = { progress.value },
            showPauseButton = autoAdvance,
            isPaused = isPausedByUser,
            onTogglePause = { isPausedByUser = !isPausedByUser },
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/** Progress segments plus pause and close, tinted for whichever slide is showing. */
@Composable
private fun RecapChrome(
    pagerState: PagerState,
    slideCount: Int,
    palettes: ImmutableList<RecapPalette>,
    progress: () -> Float,
    showPauseButton: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPalette = palettes.forPage(pagerState.currentPage)
    val tint by animateColorAsState(currentPalette.content, label = "recapChromeTint")
    RecapSystemBarsAppearance(lightBackground = currentPalette.background.luminance() > 0.5f)

    val progressDescription = stringResource(
        R.string.recap_cd_slide_progress,
        pagerState.currentPage + 1,
        slideCount
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp)
    ) {
        RecapProgressBar(
            count = slideCount,
            // The timer follows the settled page, so the bar does too (not mid-swipe pages).
            currentPage = { pagerState.settledPage },
            progress = progress,
            color = { tint },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .height(4.dp)
                .semantics { contentDescription = progressDescription }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = tint)
            if (showPauseButton) {
                IconButton(onClick = onTogglePause, colors = buttonColors) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(
                            if (isPaused) R.string.recap_cd_resume else R.string.recap_cd_pause
                        )
                    )
                }
            }
            IconButton(onClick = onClose, colors = buttonColors) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.recap_cd_close)
                )
            }
        }
    }
}

/** Plain page for loading and empty states, with a way out. */
@Composable
private fun RecapStatusPage(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.recap_cd_close))
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            content()
        }
    }
}

@Composable
private fun RecapNotEnoughData(year: Int, onClose: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = RecapGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(ShapeCache.expressiveHero)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = stringResource(R.string.recap_not_enough_title),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = rememberRecapFlexFont(weight = 760, width = 112f),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.recap_not_enough_subtitle, year),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = onClose) {
            Text(stringResource(R.string.recap_close))
        }
    }
}

/** Matches status and navigation bar icons to the slide colour, and restores them on exit. */
@Composable
private fun RecapSystemBarsAppearance(lightBackground: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    if (window == null || view.isInEditMode) return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
    // Every composition, so it also wins over the theme re-applying its own appearance.
    SideEffect {
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
    }
}

/** True while a screen reader explores by touch; stories must not move on under it. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
