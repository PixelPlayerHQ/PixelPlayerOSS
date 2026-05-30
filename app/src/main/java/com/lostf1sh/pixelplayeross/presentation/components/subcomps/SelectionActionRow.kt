package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val buttonHeight = 44.dp
private val segmentedOuterCornerRadius = 26.dp
private val segmentedInnerCornerRadius = 8.dp
private val segmentGap = 3.dp
private val optionsGap = 4.dp

/**
 * Action row displayed during multi-selection mode.
 * Shows segmented Select All/Deselect buttons and Options button.
 * The selection count is shown as a floating pill separately.
 */
@Composable
fun SelectionActionRow(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leftSegmentShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = segmentedOuterCornerRadius,
        cornerRadiusBL = segmentedOuterCornerRadius,
        cornerRadiusTR = segmentedInnerCornerRadius,
        cornerRadiusBR = segmentedInnerCornerRadius,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBR = 60
    )
    
    val rightSegmentShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = segmentedInnerCornerRadius,
        cornerRadiusBL = segmentedInnerCornerRadius,
        cornerRadiusTR = segmentedOuterCornerRadius,
        cornerRadiusBR = segmentedOuterCornerRadius,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBR = 60
    )
    
    val optionsShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = segmentedOuterCornerRadius,
        cornerRadiusBL = segmentedOuterCornerRadius,
        cornerRadiusTR = segmentedOuterCornerRadius,
        cornerRadiusBR = segmentedOuterCornerRadius,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBR = 60
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Segmented buttons: Select All + Deselect
        Row(
            horizontalArrangement = Arrangement.spacedBy(segmentGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Select All button (left segment)
            FilledTonalButton(
                onClick = onSelectAll,
                shape = leftSegmentShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(buttonHeight)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.presentation_batch_g_selection_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    fontFamily = RoundedSans
                )
            }
            
            // Deselect button (right segment) - using secondary colors
            FilledTonalButton(
                onClick = onDeselect,
                shape = rightSegmentShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(buttonHeight)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Deselect,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.presentation_batch_g_selection_deselect),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    fontFamily = RoundedSans
                )
            }
        }
        
        //Spacer(modifier = Modifier.width(optionsGap))
        
        // Options button
        FilledTonalButton(
            onClick = onOptionsClick,
            shape = optionsShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.height(buttonHeight).width(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.presentation_batch_g_selection_cd_more),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Floating pill that shows the selection count only (no "selected" text).
 */
@Composable
fun SelectionCountPill(
    selectedCount: Int,
    modifier: Modifier = Modifier
) {
    val pillShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBR = 20.dp,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBR = 60
    )
    
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn() + scaleIn(initialScale = 0.8f),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            shape = pillShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RoundedSans,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
