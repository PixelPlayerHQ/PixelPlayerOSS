package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.util.lerp as lerpFloat
import com.lostf1sh.pixelplayeross.presentation.utils.SegmentedItemShapeDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.AutoScrollingText
import com.lostf1sh.pixelplayeross.presentation.components.ShimmerBox
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/**
 * Morphs the corners of a segmented [base] shape into a fully rounded container as
 * [progress] goes from 0 to 1. Corner sizes are resolved per-measure, so percent-based
 * corners and any item height morph correctly.
 */
private class HighlightMorphShape(
    private val base: CornerBasedShape,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val full = size.minDimension / 2f
        fun corner(cornerSize: CornerSize): CornerSize =
            CornerSize(lerpFloat(cornerSize.toPx(size, density), full, progress))
        return base.copy(
            topStart = corner(base.topStart),
            topEnd = corner(base.topEnd),
            bottomEnd = corner(base.bottomEnd),
            bottomStart = corner(base.bottomStart),
        ).createOutline(size, layoutDirection, density)
    }
}

/**
 * Enhanced song list item with multi-selection support.
 * 
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Callback for more options button
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 50.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() }
    val isHighlighted = isCurrentSong && !isLoading
    val colors = MaterialTheme.colorScheme

    val highlightProgress by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "highlightProgress"
    )
    val selectionVisualProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "selectionVisualProgress"
    )
    val selectionScaleProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScaleProgress"
    )

    val isAnimating = highlightProgress > 0.001f || selectionVisualProgress > 0.001f

    val surfaceShape = when {
        !isAnimating && customShape != null -> customShape
        !isAnimating -> SegmentedItemShapeDefaults.SongSingleShape
        customShape is CornerBasedShape && highlightProgress > 0f ->
            HighlightMorphShape(customShape, highlightProgress)
        highlightProgress > 0f ->
            RoundedCornerShape(lerpDp(24.dp, 50.dp, highlightProgress))
        else -> customShape ?: SegmentedItemShapeDefaults.SongSingleShape
    }

    val albumShape = if (highlightProgress > 0.001f) {
        RoundedCornerShape(lerpDp(10.dp, 50.dp, highlightProgress))
    } else {
        SegmentedItemShapeDefaults.AlbumArtDefaultShape
    }

    val baseContainerColor = containerColorOverride ?: colors.surfaceContainerLow
    val containerColor = if (isAnimating) {
        val playbackContainerColor = lerpColor(baseContainerColor, colors.primaryContainer, highlightProgress)
        lerpColor(playbackContainerColor, colors.secondaryContainer, selectionVisualProgress)
    } else {
        baseContainerColor
    }

    val baseContentColor = colors.onSurface
    val contentColor = if (isAnimating) {
        val playbackContentColor = lerpColor(baseContentColor, colors.onPrimaryContainer, highlightProgress)
        lerpColor(playbackContentColor, colors.onSecondaryContainer, selectionVisualProgress)
    } else {
        baseContentColor
    }

    val selectionScale = if (selectionScaleProgress > 0.001f) lerpFloat(1f, 0.98f, selectionScaleProgress) else 1f
    val selectionBorderWidth = if (selectionVisualProgress > 0.001f) lerpDp(0.dp, 2.5.dp, selectionVisualProgress) else 0.dp
    val selectionBorderColor = if (selectionVisualProgress > 0.001f) lerpColor(colors.primary.copy(alpha = 0f), colors.primary, selectionVisualProgress) else Color.Transparent
    val mvContainerColor = if (highlightProgress > 0.001f) lerpColor(colors.onSurface, colors.primaryContainer, highlightProgress) else colors.onSurface
    val mvContentColor = if (highlightProgress > 0.001f) lerpColor(colors.surfaceContainerHigh, colors.onPrimaryContainer, highlightProgress) else colors.surfaceContainerHigh
    val selectionOverlayColor = if (selectionVisualProgress > 0.001f) lerpColor(Color.Transparent, colors.primary.copy(alpha = 0.7f), selectionVisualProgress) else Color.Transparent
    val selectionOverlayContentColor = if (selectionVisualProgress > 0.001f) lerpColor(Color.Transparent, colors.onPrimary, selectionVisualProgress) else Color.Transparent
    val showSelectionDecoration = selectionVisualProgress > 0.001f

    if (isLoading) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = containerColorOverride ?: colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else 4.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .scale(selectionScale)
                .clip(surfaceShape)
                .then(
                    if (showSelectionDecoration) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick {
                        if (isSelectionMode) {
                            onLongPress()
                        } else {
                            onClick()
                        }
                        true
                    }
                    onLongClick { onLongPress(); true }
                }
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) {
                                onLongPress()
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            onLongPress()
                        }
                    )
                },
            shape = surfaceShape,
            color = containerColor,
            // The animated container is rarely an exact theme colour, so the default
            // contentColorFor() would fall back to the parent's LocalContentColor.
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = null,
                            shape = albumShape,
                            targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = selectionOverlayColor,
                                        shape = albumShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = selectionOverlayContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.presentation_batch_g_list_cd_selected),
                                        tint = selectionOverlayContentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (isHighlighted && !isSelectionMode) {
                        AutoScrollingText(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            gradientEdgeColor = containerColor,
                        )

                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = contentColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = showMoreOptionsButton && !isSelectionMode

                if (showPlayingIndicator) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }

                if (showPlayingIndicator || showTrailingAction) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (showTrailingAction) {
                    FilledIconButton(
                        onClick = { onMoreOptionsClick(song) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = mvContentColor,
                            contentColor = mvContainerColor
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.presentation_batch_g_list_cd_more_for_title, song.title),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
