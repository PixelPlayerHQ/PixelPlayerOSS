package com.lostf1sh.pixelplayeross.presentation.screens.search.components

import androidx.annotation.OptIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.Genre
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.getNavigationBarHeight
import com.lostf1sh.pixelplayeross.presentation.components.resolveNavBarOccupiedHeight
import com.lostf1sh.pixelplayeross.presentation.utils.GenreIconProvider
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.collections.immutable.ImmutableList

/**
 * The genre tiles shown by Search and by the Library's Genres tab.
 *
 * Search keeps the defaults. The Library tab hides the [showHeader] row (its grid/list toggle
 * lives in the sort sheet there), shows each genre's song count, and passes its own
 * [horizontalPadding], [contentPadding] and [gridState] to line up with the other library tabs.
 */
@OptIn(UnstableApi::class)
@Composable
fun GenreCategoriesGrid(
    genres: ImmutableList<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedGenreIds: Set<String> = emptySet(),
    onGenreLongPress: (Genre) -> Unit = {},
    onGenreSelectionToggle: (Genre) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    showHeader: Boolean = true,
    showSongCount: Boolean = false,
    horizontalPadding: Dp = 18.dp,
    contentPadding: PaddingValues? = null,
    gridState: LazyGridState = rememberLazyGridState()
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_genres_available), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val systemNavBarHeight = getNavigationBarHeight()
    val customGenreIcons = playerViewModel.customGenreIcons.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
        context = kotlin.coroutines.EmptyCoroutineContext
    ).value

    val isGridView by playerViewModel.isGenreGridView.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 70,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 70
            )),
        contentPadding = contentPadding ?: PaddingValues(
            top = 8.dp,
            bottom = 28.dp + resolveNavBarOccupiedHeight(systemNavBarHeight, navBarCompactMode) + MiniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeader) item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.browse_by_genre),
                    style = MaterialTheme.typography.titleLarge
                )
                
                val shape = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (!isGridView) 12f else 50f,
                    label = "shapeAnimation"
                )
                
                androidx.compose.material3.FilledIconButton(
                    onClick = { playerViewModel.toggleGenreViewMode() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(shape.value.dp)
                ) {
                androidx.compose.material3.Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = stringResource(R.string.cd_toggle_grid_list)
                    )
                }
            }
        }
        items(genres, key = { it.id }) { genre ->
            GenreCard(
                genre = genre,
                customIcons = customGenreIcons,
                onClick = { onGenreClick(genre) },
                isGridView = isGridView,
                isSelectionMode = isSelectionMode,
                isSelected = genre.id in selectedGenreIds,
                selectionIndex = getSelectionIndex(genre.id),
                onLongPress = { onGenreLongPress(genre) },
                onSelectionToggle = { onGenreSelectionToggle(genre) },
                showSongCount = showSongCount
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    customIcons: Map<String, Int>,
    onClick: () -> Unit,
    isGridView: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    selectionIndex: Int?,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit,
    showSongCount: Boolean
) {
    val isDark = LocalPixelPlayerDarkTheme.current
    val themeColor = remember(genre, isDark) {
        com.lostf1sh.pixelplayeross.ui.theme.GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = isDark,
            fallbackGenreId = genre.id
        )
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    val shape = RoundedCornerShape(20.dp)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "genreCardSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "genreCardSelectionBorder"
    )

    val cardModifier = if (isGridView) {
        Modifier.aspectRatio(1.2f)
    } else {
        Modifier.fillMaxWidth().height(100.dp)
    }

    Card(
        modifier = cardModifier
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .combinedClickable(
                onClick = if (isSelectionMode) onSelectionToggle else onClick,
                onLongClick = onLongPress
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
        ) {
            val titleStartPadding = 14.dp
            val titleEndPadding = if (isGridView) 14.dp else 96.dp
            val titlePresentation = remember(
                genre.id,
                genre.name,
                isGridView,
            ) {
                GenreTypography.resolveTitlePresentationFast(
                    genreId = genre.id,
                    genreName = genre.name,
                    isGridView = isGridView,
                )
            }

            Box(
                modifier = Modifier
                    .size(90.dp) 
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 16.dp) 
            ) {
                SmartImage(
                    model = GenreIconProvider.getGenreImageResource(genre.name, customIcons),
                    contentDescription = stringResource(R.string.cd_genre_illustration),
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    colorFilter = ColorFilter.tint(onBackgroundColor),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = titleStartPadding, top = 14.dp, end = titleEndPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Shrinks within bounds and wraps between words to fit the tile; ellipsizes
                // only when the name still does not fit at the smallest size.
                Text(
                    text = titlePresentation.text,
                    style = titlePresentation.style,
                    color = onBackgroundColor,
                    autoSize = titlePresentation.autoSize,
                    maxLines = titlePresentation.maxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showSongCount && genre.songCount > 0) {
                    Text(
                        text = formatSongCount(genre.songCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = onBackgroundColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex != null && selectionIndex >= 0) {
                        Text(
                            text = selectionIndex.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
