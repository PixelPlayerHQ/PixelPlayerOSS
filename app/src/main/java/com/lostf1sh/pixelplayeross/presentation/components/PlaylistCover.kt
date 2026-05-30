package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.PlaylistShapeType
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.resolvePlaylistCoverContentColor
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun PlaylistCover(
    playlist: Playlist,
    playlistSongs: List<Song>,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val shape = remember(playlist.coverShapeType, playlist.coverShapeDetail1, playlist.coverShapeDetail2, playlist.coverShapeDetail3, size) {
        when (playlist.coverShapeType) {
            PlaylistShapeType.Circle.name -> CircleShape
            PlaylistShapeType.SmoothRect.name -> {
                val referenceSize = 200f
                val currentSize = size.value
                val scale = currentSize / referenceSize
                val r = ((playlist.coverShapeDetail1 ?: 20f) * scale).dp
                val s = (playlist.coverShapeDetail2 ?: 60f).toInt()
                AbsoluteSmoothCornerShape(r, s, r, s, r, s, r, s)
            }
            PlaylistShapeType.RotatedPill.name -> {
                androidx.compose.foundation.shape.GenericShape { size, _ ->
                    val w = size.width
                    val h = size.height
                    val pillW = w * 0.75f
                    val offset = (w - pillW) / 2
                    addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                }
            }
            PlaylistShapeType.Star.name -> RoundedStarShape(
                sides = (playlist.coverShapeDetail4 ?: 5f).toInt(),
                curve = (playlist.coverShapeDetail1 ?: 0.15f).toDouble(),
                rotation = playlist.coverShapeDetail2 ?: 0f
            )
            else -> RoundedCornerShape(8.dp)
        }
    }

    val shapeMod = if (playlist.coverShapeType == PlaylistShapeType.RotatedPill.name) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
    val iconMod = if (playlist.coverShapeType == PlaylistShapeType.RotatedPill.name) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
    val scaleMod = if (playlist.coverShapeType == PlaylistShapeType.Star.name) {
        val s = playlist.coverShapeDetail3 ?: 1f
        Modifier.graphicsLayer(scaleX = s, scaleY = s)
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .then(scaleMod)
            .then(shapeMod)
            .clip(shape)
    ) {
        if (playlist.coverImageUri != null) {
            AsyncImage(
                model = playlist.coverImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (playlist.coverColorArgb != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(playlist.coverColorArgb)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconByName(playlist.coverIconName) ?: Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = resolvePlaylistCoverContentColor(
                        playlist.coverColorArgb,
                        MaterialTheme.colorScheme
                    ),
                    modifier = Modifier.size(size / 2).then(iconMod)
                )
            }
        } else {
            PlaylistArtCollage(
                songs = playlistSongs,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.MusicNote
    }
}
