package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.screens.ExpressiveSettingsGroup
import com.lostf1sh.pixelplayeross.presentation.screens.getCategoryColors
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel

/**
 * Renders scored search results grouped by the category each setting lives in.
 *
 * The engine returns results split into top/related sections, but showing both splits side by
 * side reads as duplication when a setting scores near the boundary. Instead the sections are
 * flattened in score order and regrouped by category, keeping the first (highest-scoring)
 * occurrence of each setting.
 */
@Composable
fun SettingsSearchResultsContent(
    query: String,
    results: List<SearchResultSection>,
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_search_no_results, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val groupedByCategory = LinkedHashMap<SettingsCategory, MutableList<SearchResultItem>>()
    val seenKeys = mutableSetOf<String>()
    for (section in results) {
        for (resultItem in section.items) {
            if (seenKeys.add(resultItem.spec.itemKey)) {
                groupedByCategory.getOrPut(resultItem.spec.category) { mutableListOf() }
                    .add(resultItem)
            }
        }
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        groupedByCategory.forEach { (category, items) ->
            item(key = "header_${category.id}") {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            item(key = "group_${category.id}") {
                ExpressiveSettingsGroup {
                    val total = items.size
                    items.forEachIndexed { index, resultItem ->
                        val spec = resultItem.spec
                        val accentColor = getCategoryColors(spec.category, isDark).first

                        val shape = when {
                            total == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(
                                topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp
                            )
                            index == total - 1 -> RoundedCornerShape(
                                topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp
                            )
                            else -> RoundedCornerShape(4.dp)
                        }

                        val navigate: () -> Unit = { navController.navigateSafely(spec.createNavigationRoute()) }

                        when (spec.type) {
                            SettingType.SWITCH -> SearchResultSwitchItem(
                                title = resultItem.matchedTitle,
                                subtitle = resultItem.matchedSubtitle,
                                accentColor = accentColor,
                                checked = spec.getValue?.invoke(uiState) == true,
                                onToggle = { checked -> spec.onToggle?.invoke(settingsViewModel, checked) },
                                onCardClick = navigate,
                                shape = shape
                            )
                            SettingType.NAVIGABLE_CARD -> SearchResultNavigableItem(
                                title = resultItem.matchedTitle,
                                subtitle = resultItem.matchedSubtitle,
                                accentColor = accentColor,
                                onClick = navigate,
                                shape = shape
                            )
                        }

                        if (index < total - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultSwitchItem(
    title: String,
    subtitle: String?,
    accentColor: Color,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    shape: Shape
) {
    SearchResultRow(
        title = title,
        subtitle = subtitle,
        accentColor = accentColor,
        onClick = onCardClick,
        shape = shape
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                    label = "search_switch_thumb_icon"
                ) { isChecked ->
                    Icon(
                        imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SearchResultNavigableItem(
    title: String,
    subtitle: String?,
    accentColor: Color,
    onClick: () -> Unit,
    shape: Shape
) {
    SearchResultRow(
        title = title,
        subtitle = subtitle,
        accentColor = accentColor,
        onClick = onClick,
        shape = shape
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String?,
    accentColor: Color,
    onClick: () -> Unit,
    shape: Shape,
    trailing: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 24.dp, max = 48.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            trailing()
        }
    }
}
