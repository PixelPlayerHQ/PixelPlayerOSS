package com.lostf1sh.pixelplayeross.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

private data class Contributor(
    val id: String,
    val displayName: String,
    val role: String,
    val detail: String? = null,
    val badge: String? = null,
    val avatarUrl: String? = null,
    @DrawableRes val iconRes: Int? = null,
    val githubUrl: String? = null,
    val telegramUrl: String? = null,
    val contributions: Int? = null,
)

private val CoreMaintainer = Contributor(
    id = "lostf1sh",
    displayName = "@lostf1sh",
    role = "FOSS Maintainer",
    detail = "Maintains PixelPlayerOSS. GitHub and Telegram: @lostf1sh.",
    avatarUrl = "https://github.com/lostf1sh.png",
    iconRes = R.drawable.round_developer_board_24,
    githubUrl = "https://github.com/lostf1sh",
    telegramUrl = "https://t.me/lostf1sh",
)

private val NonFossMaintainer = Contributor(
    id = "theovilardo",
    displayName = "@theovilardo",
    role = "Author / Non-FOSS Maintainer",
    detail = "Author and maintainer of the original Google Play / non-FOSS PixelPlayer release.",
    badge = "Original app",
    avatarUrl = "https://github.com/theovilardo.png",
    iconRes = R.drawable.round_developer_board_24,
    githubUrl = "https://github.com/theovilardo",
)

private val AboutMaintainers = listOf(
    CoreMaintainer,
    NonFossMaintainer,
)

private const val SourceRepoUrl = "https://github.com/lostf1sh/PixelPlayerOSS"
private const val FDroidUrl = "https://f-droid.org/packages/com.lostf1sh.pixelplayeross/"
private const val SponsorUrl = "https://github.com/sponsors/lostf1sh"

private data class ProjectLink(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val url: String,
)

// AboutTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
) {
    val context = LocalContext.current
    val versionName: String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "N/A"
    } catch (_: Exception) {
        "N/A"
    }

    val projectLinks = listOf(
        ProjectLink(
            id = "source",
            title = stringResource(R.string.about_link_source_title),
            subtitle = stringResource(R.string.about_link_source_subtitle),
            iconRes = R.drawable.github,
            url = SourceRepoUrl,
        ),
        ProjectLink(
            id = "fdroid",
            title = stringResource(R.string.about_link_fdroid_title),
            subtitle = stringResource(R.string.about_link_fdroid_subtitle),
            iconRes = R.drawable.fdroid,
            url = FDroidUrl,
        ),
    )

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }
    val transition = rememberTransition(transitionState, label = "AboutAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) },
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 170.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - (
            (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
            ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (
                    !isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
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
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            },
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                bottom = MiniPlayerHeight +
                    WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 12.dp,
            ),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero_card") {
                AboutHeroCard(
                    versionName = versionName,
                    onVersionLongPress = {
                        navController.navigateSafely(Screen.EasterEgg.route)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                )
            }

            item(key = "support_card") {
                AboutSupportCard(
                    onSponsorClick = { openUrl(context, SponsorUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "project_title") {
                AboutSectionHeader(
                    title = stringResource(R.string.about_project_title),
                    subtitle = stringResource(R.string.about_project_subtitle),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            itemsIndexed(
                items = projectLinks,
                key = { _, link -> "project_${link.id}" },
            ) { index, link ->
                AboutLinkCard(
                    link = link,
                    shape = expressiveListShape(index = index, count = projectLinks.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index == 0) 0.dp else 3.dp),
                    onClick = { openUrl(context, link.url) },
                )
            }

            item(key = "maintainer_title") {
                AboutSectionHeader(
                    title = stringResource(R.string.about_maintainer_title),
                    subtitle = stringResource(R.string.about_maintainer_subtitle),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            itemsIndexed(
                items = AboutMaintainers,
                key = { _, contributor -> "maintainer_${contributor.id}" },
            ) { index, contributor ->
                ContributorCard(
                    contributor = contributor,
                    shape = expressiveListShape(index = index, count = AboutMaintainers.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index == 0) 0.dp else 3.dp),
                    showContributionCount = false,
                    onCardClick = contributor.githubUrl?.let { url -> { openUrl(context, url) } },
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.screen_about),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigationIconClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }
}

@Composable
private fun AboutHeroCard(
    versionName: String,
    onVersionLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        shape = heroShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pixelplayer_base_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onVersionLongPress()
                                },
                            )
                        },
                ) {
                    Text(
                        text = stringResource(R.string.about_version_format, versionName),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CommunitySignalsRow()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunitySignalsRow() {
    val labels = listOf(
        stringResource(R.string.about_signal_open_source) to Icons.Rounded.Public,
        stringResource(R.string.about_signal_community_first) to Icons.Rounded.AutoAwesome,
        stringResource(R.string.about_signal_material3) to Icons.Rounded.Palette,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { (label, icon) ->
            Surface(
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutSupportCard(
    onSponsorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = AbsoluteSmoothCornerShape(30.dp, 60)

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_favorite_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_support_eyebrow),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.about_support_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    stringResource(R.string.about_support_chip_foss),
                    stringResource(R.string.about_support_chip_ci),
                    stringResource(R.string.about_support_chip_streaming),
                ).forEach { label ->
                    Surface(
                        shape = AbsoluteSmoothCornerShape(16.dp, 60),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onSponsorClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_support_cta),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AboutLinkCard(
    link: ProjectLink,
    shape: AbsoluteSmoothCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(R.string.cd_about_open_link, link.title),
                onClick = onClick,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painter = painterResource(link.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = link.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Icon(
                painter = painterResource(R.drawable.rounded_chevron_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ContributorCard(
    contributor: Contributor,
    shape: AbsoluteSmoothCornerShape,
    modifier: Modifier = Modifier,
    showContributionCount: Boolean,
    onCardClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onCardClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onCardClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .then(clickableModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContributorAvatar(
                name = contributor.displayName,
                avatarUrl = contributor.avatarUrl,
                iconRes = contributor.iconRes ?: R.drawable.rounded_person_24,
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = contributor.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )

                contributor.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    contributor.badge?.let { badge ->
                        ContributorLabel(text = badge)
                    }
                    if (showContributionCount && contributor.contributions != null) {
                        ContributorLabel(
                            text = stringResource(
                                R.string.about_contributions_format,
                                contributor.contributions,
                            ),
                        )
                    }
                }
            }

            SocialIconButton(
                painterRes = R.drawable.github,
                contentDescription = stringResource(R.string.cd_open_github_profile),
                url = contributor.githubUrl,
            )
            SocialIconButton(
                painterRes = R.drawable.telegram,
                contentDescription = stringResource(R.string.cd_open_telegram_profile),
                url = contributor.telegramUrl,
            )
        }
    }
}

@Composable
private fun ContributorLabel(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContributorAvatar(
    name: String,
    avatarUrl: String?,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val letterBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val letterTint = MaterialTheme.colorScheme.onSurfaceVariant
    val initial = name.removePrefix("@").firstOrNull()?.uppercase() ?: "?"
    var cachedBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 2.dp,
    ) {
        when {
            cachedBitmap != null -> {
                Image(
                    bitmap = cachedBitmap!!,
                    contentDescription = stringResource(R.string.cd_contributor_avatar, name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            !avatarUrl.isNullOrBlank() -> {
                SmartImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_contributor_avatar, name),
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    placeholderResId = iconRes ?: R.drawable.ic_music_placeholder,
                    errorResId = R.drawable.rounded_broken_image_24,
                    targetSize = Size(96, 96),
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            cachedBitmap = state.result.drawable.toBitmap().asImageBitmap()
                        }
                    },
                )
            }
            iconRes != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(letterBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = stringResource(R.string.cd_contributor_icon, name),
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(letterBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = letterTint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialIconButton(
    painterRes: Int,
    contentDescription: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    IconButton(
        onClick = { openUrl(context, url) },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun expressiveListShape(index: Int, count: Int): AbsoluteSmoothCornerShape {
    val outer = 22.dp
    val inner = 8.dp

    return when {
        count <= 1 -> AbsoluteSmoothCornerShape(outer, 60)
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = outer,
            cornerRadiusTR = outer,
            cornerRadiusBL = inner,
            cornerRadiusBR = inner,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = inner,
            cornerRadiusTR = inner,
            cornerRadiusBL = outer,
            cornerRadiusBR = outer,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        else -> AbsoluteSmoothCornerShape(inner, 60)
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try {
        url.toUri()
    } catch (_: Throwable) {
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Ignore if no handler is available.
    }
}
