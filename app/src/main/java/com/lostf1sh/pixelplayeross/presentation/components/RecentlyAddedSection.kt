package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.collections.immutable.ImmutableList

private const val HomeRecentlyAddedLimit = 20
private val HomeRecentlyAddedCardWidth = 136.dp
private val HomeRecentlyAddedArtTargetSize = Size(320, 320)

@Composable
fun RecentlyAddedSection(
    songs: ImmutableList<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    currentSongId: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    modifier: Modifier = Modifier
) {
    val visibleSongs = remember(songs) { songs.take(HomeRecentlyAddedLimit) }
    if (visibleSongs.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = stringResource(R.string.home_recently_added_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            FilledIconButton(
                modifier = Modifier
                    .height(40.dp)
                    .width(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                onClick = onPlayAllClick
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_recently_added_play_cd),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = visibleSongs,
                key = { it.id },
                contentType = { "recently_added_card" }
            ) { song ->
                RecentlyAddedSongCard(
                    song = song,
                    isCurrentSong = currentSongId == song.id,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedSongCard(
    song: Song,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    val containerColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val titleColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val artistColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (isCurrentSong) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = Modifier
            .width(HomeRecentlyAddedCardWidth)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = stringResource(R.string.cd_album_art_for_title, song.title),
                targetSize = HomeRecentlyAddedArtTargetSize,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(HomeRecentlyAddedCardWidth - 16.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
