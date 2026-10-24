@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.recap

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.R
import java.text.NumberFormat

/** Closing slide: a shareable card of the year's highlights, plus what to do next. */
@Composable
internal fun RecapSummarySlide(
    slide: RecapSlide.Summary,
    palette: RecapPalette,
    reveal: () -> Float,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    actions: RecapSummaryActions
) {
    val cardLayer = rememberGraphicsLayer()
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState, horizontalAlignment = Alignment.CenterHorizontally) {
        RecapKicker(
            text = stringResource(R.string.recap_summary_kicker),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(4.dp))
        // The captured area includes a margin of page colour, so the shared image has no
        // transparent corners for messengers to turn black.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .recapReveal(reveal, order = 1)
                .drawWithContent {
                    cardLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(cardLayer)
                }
                .background(palette.background)
                .padding(12.dp)
        ) {
            RecapSummaryCard(slide = slide, palette = palette)
        }
        Spacer(Modifier.height(12.dp))
        // Stacked rather than side by side: translated labels do not fit half a phone's width.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .recapReveal(reveal, order = 3),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { actions.onShare(cardLayer) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.content,
                    contentColor = palette.background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.recap_share),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (slide.canPlayTopSongs) {
                FilledTonalButton(
                    onClick = actions.onPlayTopSongs,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = palette.content.copy(alpha = 0.14f),
                        contentColor = palette.content
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.recap_play_top_songs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = actions.onReplay,
            colors = ButtonDefaults.textButtonColors(contentColor = palette.content),
            modifier = Modifier.recapReveal(reveal, order = 4)
        ) {
            Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.recap_replay))
        }
    }
}

@Composable
private fun RecapSummaryCard(slide: RecapSlide.Summary, palette: RecapPalette) {
    val locale = LocalConfiguration.current.locales[0]
    val minutes = remember(slide.totalMs, locale) {
        NumberFormat.getIntegerInstance(locale).format(recapListenedMinutes(slide.totalMs))
    }
    val ink = palette.onAccent
    val cells = buildList {
        slide.topArtist?.let { artist ->
            add(
                SummaryCellData(
                    label = stringResource(R.string.recap_summary_top_artist),
                    value = artist.name,
                    artUri = artist.imageUri,
                    monogramSource = artist.name,
                    circularArt = true
                )
            )
        }
        slide.topSong?.let { song ->
            add(SummaryCellData(label = stringResource(R.string.recap_summary_top_song), value = song.title, artUri = song.artUri))
        }
        slide.topGenre?.let { genre ->
            add(SummaryCellData(label = stringResource(R.string.recap_summary_top_genre), value = genre))
        }
        slide.topAlbum?.let { album ->
            add(SummaryCellData(label = stringResource(R.string.recap_summary_top_album), value = album.title, artUri = album.artUri))
        }
        add(
            SummaryCellData(
                label = stringResource(R.string.recap_summary_listening_style),
                value = stringResource(slide.personality.titleRes)
            )
        )
    }.take(4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(palette.accent)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(ink),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.recap_intro_kicker),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = rememberRecapFlexFont(weight = 680, width = 110f),
                color = ink.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RecapHeadline(
            text = stringResource(
                if (slide.isSoFar) R.string.recap_summary_title_so_far else R.string.recap_summary_title,
                slide.year
            ),
            color = ink,
            maxFontSize = 30.sp,
            minFontSize = 20.sp,
            weight = 880
        )
        Column {
            Text(
                text = stringResource(R.string.recap_summary_minutes),
                style = MaterialTheme.typography.labelLarge,
                color = ink.copy(alpha = 0.78f)
            )
            RecapHeadline(
                text = minutes,
                color = ink,
                maxFontSize = 52.sp,
                minFontSize = 28.sp,
                maxLines = 1,
                weight = 950
            )
        }
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { cell ->
                    SummaryCell(cell = cell, palette = palette, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Immutable
private data class SummaryCellData(
    val label: String,
    val value: String,
    val artUri: String? = null,
    val monogramSource: String? = null,
    val circularArt: Boolean = false
)

@Composable
private fun SummaryCell(cell: SummaryCellData, palette: RecapPalette, modifier: Modifier = Modifier) {
    val ink = palette.onAccent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ink.copy(alpha = 0.1f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (cell.artUri != null || cell.monogramSource != null) {
            RecapArtwork(
                model = cell.artUri,
                shape = if (cell.circularArt) CircleShape else RoundedCornerShape(10.dp),
                // Swapped so a monogram stands out on the accent-coloured card.
                palette = palette.copy(accent = ink, onAccent = palette.accent),
                monogram = cell.monogramSource?.let { rememberMonogram(it) },
                targetSizePx = 160,
                modifier = Modifier.size(40.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cell.label,
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = cell.value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = rememberRecapFlexFont(weight = 740, width = 104f),
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Whether a graphics layer has anything to capture; a never-drawn layer has no size. */
internal val GraphicsLayer.hasContent: Boolean
    get() = size.width > 0 && size.height > 0
