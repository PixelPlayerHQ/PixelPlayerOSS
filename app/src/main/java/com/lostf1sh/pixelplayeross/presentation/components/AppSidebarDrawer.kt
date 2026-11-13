package com.lostf1sh.pixelplayeross.presentation.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.preferences.SidebarShortcutKeys
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** How far the sheet slides toward its screen edge at full predictive-back progress. */
private val PredictiveBackMaxOffset = 16.dp

sealed class DrawerDestination(val route: String) {
    object Home : DrawerDestination("home")
    object Stats : DrawerDestination("stats")
    object Equalizer : DrawerDestination("equalizer")
    object Bookmarks : DrawerDestination("audio_bookmarks")
    object Settings : DrawerDestination("settings")
}

@Composable
fun AppSidebarDrawer(
    drawerState: DrawerState,
    selectedRoute: String,
    enabledShortcuts: Set<String>,
    onShortcutsChanged: (Set<String>) -> Unit,
    onDestinationSelected: (DrawerDestination) -> Unit,
    content: @Composable () -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    // The sheet sits on the right edge in RTL, so it has to slide right to close there.
    val closingDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only while open, so the scrim and a swipe can close it. Edge-swipe opening stays
        // off because it would fight the system back gesture; the Home menu button opens it.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            val drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val scale = 1f - (predictiveBackProgress * 0.08f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (closingDirection < 0f) 0f else 1f,
                            pivotFractionY = 0.5f
                        )
                        translationX =
                            closingDirection * predictiveBackProgress * PredictiveBackMaxOffset.toPx()
                        alpha = 1f - (predictiveBackProgress * 0.15f)
                    }
                    .drawBehind {
                        val bleedPx = 60.dp.toPx()
                        if (closingDirection < 0f) {
                            drawRect(
                                color = drawerContainerColor,
                                topLeft = Offset(-bleedPx, 0f),
                                size = Size(bleedPx, size.height)
                            )
                        } else {
                            drawRect(
                                color = drawerContainerColor,
                                topLeft = Offset(size.width, 0f),
                                size = Size(bleedPx, size.height)
                            )
                        }
                    },
                drawerContainerColor = drawerContainerColor
            ) {
                DrawerContent(
                    selectedRoute = selectedRoute,
                    enabledShortcuts = enabledShortcuts,
                    onEditShortcutsClick = { showConfigDialog = true },
                    onDestinationSelected = onDestinationSelected
                )
            }
        },
        content = content
    )

    AppSidebarBackHandler(
        drawerState = drawerState,
        onProgressChanged = { predictiveBackProgress = it }
    )

    if (showConfigDialog) {
        ShortcutsConfigDialog(
            enabledShortcuts = enabledShortcuts,
            onDismiss = { showConfigDialog = false },
            onSave = onShortcutsChanged
        )
    }
}

/** Back closes the open drawer instead of leaving the current screen or the app. */
@Composable
private fun AppSidebarBackHandler(
    drawerState: DrawerState,
    onProgressChanged: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }
    val fastSpatialSpec = remember { MotionScheme.expressive().fastSpatialSpec<Float>() }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = drawerState.isOpen) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    onProgressChanged(backEvent.progress)
                }
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = fastSpatialSpec
                    ) { onProgressChanged(value) }
                    drawerState.close()
                    progressAnim.snapTo(0f)
                    onProgressChanged(0f)
                }
            } catch (_: CancellationException) {
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = fastSpatialSpec
                    ) { onProgressChanged(value) }
                }
            }
        }
    } else {
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}

@Composable
private fun drawerItemColors(): NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun DrawerItem(
    icon: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                painter = icon,
                contentDescription = label
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp),
        colors = drawerItemColors(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun shortcutLabel(key: String): String = when (key) {
    SidebarShortcutKeys.HOME -> stringResource(R.string.tab_home)
    SidebarShortcutKeys.STATS -> stringResource(R.string.presentation_batch_g_stats_title)
    SidebarShortcutKeys.EQUALIZER -> stringResource(R.string.settings_category_equalizer_title)
    SidebarShortcutKeys.AUDIO_BOOKMARKS -> stringResource(R.string.audio_bookmarks_title)
    else -> key
}

@Composable
private fun DrawerContent(
    selectedRoute: String,
    enabledShortcuts: Set<String>,
    onEditShortcutsClick: () -> Unit,
    onDestinationSelected: (DrawerDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.presentation_batch_g_app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.presentation_batch_g_app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEditShortcutsClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.sidebar_edit_shortcuts),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (SidebarShortcutKeys.HOME in enabledShortcuts) {
            DrawerItem(
                icon = rememberVectorPainter(Icons.Rounded.Home),
                label = shortcutLabel(SidebarShortcutKeys.HOME),
                selected = selectedRoute == DrawerDestination.Home.route,
                onClick = { onDestinationSelected(DrawerDestination.Home) }
            )
        }

        if (SidebarShortcutKeys.STATS in enabledShortcuts) {
            DrawerItem(
                icon = rememberVectorPainter(Icons.Rounded.BarChart),
                label = shortcutLabel(SidebarShortcutKeys.STATS),
                selected = selectedRoute == DrawerDestination.Stats.route,
                onClick = { onDestinationSelected(DrawerDestination.Stats) }
            )
        }

        if (SidebarShortcutKeys.EQUALIZER in enabledShortcuts) {
            DrawerItem(
                icon = rememberVectorPainter(Icons.Rounded.GraphicEq),
                label = shortcutLabel(SidebarShortcutKeys.EQUALIZER),
                selected = selectedRoute == DrawerDestination.Equalizer.route,
                onClick = { onDestinationSelected(DrawerDestination.Equalizer) }
            )
        }

        if (SidebarShortcutKeys.AUDIO_BOOKMARKS in enabledShortcuts) {
            DrawerItem(
                icon = rememberVectorPainter(Icons.Rounded.Bookmark),
                label = shortcutLabel(SidebarShortcutKeys.AUDIO_BOOKMARKS),
                selected = selectedRoute == DrawerDestination.Bookmarks.route,
                onClick = { onDestinationSelected(DrawerDestination.Bookmarks) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        DrawerItem(
            icon = painterResource(R.drawable.rounded_settings_24),
            label = stringResource(R.string.settings_top_bar_title),
            selected = selectedRoute == DrawerDestination.Settings.route,
            onClick = { onDestinationSelected(DrawerDestination.Settings) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ShortcutsConfigDialog(
    enabledShortcuts: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var tempShortcuts by remember { mutableStateOf(enabledShortcuts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sidebar_shortcuts_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarShortcutKeys.ALL.forEach { key ->
                    val checked = key in tempShortcuts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                tempShortcuts = if (checked) tempShortcuts - key else tempShortcuts + key
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                tempShortcuts = if (isChecked) tempShortcuts + key else tempShortcuts - key
                            }
                        )
                        Text(
                            text = shortcutLabel(key),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(tempShortcuts)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
