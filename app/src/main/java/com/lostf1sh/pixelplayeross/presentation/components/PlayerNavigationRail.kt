package com.lostf1sh.pixelplayeross.presentation.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.BottomNavItem
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 navigation rail used instead of the bottom bar on medium/expanded
 * window widths (tablets, unfolded foldables).
 *
 * Features:
 * - Surface container with subtle elevation and rounded outer corners.
 * - Vertically centered navigation items with clean M3 pill indicators.
 * - Seamless search double-tap support matching [PlayerInternalNavigationBar].
 */
@Composable
fun PlayerNavigationRail(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)
    val scope = rememberCoroutineScope()
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp),
        shape = RectangleShape,
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = topInset, bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute != null && currentRoute == item.screen.route
                val label = stringResource(item.labelResId)
                val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                    item.selectedIconResId
                } else {
                    item.iconResId
                }

                val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                    click@{
                        if (!latestNavigationEnabled) {
                            lastSearchTapTimestamp = 0L
                            return@click
                        }

                        val itemRoute = item.screen.route
                        val isSearchTab = itemRoute == Screen.Search.route
                        val isAlreadySelected = latestCurrentRoute == itemRoute

                        if (isSearchTab) {
                            val now = SystemClock.elapsedRealtime()
                            val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                            lastSearchTapTimestamp = now

                            if (!isAlreadySelected) {
                                if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                    lastSearchTapTimestamp = 0L
                                    return@click
                                }
                            }

                            if (isDoubleTap) {
                                lastSearchTapTimestamp = 0L
                                if (isAlreadySelected) {
                                    latestOnSearchIconDoubleTap()
                                } else {
                                    scope.launch {
                                        delay(160L)
                                        latestOnSearchIconDoubleTap()
                                    }
                                }
                            }
                        } else if (!isAlreadySelected) {
                            lastSearchTapTimestamp = 0L
                            navController.navigateToTopLevelSafely(itemRoute)
                        } else {
                            lastSearchTapTimestamp = 0L
                        }
                    }
                }

                NavigationRailItem(
                    selected = isSelected,
                    onClick = onClickLambda,
                    enabled = currentRoute != null,
                    icon = {
                        Icon(
                            painter = painterResource(id = iconPainterResId),
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    }
}
