package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.resolveNavBarOccupiedHeight
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.media.WaveformData
import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe
import com.lostf1sh.pixelplayeross.data.service.player.MixEditing
import com.lostf1sh.pixelplayeross.data.service.player.MixPlanner
import com.lostf1sh.pixelplayeross.presentation.components.ConnectedToggleButtonRow
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.viewmodel.MixEditorUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.MixEditorViewModel
import com.lostf1sh.pixelplayeross.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MixEditorScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: MixEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The app's floating nav bar and mini player sit over the screen; keep the last
    // controls scrollable above them.
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val useNavigationRail = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val bottomOverlay = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode, useNavigationRail) +
        (if (playerState.currentSong != null) MiniPlayerHeight else 0.dp) + 24.dp
    var showMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.stopPreview() } }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSaved()
            navController.navigateUp()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.dj_mix_editor_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = stringResource(R.string.dj_mix_beta),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = { navController.navigateUp() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dj_mix_close_cd))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.dj_mix_more_cd))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dj_mix_reset_auto)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.resetToAuto()
                                },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.isDirty,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(stringResource(R.string.dj_mix_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }

            state.loadFailed || state.mix == null || state.from == null || state.to == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.dj_mix_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> MixEditorContent(state, viewModel, bottomOverlay, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MixEditorContent(
    state: MixEditorUiState,
    viewModel: MixEditorViewModel,
    bottomOverlay: Dp,
    modifier: Modifier = Modifier,
) {
    val mix = checkNotNull(state.mix)
    val from = checkNotNull(state.from)
    val to = checkNotNull(state.to)
    val outgoingColor = MaterialTheme.colorScheme.primary
    val incomingColor = MaterialTheme.colorScheme.tertiary
    // Handed down as State (not read here): the playhead moves every frame during a preview and
    // only the timeline's draw step should react to it.
    val previewPosition = viewModel.previewPositionMs.collectAsStateWithLifecycle()
    val windowOptions = remember(state.from, state.fromBpm) { state.windowOptions.toImmutableList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SongHeader(song = from, bpm = state.fromBpm, accent = outgoingColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (state.isAnalyzing) {
                // Choosing the automatic mix from the songs' audio takes a moment the first time:
                // wait for it rather than show a stand-in graph that then changes by itself.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else {
                MixTimeline(
                    state = state,
                    mix = mix,
                    previewPositionMs = previewPosition,
                    outgoingColor = outgoingColor,
                    incomingColor = incomingColor,
                    onShiftOutgoing = viewModel::shiftOutgoing,
                    onShiftIncoming = viewModel::shiftIncoming,
                    onMoveKeyframe = viewModel::moveKeyframe,
                    onAddKeyframe = viewModel::addKeyframe,
                    onRemoveKeyframe = viewModel::removeKeyframe,
                    modifier = Modifier.fillMaxSize(),
                )

                // Where a lane has no waveform, say so (cloud songs, unsupported files).
                Column(Modifier.fillMaxSize()) {
                    WaveformPlaceholder(
                        loading = state.fromWaveformLoading && state.fromWaveform == null,
                        unavailable = !state.fromWaveformLoading && state.fromWaveform == null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    WaveformPlaceholder(
                        loading = state.toWaveformLoading && state.toWaveform == null,
                        unavailable = !state.toWaveformLoading && state.toWaveform == null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                FilledIconButton(
                    onClick = viewModel::togglePreview,
                    enabled = state.canPreview,
                    modifier = Modifier.align(Alignment.Center).size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (state.isPreviewPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPreviewPlaying) R.string.dj_mix_stop_preview_cd else R.string.dj_mix_play_preview_cd
                        ),
                    )
                }

                WindowChip(
                    mix = mix,
                    options = windowOptions,
                    onSelect = viewModel::selectWindow,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        }

        SongHeader(song = to, bpm = state.toBpm, accent = incomingColor)

        if (!state.canPreview) {
            Text(
                text = stringResource(R.string.dj_mix_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Text(
            text = stringResource(R.string.dj_mix_presets),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        val presets = remember { MixPreset.entries }
        val presetLabelTexts = presets.map { presetLabel(it) }
        val presetLabels = remember(presetLabelTexts) { presetLabelTexts.toImmutableList() }
        ConnectedToggleButtonRow(
            labels = presetLabels,
            selectedIndex = presets.indexOf(mix.preset),
            onSelect = { viewModel.applyPreset(presets[it]) },
            modifier = Modifier.fillMaxWidth(),
            // Five options share one row: no check mark, tighter padding, so labels fit whole.
            showCheck = false,
            contentPadding = PaddingValues(horizontal = 4.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.dj_mix_volume), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dj_mix_volume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(bottomOverlay))
    }
}

@Composable
private fun presetLabel(preset: MixPreset): String = stringResource(
    when (preset) {
        MixPreset.AUTO -> R.string.dj_mix_preset_auto
        MixPreset.CUSTOM -> R.string.dj_mix_preset_custom
        MixPreset.FADE -> R.string.dj_mix_preset_fade
        MixPreset.RISE -> R.string.dj_mix_preset_rise
        MixPreset.CUT -> R.string.dj_mix_preset_cut
    }
)

@Composable
private fun SongHeader(song: Song, bpm: Float?, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(44.dp).clip(CircleShape).background(accent))
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (bpm != null) {
                Text(stringResource(R.string.dj_mix_bpm, bpm.roundToInt()), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                formatDuration(song.duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WaveformPlaceholder(loading: Boolean, unavailable: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (loading) {
            // The waveform is being decoded; say so instead of leaving the lane blank.
            LoadingIndicator(modifier = Modifier.size(32.dp))
        } else if (unavailable) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            ) {
                Text(
                    text = stringResource(R.string.dj_mix_waveform_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WindowChip(
    mix: MixTransition,
    options: ImmutableList<MixPlanner.WindowOption>,
    onSelect: (MixPlanner.WindowOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(windowLabel(mix.bars, mix.windowMs))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(windowLabel(option.bars, option.windowMs)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun windowLabel(bars: Int?, windowMs: Long): String =
    if (bars != null) stringResource(R.string.dj_mix_bars_option, bars)
    else stringResource(R.string.dj_mix_seconds_option, (windowMs / 1000f).roundToInt())

// --- Timeline -------------------------------------------------------------------------------

/**
 * Maps the mix window (plus some context either side) onto the canvas. Time is milliseconds
 * relative to the window start; the top lane is the outgoing song, the bottom lane the incoming.
 */
private class TimelineGeometry(val width: Float, val height: Float, val windowMs: Long, val lanePad: Float) {
    val padMs = windowMs * CONTEXT_FRACTION
    val totalMs = windowMs + 2 * padMs
    val laneHeight = height / 2f

    /** Height the volume line really spans: the lane minus a margin so edge points stay whole. */
    val lineSpan = laneHeight - 2 * lanePad

    fun xOf(relMs: Float) = (relMs + padMs) / totalMs * width
    fun relOf(x: Float) = x / width * totalMs - padMs
    fun laneTop(outgoing: Boolean) = if (outgoing) 0f else laneHeight
    fun yOf(outgoing: Boolean, v: Float) = laneTop(outgoing) + lanePad + (1f - v) * lineSpan
    fun vOf(outgoing: Boolean, y: Float) = 1f - (y - laneTop(outgoing) - lanePad) / lineSpan
    fun isOutgoingLane(y: Float) = y < laneHeight

    /** Pixels per unit of keyframe time (0..1 across the window). */
    val pxPerT get() = width * windowMs / totalMs

    companion object {
        const val CONTEXT_FRACTION = 0.3f
    }
}

private sealed interface TimelineDrag {
    data class Keyframe(val outgoing: Boolean, val index: Int) : TimelineDrag
    data class Lane(val outgoing: Boolean) : TimelineDrag
}

@Composable
private fun MixTimeline(
    state: MixEditorUiState,
    mix: MixTransition,
    previewPositionMs: State<Long?>,
    outgoingColor: Color,
    incomingColor: Color,
    onShiftOutgoing: (Long) -> Unit,
    onShiftIncoming: (Long) -> Unit,
    onMoveKeyframe: (outgoing: Boolean, index: Int, t: Float, v: Float) -> Unit,
    onAddKeyframe: (outgoing: Boolean, t: Float, v: Float) -> Unit,
    onRemoveKeyframe: (outgoing: Boolean, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentMix by rememberUpdatedState(mix)
    val onShiftOut by rememberUpdatedState(onShiftOutgoing)
    val onShiftIn by rememberUpdatedState(onShiftIncoming)
    val onMove by rememberUpdatedState(onMoveKeyframe)
    val onAdd by rememberUpdatedState(onAddKeyframe)
    val onRemove by rememberUpdatedState(onRemoveKeyframe)
    val hitRadiusPx = with(density) { 28.dp.toPx() }
    val lanePadPx = with(density) { 16.dp.toPx() }

    val waveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val windowTint = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val handleRing = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                var drag: TimelineDrag? = null
                detectDragGestures(
                    onDragStart = { pos ->
                        val m = currentMix
                        val g = TimelineGeometry(size.width.toFloat(), size.height.toFloat(), m.windowMs, lanePadPx)
                        val outgoing = g.isOutgoingLane(pos.y)
                        val line = if (outgoing) m.outVolume else m.inVolume
                        val hit = MixEditing.hitKeyframe(
                            line,
                            t = g.relOf(pos.x) / m.windowMs,
                            v = g.vOf(outgoing, pos.y),
                            maxDt = hitRadiusPx / g.pxPerT,
                            maxDv = hitRadiusPx / g.lineSpan,
                        )
                        drag = if (hit >= 0) TimelineDrag.Keyframe(outgoing, hit) else TimelineDrag.Lane(outgoing)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val m = currentMix
                        val g = TimelineGeometry(size.width.toFloat(), size.height.toFloat(), m.windowMs, lanePadPx)
                        when (val d = drag) {
                            is TimelineDrag.Keyframe -> onMove(
                                d.outgoing,
                                d.index,
                                g.relOf(change.position.x) / m.windowMs,
                                g.vOf(d.outgoing, change.position.y),
                            )
                            // Dragging pulls the song's waveform with the finger, so the point
                            // under the (fixed) window moves the opposite way in song time.
                            is TimelineDrag.Lane -> {
                                val deltaMs = (-amount.x / g.width * g.totalMs).toLong()
                                if (deltaMs != 0L) {
                                    if (d.outgoing) onShiftOut(deltaMs) else onShiftIn(deltaMs)
                                }
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = { drag = null },
                    onDragCancel = { drag = null },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { pos ->
                        val m = currentMix
                        val g = TimelineGeometry(size.width.toFloat(), size.height.toFloat(), m.windowMs, lanePadPx)
                        val outgoing = g.isOutgoingLane(pos.y)
                        val line = if (outgoing) m.outVolume else m.inVolume
                        val t = g.relOf(pos.x) / m.windowMs
                        val v = g.vOf(outgoing, pos.y)
                        val hit = MixEditing.hitKeyframe(
                            line, t, v,
                            maxDt = hitRadiusPx / g.pxPerT,
                            maxDv = hitRadiusPx / g.lineSpan,
                        )
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (hit >= 0) onRemove(outgoing, hit) else if (t in 0f..1f) onAdd(outgoing, t, v)
                    },
                )
            },
    ) {
        val g = TimelineGeometry(size.width, size.height, mix.windowMs, lanePadPx)

        // The mix window.
        drawRect(
            color = windowTint.copy(alpha = 0.07f),
            topLeft = Offset(g.xOf(0f), 0f),
            size = androidx.compose.ui.geometry.Size(g.xOf(mix.windowMs.toFloat()) - g.xOf(0f), size.height),
        )

        // Bar grid, from the outgoing song's tempo, counted from the window start.
        state.fromBpm?.let { bpm ->
            val barMs = 4 * 60_000f / bpm
            var rel = -(g.padMs / barMs).toInt() * barMs
            while (rel <= g.windowMs + g.padMs) {
                val x = g.xOf(rel)
                drawLine(gridColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                rel += barMs
            }
        }

        drawWaveform(g, state.fromWaveform, mix.outPointMs, outgoing = true, color = waveColor, windowMs = mix.windowMs)
        drawWaveform(g, state.toWaveform, mix.inStartMs, outgoing = false, color = waveColor, windowMs = mix.windowMs)

        // Lane divider.
        drawLine(gridColor, Offset(0f, g.laneHeight), Offset(size.width, g.laneHeight), strokeWidth = 2f)

        drawVolumeLine(g, mix.outVolume, outgoing = true, color = outgoingColor, ring = handleRing)
        drawVolumeLine(g, mix.inVolume, outgoing = false, color = incomingColor, ring = handleRing)

        // Playhead: the window start when idle, the audible position while previewing.
        val previewMs = previewPositionMs.value
        val playheadX = g.xOf((previewMs ?: 0L).toFloat())
        drawLine(
            color = playheadColor.copy(alpha = if (previewMs != null) 1f else 0.5f),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawWaveform(
    g: TimelineGeometry,
    waveform: WaveformData?,
    songTimeAtWindowStartMs: Long,
    outgoing: Boolean,
    color: Color,
    windowMs: Long,
) {
    if (waveform == null) return
    val barWidth = 3.dp.toPx()
    val step = 4.dp.toPx()
    val centerY = g.laneTop(outgoing) + g.laneHeight / 2f
    val maxHalf = g.laneHeight * 0.42f
    var x = 0f
    while (x < size.width) {
        val rel = g.relOf(x)
        val songMs = songTimeAtWindowStartMs + rel.toLong()
        val peak = if (songMs < 0) 0f else waveform.peakAtMs(songMs)
        // The outgoing song stops after the window, the incoming one hasn't started before it.
        val audible = if (outgoing) rel <= windowMs else rel >= 0f
        val half = (sqrt(peak) * maxHalf).coerceAtLeast(1.5.dp.toPx())
        drawRoundRect(
            color = color.copy(alpha = if (audible) 0.45f else 0.15f),
            topLeft = Offset(x, centerY - half),
            size = androidx.compose.ui.geometry.Size(barWidth, half * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
        )
        x += step
    }
}

private fun DrawScope.drawVolumeLine(
    g: TimelineGeometry,
    line: List<VolumeKeyframe>,
    outgoing: Boolean,
    color: Color,
    ring: Color,
) {
    if (line.isEmpty()) return
    val points = line.map { Offset(g.xOf(it.t * g.windowMs), g.yOf(outgoing, it.v)) }
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, points.first().y)
        points.forEach { lineTo(it.x, it.y) }
        lineTo(size.width, points.last().y)
    }
    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    points.forEach {
        drawCircle(ring, radius = 9.dp.toPx(), center = it)
        drawCircle(color, radius = 6.dp.toPx(), center = it)
    }
}
