package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.SortDirection
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Sort sheet for a library list.
 *
 * [secondaryOptions] adds a second set of options for other items on the same screen, such as the
 * songs inside a folder next to its subfolders. A switcher under the title then picks which set
 * the sheet shows, labelled [primaryTargetLabel] and [secondaryTargetLabel]; it opens on the
 * secondary set when [initiallyShowSecondary] is true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortBottomSheet(
    title: String,
    options: ImmutableList<SortOption>,
    selectedOption: SortOption?,
    onDismiss: () -> Unit,
    onOptionSelected: (SortOption) -> Unit,
    onDirectionToggle: (SortOption) -> Unit = onOptionSelected,
    secondaryOptions: ImmutableList<SortOption> = persistentListOf(),
    secondarySelectedOption: SortOption? = null,
    onSecondaryOptionSelected: (SortOption) -> Unit = {},
    onSecondaryDirectionToggle: (SortOption) -> Unit = onSecondaryOptionSelected,
    primaryTargetLabel: String = "",
    secondaryTargetLabel: String = "",
    initiallyShowSecondary: Boolean = false,
    showViewToggle: Boolean = false,
    viewSectionTitle: String = "View",
    viewToggleLabel: String = "Playlist View",
    viewToggleChecked: Boolean = false,
    onViewToggleChange: (Boolean) -> Unit = {},
    viewToggleContent: (@Composable () -> Unit)? = null,
    sourceToggleContent: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.surfaceContainerLow

    val boxBackgroundColor by animateColorAsState(
        targetValue = if (viewToggleChecked) MaterialTheme.colorScheme.tertiary else unselectedColor,
        label = "boxBackgroundColorAnimation"
    )

    val boxCornerRadius by animateDpAsState(
        targetValue = if (viewToggleChecked) 18.dp else 50.dp,
        label = "boxCornerRadiusAnimation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        // Scrolls when both sort targets plus the view and source sections outgrow short screens.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 0.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 0.dp, bottom = 16.dp)
            )

            val hasSecondaryTarget = secondaryOptions.isNotEmpty()
            var secondaryTargetChosen by rememberSaveable { mutableStateOf(initiallyShowSecondary) }
            val showSecondaryTarget = hasSecondaryTarget && secondaryTargetChosen

            if (hasSecondaryTarget) {
                LibrarySheetSortTargetSwitcher(
                    modifier = Modifier.padding(bottom = 12.dp),
                    primaryLabel = primaryTargetLabel,
                    secondaryLabel = secondaryTargetLabel,
                    secondarySelected = showSecondaryTarget,
                    onSecondarySelectedChange = { secondaryTargetChosen = it }
                )
            }

            AnimatedContent(
                targetState = showSecondaryTarget,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 90)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 90))
                },
                label = "sortTargetSwitch"
            ) { secondary ->
                if (secondary) {
                    LibrarySheetSortSection(
                        options = secondaryOptions,
                        selectedOption = secondarySelectedOption,
                        onOptionSelected = onSecondaryOptionSelected,
                        onDirectionToggle = onSecondaryDirectionToggle,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    )
                } else {
                    LibrarySheetSortSection(
                        options = options,
                        selectedOption = selectedOption,
                        onOptionSelected = onOptionSelected,
                        onDirectionToggle = onDirectionToggle,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    )
                }
            }

            if (showViewToggle || viewToggleContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewSectionTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                )

                if (viewToggleContent != null) {
                    viewToggleContent()
                } else {
                    LibrarySheetToggleCard(
                        label = viewToggleLabel,
                        checked = viewToggleChecked,
                        boxBackgroundColor = boxBackgroundColor,
                        boxCornerRadius = boxCornerRadius,
                        onCheckedChange = onViewToggleChange
                    )
                }
            }

            if (sourceToggleContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.presentation_batch_g_sort_source),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                )
                sourceToggleContent()
            }

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                extraContent()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LibrarySheetSortSection(
    options: ImmutableList<SortOption>,
    selectedOption: SortOption?,
    onOptionSelected: (SortOption) -> Unit,
    onDirectionToggle: (SortOption) -> Unit,
    selectedColor: Color,
    unselectedColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        @Suppress("UNCHECKED_CAST")
        val safeOptions = remember(options) {
            (options as List<SortOption?>).filterNotNull()
        }
        val resolvedSelectedOption = remember(safeOptions, selectedOption) {
            safeOptions.firstOrNull { option ->
                option.storageKey == selectedOption?.storageKey
            } ?: selectedOption ?: safeOptions.firstOrNull()
        }
        val methodOptions = remember(safeOptions) {
            safeOptions
                .map { it.methodOption() }
                .distinctBy { it.methodKey }
        }
        val selectedDirection = resolvedSelectedOption?.direction
        val shouldShowDirectionToggle = remember(methodOptions) {
            methodOptions.any { it.canFlipDirection }
        }

        if (shouldShowDirectionToggle && resolvedSelectedOption != null) {
            val directionLabel = when (selectedDirection) {
                SortDirection.Descending -> stringResource(R.string.presentation_batch_g_sort_descending)
                SortDirection.Ascending -> stringResource(R.string.presentation_batch_g_sort_ascending)
                null -> stringResource(R.string.presentation_batch_g_sort_original_order)
            }
            val isDescending = selectedDirection == SortDirection.Descending

            LibrarySheetSortDirectionCard(
                modifier = Modifier.padding(bottom = 12.dp),
                label = directionLabel,
                isDescending = isDescending,
                enabled = resolvedSelectedOption.canFlipDirection,
                onClick = {
                    onDirectionToggle(resolvedSelectedOption.flipDirection())
                }
            )
        }

        Column(
            modifier = Modifier
                .clip(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            methodOptions.forEach { option ->
                val isSelected = resolvedSelectedOption?.methodKey == option.methodKey
                val containerColor = remember(isSelected) {
                    if (isSelected) selectedColor else unselectedColor
                }

                Surface(
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                        .clip(
                            shape = RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 8.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 8.dp
                            )
                        )
                        .selectable(
                            selected = isSelected,
                            onClick = {
                                onOptionSelected(option.resolveForDirection(selectedDirection))
                            },
                            role = Role.RadioButton
                        )
                        .semantics { this.selected = isSelected }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(option.methodLabelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySheetSortTargetSwitcher(
    primaryLabel: String,
    secondaryLabel: String,
    secondarySelected: Boolean,
    onSecondarySelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToggleSegmentButton(
            modifier = Modifier.weight(1f),
            active = !secondarySelected,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            activeCornerRadius = 32.dp,
            onClick = { onSecondarySelectedChange(false) },
            text = primaryLabel
        )
        ToggleSegmentButton(
            modifier = Modifier.weight(1f),
            active = secondarySelected,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            activeCornerRadius = 32.dp,
            onClick = { onSecondarySelectedChange(true) },
            text = secondaryLabel
        )
    }
}

@Composable
private fun LibrarySheetSortDirectionCard(
    modifier: Modifier = Modifier,
    label: String,
    isDescending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val transition = updateTransition(
        targetState = isDescending,
        label = "sortDirectionTransition"
    )
    val containerColor by transition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionContainerColor"
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
            descending -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    }
    val contentColor by transition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionContentColor"
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            descending -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    val iconContainerColor by animateColorAsState(
        targetValue = contentColor.copy(alpha = if (enabled) 0.16f else 0.1f),
        label = "sortDirectionIconContainerColor"
    )
    val iconRotation by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "sortDirectionIconRotation"
    ) { descending ->
        if (descending) 0f else 180f
    }
    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionIconScale"
    ) { descending ->
        if (descending) 1f else 1.08f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.72f)
            .clip(AbsoluteSmoothCornerShape(18.dp, 60))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(iconRotation)
                        .scale(iconScale)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.presentation_batch_g_sort_order),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.82f)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
internal fun LibrarySheetToggleCard(
    label: String,
    checked: Boolean,
    boxBackgroundColor: Color,
    boxCornerRadius: Dp,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .clip(
                AbsoluteSmoothCornerShape(
                    cornerRadiusBL = boxCornerRadius,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = boxCornerRadius,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusTL = boxCornerRadius,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBR = boxCornerRadius,
                    smoothnessAsPercentTR = 60
                )
            )
            .background(color = boxBackgroundColor)
            .clickable(onClick = { onCheckedChange(!checked) })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                // Checked, the card itself is `tertiary`, so the switch inverts the usual
                // roles: an onTertiary track with a tertiary thumb. Both pairs are contrast-
                // guaranteed in every palette, unlike onTertiaryContainer on tertiary.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                    checkedTrackColor = MaterialTheme.colorScheme.onTertiary,
                    checkedBorderColor = MaterialTheme.colorScheme.onTertiary,
                    checkedIconColor = MaterialTheme.colorScheme.onTertiary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.presentation_batch_g_cd_switch_on),
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
