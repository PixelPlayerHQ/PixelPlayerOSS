@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.jellyfin.auth.JellyfinLoginActivity
import com.lostf1sh.pixelplayeross.presentation.navidrome.auth.NavidromeLoginActivity
import com.lostf1sh.pixelplayeross.presentation.viewmodel.AccountsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ExternalAccountUiModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ExternalServiceAccount
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun AccountsScreen(
    onBackClick: () -> Unit,
    onOpenNavidromeDashboard: () -> Unit = {},
    onOpenJellyfinDashboard: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
            1f - (
                (topBarHeight.value - minTopBarHeightPx) /
                    (maxTopBarHeightPx - minTopBarHeightPx)
                ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (
                        lazyListState.firstVisibleItemIndex > 0 ||
                            lazyListState.firstVisibleItemScrollOffset > 0
                        )
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(modifier = Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AccountsHeroSection(
                    connectedCount = uiState.connectedAccounts.size,
                    disconnectedCount = uiState.disconnectedServices.size
                )
            }

            if (uiState.connectedAccounts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.presentation_batch_b_accounts_linked_services),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(
                    items = uiState.connectedAccounts,
                    key = { it.service.name }
                ) { account ->
                    ConnectedAccountCard(
                        account = account,
                        onManage = {
                            openService(
                                context = context,
                                service = account.service,
                                onOpenNavidromeDashboard = onOpenNavidromeDashboard,
                                onOpenJellyfinDashboard = onOpenJellyfinDashboard,
                                preferDashboard = true
                            )
                        },
                        onLogout = { viewModel.logout(account.service) },
                        painter = if (account.service == ExternalServiceAccount.JELLYFIN) {
                            painterResource(R.drawable.ic_jellyfin)
                        } else if (account.service == ExternalServiceAccount.NAVIDROME) {
                            painterResource(R.drawable.ic_navidrome_md3)
                        } else null
                    )
                }
            } else {
                item {
                    EmptyAccountsCard(
                        disconnectedServices = uiState.disconnectedServices,
                        onConnect = { service ->
                            openService(
                                context = context,
                                service = service,
                                onOpenNavidromeDashboard = onOpenNavidromeDashboard,
                                onOpenJellyfinDashboard = onOpenJellyfinDashboard,
                                preferDashboard = false
                            )
                        }
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_accounts_row_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }
}

@Composable
private fun AccountsHeroSection(
    connectedCount: Int,
    disconnectedCount: Int
) {
    val connectedHeroTitle = stringResource(R.string.presentation_batch_b_accounts_connected_hero_title)
    val connectedHeroBody = stringResource(R.string.presentation_batch_b_accounts_connected_hero_body)
    val statActive = stringResource(R.string.presentation_batch_b_accounts_stat_active)
    val statAvailable = stringResource(R.string.presentation_batch_b_accounts_stat_available)
    val sectionShape = AbsoluteSmoothCornerShape(30.dp, 60)
    Card(
        shape = sectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = connectedHeroTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = connectedHeroBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatTile(
                    title = statActive,
                    value = connectedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeroStatTile(
                    title = statAvailable,
                    value = (connectedCount + disconnectedCount).toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(18.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConnectedAccountCard(
    account: ExternalAccountUiModel,
    onManage: () -> Unit,
    onLogout: () -> Unit,
    painter: androidx.compose.ui.graphics.painter.Painter? = null
) {
    val statusConnected = stringResource(R.string.presentation_batch_b_accounts_status_connected)
    val openService = stringResource(R.string.presentation_batch_b_accounts_open_service)
    val loggingOut = stringResource(R.string.presentation_batch_b_accounts_logging_out)
    val logOut = stringResource(R.string.cd_logout)
    val palette = servicePalette(account.service)
    val cardShape = AbsoluteSmoothCornerShape(28.dp, 60)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    if (account.service == ExternalServiceAccount.NAVIDROME) {
        ServiceIcon(
            service = account.service,
            tint = palette.iconTint,
            modifier = Modifier
                .width(48.dp)
                .height(40.dp)
        )
    } else {
        Surface(
            shape = AbsoluteSmoothCornerShape(16.dp, 60),
            color = palette.iconContainer
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = palette.iconTint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            } else {
                ServiceIcon(
                    service = account.service,
                    tint = palette.iconTint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
    }

    Spacer(Modifier.size(12.dp))

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = account.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = account.accountLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Surface(
        shape = AbsoluteSmoothCornerShape(12.dp, 60),
        color = palette.statusContainer
    ) {
        Text(
            text = statusConnected,
            style = MaterialTheme.typography.labelMedium,
            color = palette.statusTint,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

            Surface(
                shape = AbsoluteSmoothCornerShape(14.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = palette.iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = account.syncedContentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))

            FilledTonalButton(
                onClick = onManage,
                enabled = !account.isLoggingOut,
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.primaryActionContainer,
                    contentColor = palette.primaryActionTint,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = openService,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onLogout,
                enabled = !account.isLoggingOut,
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                border = BorderStroke(1.dp, palette.primaryActionTint.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (account.isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (account.isLoggingOut) loggingOut else logOut,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyAccountsCard(
    disconnectedServices: List<ExternalServiceAccount>,
    onConnect: (ExternalServiceAccount) -> Unit
) {
    val noLinkedTitle = stringResource(R.string.presentation_batch_b_accounts_no_linked_title)
    val noLinkedBody = stringResource(R.string.presentation_batch_b_accounts_no_linked_body)
    val connectTemplate = stringResource(R.string.presentation_batch_b_accounts_connect_service)
    Card(
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = noLinkedTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = noLinkedBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            disconnectedServices.forEach { service ->
                val painter = when (service) {
                    ExternalServiceAccount.JELLYFIN -> painterResource(R.drawable.ic_jellyfin)
                    ExternalServiceAccount.NAVIDROME -> painterResource(R.drawable.ic_navidrome_md3)
                }
                FilledTonalButton(
                    onClick = { onConnect(service) },
                    enabled = true,
                    shape = AbsoluteSmoothCornerShape(18.dp, 60),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = connectTemplate.format(serviceDisplayName(service))
                    )
                }
            }
        }
    }
}

private data class ServicePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val statusContainer: Color,
    val statusTint: Color,
    val primaryActionContainer: Color,
    val primaryActionTint: Color
)

@Composable
private fun servicePalette(service: ExternalServiceAccount): ServicePalette {
    return when (service) {
        ExternalServiceAccount.NAVIDROME -> ServicePalette(
            iconContainer = Color.White,
            iconTint = Color.Unspecified,
            statusContainer = Color(0xFFE1F5FE),
            statusTint = Color(0xFF0277BD),
            primaryActionContainer = Color(0xFFE3F2FD),
            primaryActionTint = Color(0xFF1565C0)
        )
        ExternalServiceAccount.JELLYFIN -> ServicePalette(
            iconContainer = Color(0xFF00A4DC),
            iconTint = Color.White,
            statusContainer = Color(0xFFE1F5FE),
            statusTint = Color(0xFF0277BD),
            primaryActionContainer = Color(0xFFE3F2FD),
            primaryActionTint = Color(0xFF1565C0)
        )
    }
}

private fun accountIcon(service: ExternalServiceAccount): ImageVector {
    return when (service) {
        ExternalServiceAccount.NAVIDROME -> Icons.Rounded.CloudQueue
        ExternalServiceAccount.JELLYFIN -> Icons.Rounded.CloudQueue
    }
}

@Composable
private fun ServiceIcon(service: ExternalServiceAccount, tint: Color, modifier: Modifier = Modifier) {
    if (service == ExternalServiceAccount.NAVIDROME) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            // Subsonic icon (Bottom) - No outer container
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_subsonic),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(32.dp)
            )
            
            // Navidrome icon (Top) - Closer horizontal offset, no outer container
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_navidrome),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 16.dp) // Closer overlap offset (was 24dp)
            )
        }
    } else if (service == ExternalServiceAccount.JELLYFIN) {
        Icon(
            painter = painterResource(R.drawable.ic_jellyfin),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = accountIcon(service),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
private fun serviceDisplayName(service: ExternalServiceAccount): String {
    return when (service) {
        ExternalServiceAccount.NAVIDROME -> stringResource(R.string.cd_subsonic_logo)
        ExternalServiceAccount.JELLYFIN -> stringResource(R.string.auth_jellyfin_title)
    }
}

private fun openService(
    context: Context,
    service: ExternalServiceAccount,
    onOpenNavidromeDashboard: () -> Unit,
    onOpenJellyfinDashboard: () -> Unit,
    preferDashboard: Boolean
) {
    when (service) {
        ExternalServiceAccount.NAVIDROME -> {
            if (preferDashboard) {
                onOpenNavidromeDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, NavidromeLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.JELLYFIN -> {
            if (preferDashboard) {
                onOpenJellyfinDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, JellyfinLoginActivity::class.java)
                )
            }
        }
    }
}

private fun safeStartActivity(
    context: Context,
    intent: Intent
) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.accounts_unable_open_screen), Toast.LENGTH_SHORT).show()
        }
}
