package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.presentation.viewmodel.AdvancedStatsViewModel
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.RecentlyPlayedRangeSelector
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.SmartImageCompactListTargetSize
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import com.lostf1sh.pixelplayeross.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberGoogleSansFlex(
    weight: Int,
    width: Float,
    rond: Float = 100f
): FontFamily {
    return remember(weight, width, rond) {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.Setting("ROND", rond)
                )
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdvancedStatsScreen(
    navController: NavController,
    viewModel: AdvancedStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val titleFont = rememberGoogleSansFlex(weight = 750, width = 125f)
    val bodyFont = rememberGoogleSansFlex(weight = 500, width = 100f)

    val density = LocalDensity.current
    val dailyListState = rememberLazyListState()
    val habitsListState = rememberLazyListState()
    val activeListState = if (uiState.activeTab == 0) dailyListState else habitsListState
    val coroutineScope = rememberCoroutineScope()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 176.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value, minTopBarHeightPx, maxTopBarHeightPx) {
        collapseFraction = 1f -
            ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx))
                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember(activeListState, minTopBarHeightPx, maxTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingUp = delta < 0

                if (!isScrollingUp &&
                    (activeListState.firstVisibleItemIndex > 0 || activeListState.firstVisibleItemScrollOffset > 0)
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

                val canConsume = !(isScrollingUp && newHeight == minTopBarHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(activeListState.isScrollInProgress, uiState.activeTab) {
        if (!activeListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = activeListState.firstVisibleItemIndex == 0 &&
                activeListState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != target) {
                coroutineScope.launch {
                    topBarHeight.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val tabsHeight = 76.dp
    val contentTopPadding = currentTopBarHeightDp + tabsHeight + 4.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (uiState.activeTab == 0) {
            DailyLogTabContent(
                uiState = uiState,
                viewModel = viewModel,
                listState = dailyListState,
                topContentPadding = contentTopPadding
            )
        } else {
            HabitsTrendsTabContent(
                uiState = uiState,
                viewModel = viewModel,
                listState = habitsListState,
                topContentPadding = contentTopPadding
            )
        }

        val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha))
                .padding(bottom = 12.dp)
                .zIndex(5f)
        ) {
            CollapsibleCommonTopBar(
                title = "Listening Insights",
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackClick = { navController.popBackStack() },
                containerColor = Color.Transparent
            )

            ExpressiveSegmentedSelector(
                selectedTabIndex = uiState.activeTab,
                onTabSelected = { viewModel.selectTab(it) },
                modifier = Modifier
                    .height(52.dp)
                    .padding(horizontal = 16.dp)
            )
        }

    }
}

@Composable
fun ExpressiveSegmentedSelector(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<String> = listOf("Daily Log", "Insights")
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        tabs.forEachIndexed { index, label ->
            customItem(
                buttonGroupContent = {
                    val selected = selectedTabIndex == index
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { onTabSelected(index) },
                        modifier = Modifier
                            .weight(if (selected) 1.15f else 1f)
                            .animateWidth(interactionSource)
                            .height(52.dp)
                            .semantics { role = Role.Tab },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = null,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        interactionSource = interactionSource
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = rememberGoogleSansFlex(
                                weight = if (selected) 720 else 560,
                                width = if (selected) 122f else 104f,
                                rond = if (selected) 92f else 100f
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                menuContent = { menuState ->
                    val selected = selectedTabIndex == index
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            onTabSelected(index)
                            menuState.dismiss()
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun DailyLogTabContent(
    uiState: AdvancedStatsViewModel.AdvancedStatsUiState,
    viewModel: AdvancedStatsViewModel,
    listState: LazyListState,
    topContentPadding: androidx.compose.ui.unit.Dp
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = topContentPadding, bottom = 90.dp)
    ) {
        // Heatmap Calendar Section
        item {
            CalendarHeatmapCard(
                currentMonth = uiState.currentMonth,
                selectedDate = uiState.selectedDate,
                dailyDurations = uiState.dailyDurations,
                goalTargetMs = uiState.goalMinutes * 60_000L,
                onDateSelected = { viewModel.selectDate(it) },
                onPreviousMonth = { viewModel.changeMonth(uiState.currentMonth.minusMonths(1)) },
                onNextMonth = { viewModel.changeMonth(uiState.currentMonth.plusMonths(1)) }
            )
        }

        // Daily Target Goal Card
        item {
            val dailyDur = uiState.dailyDurations[uiState.selectedDate] ?: 0L
            DailyTargetGoalCard(
                durationMs = dailyDur,
                goalMinutes = uiState.goalMinutes,
                goalStreak = uiState.goalStreak,
                onGoalMinutesSelected = { viewModel.setGoalMinutes(it) }
            )
        }

        // Daily Summary Statistics section
        item {
            DailySummaryCard(
                selectedDate = uiState.selectedDate,
                summary = uiState.selectedDaySummary,
                isLoading = uiState.isDaySummaryLoading
            )
        }

        // Timelapse Section - Rendered directly on the background (Not inside a card)
        item {
            DayTimelapseChart(
                hourlyDistribution = uiState.hourlyDistribution
            )
        }

        // Top Songs for Selected Day
        val topSongs = uiState.selectedDaySummary?.topSongs ?: emptyList()
        if (topSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Top Songs on this Day",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(topSongs, key = { it.songId }) { songSummary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display actual album cover using SmartImage
                        SmartImage(
                            model = songSummary.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            targetSize = SmartImageCompactListTargetSize
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = songSummary.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = rememberGoogleSansFlex(weight = 600, width = 100f),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songSummary.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${songSummary.playCount} plays",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = rememberGoogleSansFlex(weight = 650, width = 110f),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val minutes = (songSummary.totalDurationMs / 1000) / 60
                            Text(
                                text = "${minutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitsTrendsTabContent(
    uiState: AdvancedStatsViewModel.AdvancedStatsUiState,
    viewModel: AdvancedStatsViewModel,
    listState: LazyListState,
    topContentPadding: androidx.compose.ui.unit.Dp
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = topContentPadding, bottom = 90.dp)
    ) {
        // Scrollable time range selectors (TabAnimation inside Scrollable TabRow)
        item {
            RangeSelectorRow(
                selectedRange = uiState.selectedRange,
                onRangeSelected = { viewModel.selectRange(it) }
            )
        }

        if (uiState.isRangeSummaryLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        } else {
            val summary = uiState.rangeSummary

            // Music Personality Card
            item {
                val personality = viewModel.getMusicPersonality(summary)
                MusicPersonalityCard(personality = personality)
            }

            // Streak Record Card (Vertically stacked, texts stacked to prevent truncation)
            item {
                StreakCard(longestStreak = summary?.longestStreakDays ?: 0)
            }

            // Active Days Card (Stacked vertically, texts stacked to prevent truncation)
            item {
                ActiveDaysCard(activeDays = summary?.activeDays ?: 0)
            }

            // Prime Listening Window Preferences Card
            item {
                val peakHour = summary?.peakTimeline?.label
                PrimeListeningCard(
                    peakDay = summary?.peakDayLabel,
                    peakHourLabel = peakHour
                )
            }

            // General Habits Info Card
            item {
                GeneralHabitsCard(summary = summary)
            }

            // Top Genres (Progress indicator list)
            val topGenres = summary?.topGenres ?: emptyList()
            if (topGenres.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Genres",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp)
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            val maxDur = topGenres.first().totalDurationMs.coerceAtLeast(1L)
                            topGenres.forEach { genreSummary ->
                                val fraction = genreSummary.totalDurationMs.toFloat() / maxDur
                                val mins = genreSummary.totalDurationMs / 1000 / 60
                                ComparativeProgressBarItem(
                                    title = genreSummary.genre,
                                    subtitle = null, // No subtitle slop
                                    value = "${mins}m",
                                    fraction = fraction,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Top Artists
            val topArtists = summary?.topArtists ?: emptyList()
            if (topArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Artists",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp)
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            val maxPlays = topArtists.first().playCount.coerceAtLeast(1)
                            topArtists.forEach { artistSummary ->
                                val fraction = artistSummary.playCount.toFloat() / maxPlays
                                val mins = artistSummary.totalDurationMs / 1000 / 60
                                ComparativeProgressBarItem(
                                    title = artistSummary.artist,
                                    subtitle = null,
                                    value = "${artistSummary.playCount} plays (${mins}m)",
                                    fraction = fraction,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Top Albums
            val topAlbums = summary?.topAlbums ?: emptyList()
            if (topAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp)
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            val maxDur = topAlbums.first().totalDurationMs.coerceAtLeast(1L)
                            topAlbums.forEach { albumSummary ->
                                val fraction = albumSummary.totalDurationMs.toFloat() / maxDur
                                val mins = albumSummary.totalDurationMs / 1000 / 60
                                ComparativeProgressBarItem(
                                    title = albumSummary.album,
                                    subtitle = null,
                                    value = "${mins}m",
                                    fraction = fraction,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RangeSelectorRow(
    selectedRange: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit
) {
    RecentlyPlayedRangeSelector(
        selected = selectedRange,
        onRangeSelected = onRangeSelected,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
fun CalendarHeatmapCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    dailyDurations: Map<LocalDate, Long>,
    goalTargetMs: Long,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthTitleFont = rememberGoogleSansFlex(weight = 700, width = 120f)
    // Read the locale through the configuration so the month name recomposes when the user
    // changes their language, instead of sticking at whatever it was on first composition.
    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(currentMonth, locale) {
        currentMonth.month.getDisplayName(TextStyle.FULL, locale) + " " + currentMonth.year
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Month selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "Previous Month"
                    )
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = monthTitleFont,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onNextMonth,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Next Month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekday labels
            val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Days Grid
            val firstDay = currentMonth.atDay(1)
            val firstDayOfWeek = (firstDay.dayOfWeek.value - 1) % 7
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = firstDayOfWeek + daysInMonth
            val rowsCount = (totalCells + 6) / 7

            val baseColor = MaterialTheme.colorScheme.primary

            for (r in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (c in 0..6) {
                        val cellIndex = r * 7 + c
                        if (cellIndex < firstDayOfWeek || cellIndex >= totalCells) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val dayNum = cellIndex - firstDayOfWeek + 1
                            val date = currentMonth.atDay(dayNum)
                            val isSelected = date == selectedDate
                            val duration = dailyDurations[date] ?: 0L

                            val isTargetMet = duration >= goalTargetMs

                            // Intensity colors - solid containers with differing alphas.
                            // Fixed bucket thresholds independent of the user's configurable goal.
                            val highContrastThresholdMs = 45 * 60 * 1000L
                            val intensityColor = when {
                                duration <= 0L -> Color.Transparent
                                duration < 15 * 60 * 1000L -> baseColor.copy(alpha = 0.2f)
                                duration < highContrastThresholdMs -> baseColor.copy(alpha = 0.5f)
                                duration < 120 * 60 * 1000L -> baseColor.copy(alpha = 0.8f)
                                else -> baseColor
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(intensityColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                        } else Modifier
                                    )
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = rememberGoogleSansFlex(weight = if (isSelected) 700 else 500, width = 100f),
                                        color = if (duration > highContrastThresholdMs && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isTargetMet) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(4.dp)
                                                .background(
                                                    if (duration > highContrastThresholdMs && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTargetGoalCard(
    durationMs: Long,
    goalMinutes: Int,
    goalStreak: Int,
    onGoalMinutesSelected: (Int) -> Unit
) {
    val targetMs = goalMinutes * 60_000L
    val fraction = (durationMs.toFloat() / targetMs).coerceIn(0f, 1f)
    val isMet = durationMs >= targetMs
    var showPresetPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMet) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isMet) "Daily Goal Achieved" else "Listening Target",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = rememberGoogleSansFlex(weight = 700, width = 125f),
                            color = if (isMet) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        if (goalStreak > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔥 $goalStreak",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = rememberGoogleSansFlex(weight = 700, width = 110f),
                                color = if (isMet) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val targetText = if (isMet) {
                        "${goalMinutes}m target met"
                    } else {
                        val minutesLeft = ((targetMs - durationMs) / 1000 / 60).coerceAtLeast(1)
                        "${minutesLeft}m remaining"
                    }
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = rememberGoogleSansFlex(weight = 500, width = 100f),
                        color = if (isMet) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = if (isMet) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { showPresetPicker = !showPresetPicker },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMet) Icons.Rounded.CheckCircle else Icons.Rounded.AccessTime,
                        contentDescription = "Change listening goal",
                        tint = if (isMet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (showPresetPicker) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.lostf1sh.pixelplayeross.data.preferences.LISTENING_GOAL_PRESET_MINUTES.forEach { preset ->
                        FilterChip(
                            selected = preset == goalMinutes,
                            onClick = {
                                onGoalMinutesSelected(preset)
                                showPresetPicker = false
                            },
                            label = { Text("${preset}m") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailySummaryCard(
    selectedDate: LocalDate,
    summary: PlaybackStatsSummary?,
    isLoading: Boolean
) {
    val numberFont = rememberGoogleSansFlex(weight = 750, width = 135f)
    val labelFont = rememberGoogleSansFlex(weight = 600, width = 110f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLAY TIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = labelFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalMin = (summary?.totalDurationMs ?: 0L) / 1000 / 60
                        val hr = totalMin / 60
                        val min = totalMin % 60
                        val durationStr = if (hr > 0) "${hr}h ${min}m" else "${min}m"
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = numberFont,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TRACKS PLAYED",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = labelFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = (summary?.totalPlayCount ?: 0).toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = numberFont,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayTimelapseChart(
    hourlyDistribution: List<Long>
) {
    val maxVal = hourlyDistribution.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val maxValMin = maxVal / 1000 / 60
    val maxValDisplay = maxValMin.coerceAtLeast(10L)
    
    val yLabel3 = "${maxValDisplay}m"
    val yLabel2 = "${maxValDisplay * 2 / 3}m"
    val yLabel1 = "${maxValDisplay / 3}m"
    val yLabel0 = "0m"
    
    val timeLabels = listOf("0-4", "4-8", "8-12", "12-16", "16-20", "20-24")
    val peakIndex = hourlyDistribution.indexOf(hourlyDistribution.maxOrNull() ?: -1)
    
    val fontTitle = rememberGoogleSansFlex(weight = 700, width = 120f)
    val labelFont = rememberGoogleSansFlex(weight = 500, width = 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "Listening Hours",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = fontTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Chart row - Perfectly balanced grid lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .drawBehind {
                    val lineColor = Color.Gray.copy(alpha = 0.15f)
                    val strokeWidth = 1.dp.toPx()
                    val leftPadding = 40.dp.toPx()
                    val w = size.width
                    val h = size.height

                    drawLine(color = lineColor, start = Offset(leftPadding, 0f), end = Offset(w - leftPadding, 0f), strokeWidth = strokeWidth)
                    drawLine(color = lineColor, start = Offset(leftPadding, h * 0.33f), end = Offset(w - leftPadding, h * 0.33f), strokeWidth = strokeWidth)
                    drawLine(color = lineColor, start = Offset(leftPadding, h * 0.66f), end = Offset(w - leftPadding, h * 0.66f), strokeWidth = strokeWidth)
                    drawLine(color = lineColor, start = Offset(leftPadding, h), end = Offset(w - leftPadding, h), strokeWidth = strokeWidth)
                },
            verticalAlignment = Alignment.Bottom
        ) {
            // Left spacer of 40.dp to balance the right 40.dp Y-axis labels and keep bars centered
            Spacer(modifier = Modifier.width(40.dp))

            // Bars layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                hourlyDistribution.forEachIndexed { index, duration ->
                    val fraction = duration.toFloat() / maxVal
                    val barHeightFraction = fraction * 0.7f // Cap at 70% to reserve 30% height for badge
                    
                    val animatedFraction by animateFloatAsState(
                        targetValue = barHeightFraction,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "BarHeightAnimation"
                    )

                    val isPeak = index == peakIndex && duration > 0L

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (animatedFraction > 0.01f) {
                                // Dynamic bar container
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(animatedFraction)
                                        .width(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPeak) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondary
                                        ),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    // Checkmark star badge is embedded INSIDE the bar, anchored to top with symmetric padding
                                    if (isPeak) {
                                        val starShape = remember { RoundedStarShape(sides = 12, curve = 0.15, rotation = 0f) }
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp) // Symmetric spacing inside the top of the capsule
                                                .size(28.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, shape = starShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .height(4.dp)
                                        .width(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = timeLabels[index],
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = labelFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Y-Axis labels column
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(yLabel3, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(yLabel2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(yLabel1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(yLabel0, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MusicPersonalityCard(
    personality: AdvancedStatsViewModel.MusicPersonality
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (personality.title) {
                    "The Sonic Explorer" -> Icons.AutoMirrored.Rounded.TrendingUp
                    "The Loyal Devotee" -> Icons.Rounded.Favorite
                    "The Music Enthusiast" -> Icons.Rounded.MusicNote
                    else -> Icons.Rounded.Star
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LISTENER ARCHETYPE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = rememberGoogleSansFlex(weight = 600, width = 110f),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = personality.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = rememberGoogleSansFlex(weight = 750, width = 125f),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = personality.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun StreakCard(longestStreak: Int) {
    val numberFont = rememberGoogleSansFlex(weight = 750, width = 135f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Vertically stacked structure to prevent text squishing on small screens
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Whatshot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "$longestStreak days",
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = numberFont,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Streak Record",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Consecutive days of playback activity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActiveDaysCard(activeDays: Int) {
    val numberFont = rememberGoogleSansFlex(weight = 750, width = 135f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Vertically stacked structure to prevent text squishing on small screens
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "$activeDays days",
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = numberFont,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Active Days",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Total calendar days with playback activity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PrimeListeningCard(
    peakDay: String?,
    peakHourLabel: String?
) {
    val fontTitle = rememberGoogleSansFlex(weight = 700, width = 120f)
    val fontHighlight = rememberGoogleSansFlex(weight = 750, width = 130f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Prime Listening Window",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Peak Day Preference row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Today,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Your Favorite Day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = peakDay ?: "No data",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = fontHighlight,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Peak Hour Preference row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Peak Listening Hours",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = peakHourLabel ?: "No data",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = fontHighlight,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun GeneralHabitsCard(
    summary: PlaybackStatsSummary?
) {
    val numberFont = rememberGoogleSansFlex(weight = 750, width = 135f)
    val labelFont = rememberGoogleSansFlex(weight = 600, width = 110f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = rememberGoogleSansFlex(weight = 700, width = 120f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL TIME",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = labelFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val totalMin = (summary?.totalDurationMs ?: 0L) / 1000 / 60
                    val hr = totalMin / 60
                    val min = totalMin % 60
                    val totalStr = if (hr > 0) "${hr}h ${min}m" else "${min}m"
                    Text(
                        text = totalStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = numberFont,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SESSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = labelFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (summary?.totalSessions ?: 0).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = numberFont,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UNIQUE SONGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = labelFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (summary?.uniqueSongs ?: 0).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = numberFont,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
fun ComparativeProgressBarItem(
    title: String,
    subtitle: String?,
    value: String,
    fraction: Float,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = rememberGoogleSansFlex(weight = 600, width = 100f),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = rememberGoogleSansFlex(weight = 650, width = 110f),
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
