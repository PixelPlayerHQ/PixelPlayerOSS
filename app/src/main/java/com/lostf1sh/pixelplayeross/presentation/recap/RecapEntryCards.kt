@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.recap

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.ShapeCache
import kotlinx.collections.immutable.ImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@StringRes
private fun RecapEntry.titleRes(): Int =
    if (isSoFar) R.string.recap_card_title_so_far else R.string.recap_card_title

/**
 * Stats screen entry to the recaps in [entries] (most relevant first): the first one as the
 * card itself, a second one as a smaller link underneath.
 */
@Composable
fun RecapStatsCard(
    entries: ImmutableList<RecapEntry>,
    onOpen: (year: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = entries.firstOrNull() ?: return
    val secondary = entries.getOrNull(1)
    val container = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(28.dp, 60))
            .background(container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(primary.year) }
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (secondary == null) 16.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.expressiveHero)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(primary.titleRes(), primary.year),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = rememberRecapFlexFont(weight = 760, width = 114f),
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (primary.isSoFar) R.string.recap_card_subtitle_so_far else R.string.recap_card_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        if (secondary != null) {
            TextButton(
                onClick = { onOpen(secondary.year) },
                colors = ButtonDefaults.textButtonColors(contentColor = content),
                // Lines the label up with the title above (icon 56dp + gaps, minus button padding).
                modifier = Modifier.padding(start = 74.dp, bottom = 8.dp)
            ) {
                Text(text = stringResource(secondary.titleRes(), secondary.year))
                Spacer(Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Seasonal Home card announcing the recap of [entry]'s year, until it is dismissed. */
@Composable
fun RecapHomeCard(
    entry: RecapEntry,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    val motionEnabled = rememberRecapMotionEnabled()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AbsoluteSmoothCornerShape(28.dp, 60))
            .background(container)
            .clickable(onClick = onOpen)
    ) {
        RecapBackdropShape(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = content.copy(alpha = 0.12f),
            active = motionEnabled,
            size = 220.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 70.dp)
        )
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.recap_intro_kicker),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = rememberRecapFlexFont(weight = 660, width = 110f),
                color = content.copy(alpha = 0.82f),
                modifier = Modifier.padding(end = 40.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.recap_home_card_title, entry.year),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = rememberRecapFlexFont(weight = 840, width = 116f),
                color = content,
                modifier = Modifier.padding(end = 40.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.recap_home_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.recap_home_card_open))
            }
        }
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(contentColor = content),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.recap_home_card_dismiss))
        }
    }
}
