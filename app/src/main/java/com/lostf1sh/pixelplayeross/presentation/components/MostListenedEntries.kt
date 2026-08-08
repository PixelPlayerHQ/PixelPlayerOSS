package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.SongEngagementEntity
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.MostListenedType
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ThemeStateHolder
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme

/** One row of the Most Listened screen, already collapsed to the selected grouping. */
data class MostListenedEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val playCount: Int,
    val representativeSong: Song,
    val destinationId: Long,
)

/**
 * Collapses per-song play counts into ranked entries for [type].
 *
 * Album and artist entries keep the most-played track as their representative so tapping through
 * lands on real playable content, and their artwork/subtitle come from that track.
 */
fun buildMostListenedEntries(
    songs: List<Song>,
    engagements: List<SongEngagementEntity>,
    type: MostListenedType,
    artists: List<Artist> = emptyList(),
): List<MostListenedEntry> {
    val counts = engagements.associate { it.songId to it.playCount.coerceAtLeast(0) }
    val artistImages = artists.associate { it.id to it.effectiveImageUrl }
    val playedSongs = songs.filter { (counts[it.id] ?: 0) > 0 }
    return when (type) {
        MostListenedType.SONGS -> playedSongs
            .sortedWith(compareByDescending<Song> { counts[it.id] ?: 0 }.thenBy { it.title })
            .map { song ->
                MostListenedEntry(
                    key = "song:${song.id}",
                    title = song.title,
                    subtitle = song.displayArtist,
                    artwork = song.albumArtUriString,
                    playCount = counts[song.id] ?: 0,
                    representativeSong = song,
                    destinationId = song.id.toLongOrNull() ?: -1L,
                )
            }

        MostListenedType.ALBUMS -> playedSongs
            .groupBy { it.albumId }
            .mapNotNull { (albumId, albumSongs) ->
                val representative = albumSongs.maxByOrNull { counts[it.id] ?: 0 } ?: return@mapNotNull null
                MostListenedEntry(
                    key = "album:$albumId",
                    title = representative.album,
                    subtitle = representative.albumArtist?.takeIf(String::isNotBlank) ?: representative.displayArtist,
                    artwork = representative.albumArtUriString,
                    playCount = albumSongs.sumOf { counts[it.id] ?: 0 },
                    representativeSong = representative,
                    destinationId = albumId,
                )
            }
            .sortedWith(compareByDescending<MostListenedEntry> { it.playCount }.thenBy { it.title })

        MostListenedType.ARTISTS -> playedSongs
            .groupBy { it.primaryArtist.id }
            .mapNotNull { (artistId, artistSongs) ->
                val representative = artistSongs.maxByOrNull { counts[it.id] ?: 0 } ?: return@mapNotNull null
                MostListenedEntry(
                    key = "artist:$artistId",
                    title = representative.primaryArtist.name,
                    subtitle = representative.album,
                    artwork = artistImages[artistId]?.takeIf(String::isNotBlank) ?: representative.albumArtUriString,
                    playCount = artistSongs.sumOf { counts[it.id] ?: 0 },
                    representativeSong = representative,
                    destinationId = artistId,
                )
            }
            .sortedWith(compareByDescending<MostListenedEntry> { it.playCount }.thenBy { it.title })
    }
}

@Composable
fun rememberArtworkColorScheme(artwork: String?, themeStateHolder: ThemeStateHolder): ColorScheme {
    val pair by remember(artwork, themeStateHolder) {
        themeStateHolder.getAlbumColorSchemeFlow(artwork.orEmpty())
    }.collectAsStateWithLifecycle()
    val dark = LocalPixelPlayerDarkTheme.current
    return pair?.let { if (dark) it.dark else it.light } ?: MaterialTheme.colorScheme
}

/**
 * Artwork that fades into [blendColor] at its trailing edge, so a list row can carry a full-bleed
 * cover without the text on top of it losing contrast.
 */
@Composable
fun ProgressiveArtworkBlend(
    model: String?,
    blendColor: Color,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val density = LocalDensity.current
    val blurCache = remember { BlurEffectCache() }
    val blurRadiusPx = with(density) { 18.dp.toPx() }
    val blurEffect = remember(blurRadiusPx) { blurCache.get(blurRadiusPx) }
    val maskBrush = remember(horizontal) {
        if (horizontal) {
            Brush.horizontalGradient(0.28f to Color.Transparent, 1f to Color.Black)
        } else {
            Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color.Black)
        }
    }
    val blendBrush = remember(blendColor, horizontal) {
        if (horizontal) {
            Brush.horizontalGradient(
                0f to Color.Transparent,
                .52f to Color.Transparent,
                .82f to blendColor.copy(alpha = .92f),
                1f to blendColor,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Transparent,
                .48f to Color.Transparent,
                .78f to blendColor.copy(alpha = .92f),
                1f to blendColor,
            )
        }
    }
    Box(modifier) {
        SmartImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (blurEnabled) {
            SmartImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = blurEffect
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(maskBrush, blendMode = BlendMode.DstIn)
                    },
            )
        }
        Box(Modifier.fillMaxSize().background(blendBrush))
    }
}

fun MostListenedType.labelRes(): Int = when (this) {
    MostListenedType.SONGS -> R.string.most_listened_songs
    MostListenedType.ALBUMS -> R.string.most_listened_albums
    MostListenedType.ARTISTS -> R.string.most_listened_artists
}
