package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

/**
 * Header component displayed during multi-selection mode.
 * Shows stacked cover arts and action buttons for batch operations.
 *
 * @param selectedSongs List of selected songs (order preserved)
 * @param onPlayClick Callback to play all selected songs
 * @param onLikeClick Callback to like all selected songs  
 * @param onShareClick Callback to share all selected songs
 * @param modifier Modifier for the header
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionHeader(
    selectedSongs: List<Song>,
    onPlayClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Stacked cover arts + count
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stacked album arts (up to 5, overlapping)
            StackedCoverArts(
                songs = selectedSongs.take(5),
                totalCount = selectedSongs.size
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Selection count with animated number
            Column {
                AnimatedContent(
                    targetState = selectedSongs.size,
                    transitionSpec = {
                        if (targetState > initialState) {
                            // Counting up
                            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn() togetherWith
                                scaleOut() + fadeOut()
                        } else {
                            // Counting down
                            scaleIn() + fadeIn() togetherWith
                                scaleOut() + fadeOut()
                        }
                    },
                    label = "countAnimation"
                ) { count ->
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (selectedSongs.size == 1) stringResource(R.string.presentation_batch_g_selection_one_song) else stringResource(R.string.presentation_batch_g_selection_n_songs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = RoundedSans
                )
            }
        }
        
        // Right side: Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Share button
            FilledTonalIconButton(
                onClick = onShareClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.presentation_batch_g_selection_cd_share),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Like button
            FilledIconButton(
                onClick = onLikeClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.presentation_batch_g_selection_cd_like),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Play button (larger, primary action)
            MediumExtendedFloatingActionButton(
                onClick = onPlayClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.presentation_batch_g_selection_play),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RoundedSans
                )
            }
        }
    }
}

/**
 * Displays stacked circular album art images with overlap effect.
 */
@Composable
private fun StackedCoverArts(
    songs: List<Song>,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val imageSize = 48.dp
    val overlap = 20.dp // How much images overlap
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        songs.forEachIndexed { index, song ->
            val offsetX = index * (imageSize.value - overlap.value)
            val scale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "coverScale$index"
            )
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex((songs.size - index).toFloat())
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .size(imageSize)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    shape = CircleShape,
                    targetSize = Size(144, 144),
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        
        // Show "+N" indicator if more songs are selected than displayed
        if (totalCount > songs.size) {
            val offsetX = songs.size * (imageSize.value - overlap.value)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex(0f)
                    .size(imageSize)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        R.string.presentation_batch_g_selection_overflow_count,
                        totalCount - songs.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontFamily = RoundedSans
                )
            }
        }
    }
}
