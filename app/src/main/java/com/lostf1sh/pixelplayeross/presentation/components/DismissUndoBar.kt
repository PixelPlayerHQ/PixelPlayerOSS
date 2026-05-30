package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun DismissUndoBar(
    modifier: Modifier = Modifier,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    durationMillis: Long
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(key1 = onUndo) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = durationMillis.toInt(),
                easing = LinearEasing
            )
        )
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = stringResource(R.string.playlist_dismissed_message),
                    style = MaterialTheme.typography.titleSmall
                        .copy(
                            fontFamily = RoundedSans
                        ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onUndo
                    ) {
                        Text(stringResource(R.string.action_undo), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onClose
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction = progress.value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTR = 12.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = 12.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = 12.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBL = 12.dp,
                            smoothnessAsPercentBR = 60
                        )
                    )
            )
        }
    }
}
