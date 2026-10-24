@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.recap

import android.icu.text.ListFormatter
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.stats.ListeningPersonality
import com.lostf1sh.pixelplayeross.ui.theme.ShapeCache
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bars on the listening clock, one per hour. */
private const val HOURS_PER_DAY = 24

/** What the summary slide's buttons do; the rest of the story only reads. */
@Immutable
internal data class RecapSummaryActions(
    val onShare: (GraphicsLayer) -> Unit,
    val onPlayTopSongs: () -> Unit,
    val onReplay: () -> Unit
)

/**
 * One page of the story.
 *
 * @param reveal the page's entrance progress from 0 to 1; only read in draw phases.
 * @param isActive whether the page is settled on screen with motion allowed, which gates its
 *   looping decorations.
 * @param scrollState the page's vertical scroll, for pages that do not fit; hoisted so the story
 *   can pause while it is being scrolled.
 */
@Composable
internal fun RecapSlideContent(
    slide: RecapSlide,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    summaryActions: RecapSummaryActions,
    modifier: Modifier = Modifier
) {
    // Decorations bleed off the slide's edges; keep them out of the neighbouring pages.
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        when (slide) {
            is RecapSlide.Intro -> IntroSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.Minutes -> MinutesSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.Months -> MonthsSlide(slide, palette, reveal, contentPadding, scrollState)
            is RecapSlide.Clock -> ClockSlide(slide, palette, reveal, contentPadding, scrollState)
            is RecapSlide.Dedication -> DedicationSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.Genres -> GenresSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.Decade -> DecadeSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.TopAlbums -> AlbumsSlide(slide, palette, reveal, contentPadding, scrollState)
            is RecapSlide.TopArtists -> ArtistsSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.TopSongs -> SongsSlide(slide, palette, reveal, isActive, contentPadding, scrollState)
            is RecapSlide.Summary -> RecapSummarySlide(slide, palette, reveal, contentPadding, scrollState, summaryActions)
        }
    }
}

// region Intro and minutes

@Composable
private fun IntroSlide(
    slide: RecapSlide.Intro,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState, horizontalAlignment = Alignment.CenterHorizontally) {
        RecapKicker(
            text = stringResource(R.string.recap_intro_kicker),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 340.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    val fraction = revealFraction(reveal(), order = 1f)
                    alpha = fraction
                    scaleX = 0.6f + 0.4f * fraction
                    scaleY = 0.6f + 0.4f * fraction
                },
            contentAlignment = Alignment.Center
        ) {
            RecapMorphBlob(color = palette.accent, active = isActive, modifier = Modifier.fillMaxSize())
            RecapHeadline(
                text = String.format(LocalConfiguration.current.locales[0], "%d", slide.year),
                color = palette.onAccent,
                maxFontSize = 104.sp,
                minFontSize = 48.sp,
                maxLines = 1,
                weight = 950,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        RecapHeadline(
            text = stringResource(
                if (slide.isSoFar) R.string.recap_intro_title_so_far else R.string.recap_intro_title
            ),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
        Spacer(Modifier.height(12.dp))
        RecapBody(
            text = stringResource(R.string.recap_intro_subtitle),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 4)
        )
        Spacer(Modifier.height(36.dp))
        Row(
            modifier = Modifier
                .recapReveal(reveal, order = 6)
                .clip(CircleShape)
                .background(palette.content.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.recap_tap_hint),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = rememberRecapFlexFont(weight = 620, width = 106f),
                color = palette.content
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = palette.content,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MinutesSlide(
    slide: RecapSlide.Minutes,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val totalMinutes = recapListenedMinutes(slide.totalMs)
    val minutesForPlural = totalMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val days = TimeUnit.MILLISECONDS.toDays(slide.totalMs).toInt()
    val hours = TimeUnit.MILLISECONDS.toHours(slide.totalMs).toInt()
    val locale = LocalConfiguration.current.locales[0]
    val activeDays = remember(slide.activeDays, locale) {
        NumberFormat.getIntegerInstance(locale).format(slide.activeDays)
    }

    Box(Modifier.fillMaxSize()) {
        RecapBackdropShape(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = palette.accent.copy(alpha = 0.18f),
            active = isActive,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 100.dp)
        )
        RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
            RecapKicker(
                text = stringResource(R.string.recap_minutes_kicker, slide.year),
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 0)
            )
            Spacer(Modifier.height(8.dp))
            RecapCountUpText(
                target = totalMinutes,
                progress = { revealSpan(reveal(), start = 0.08f, length = 0.9f) },
                color = palette.content,
                modifier = Modifier
                    .fillMaxWidth()
                    .recapReveal(reveal, order = 1)
            )
            Text(
                text = pluralStringResource(R.plurals.recap_minutes_unit, minutesForPlural),
                modifier = Modifier.recapReveal(reveal, order = 2),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = rememberRecapFlexFont(weight = 720, width = 114f),
                color = palette.content
            )
            val equivalent = when {
                days >= 1 -> pluralStringResource(R.plurals.recap_minutes_days_equivalent, days, days)
                hours >= 1 -> pluralStringResource(R.plurals.recap_minutes_hours_equivalent, hours, hours)
                else -> null
            }
            if (equivalent != null) {
                Spacer(Modifier.height(24.dp))
                RecapBody(
                    text = equivalent,
                    color = palette.content,
                    modifier = Modifier.recapReveal(reveal, order = 3)
                )
            }
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .recapReveal(reveal, order = 4),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecapStatTile(
                    label = stringResource(R.string.recap_minutes_daily_average),
                    value = formatRecapDuration(slide.averageDailyMs),
                    color = palette.content,
                    modifier = Modifier.weight(1f)
                )
                RecapStatTile(
                    label = stringResource(R.string.recap_minutes_active_days),
                    value = activeDays,
                    color = palette.content,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// endregion

// region Months and clock

@Composable
private fun MonthsSlide(
    slide: RecapSlide.Months,
    palette: RecapPalette,
    reveal: () -> Float,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val locale = LocalConfiguration.current.locales[0]
    val peakMonth = remember(slide.peakMonthIndex, locale) {
        Month.of(slide.peakMonthIndex + 1)
            .getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
    }
    val monthLabels = remember(locale) {
        Month.entries.map { it.getDisplayName(TextStyle.NARROW_STANDALONE, locale) }
    }
    val peakMs = slide.monthlyMs[slide.peakMonthIndex]
    // The bars and their one-letter labels are hidden from screen readers; this reads them out.
    val resources = LocalResources.current
    val chartDescription = remember(slide.monthlyMs, locale, resources) {
        val monthNames = Month.entries.map { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) }
        resources.getString(
            R.string.recap_cd_months_chart,
            ListFormatter.getInstance(locale).format(recapChartEntries(resources, monthNames, slide.monthlyMs))
        )
    }

    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
        RecapKicker(
            text = stringResource(R.string.recap_months_kicker),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(8.dp))
        RecapHeadline(
            text = peakMonth,
            color = palette.content,
            maxFontSize = 64.sp,
            maxLines = 1,
            weight = 900,
            modifier = Modifier.recapReveal(reveal, order = 1)
        )
        Spacer(Modifier.height(8.dp))
        RecapBody(
            text = stringResource(R.string.recap_months_detail, formatRecapDuration(peakMs)),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 2)
        )
        Spacer(Modifier.height(36.dp))
        MonthBars(
            monthlyMs = slide.monthlyMs,
            peakIndex = slide.peakMonthIndex,
            palette = palette,
            reveal = reveal,
            description = chartDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .recapReveal(reveal, order = 3, distance = 0.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .recapReveal(reveal, order = 4, distance = 0.dp)
        ) {
            monthLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == slide.peakMonthIndex) FontWeight.Bold else FontWeight.Normal,
                    color = palette.content.copy(alpha = if (index == slide.peakMonthIndex) 1f else 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MonthBars(
    monthlyMs: List<Long>,
    peakIndex: Int,
    palette: RecapPalette,
    reveal: () -> Float,
    description: String,
    modifier: Modifier = Modifier
) {
    val max = monthlyMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        val count = monthlyMs.size
        if (count == 0) return@Canvas
        val slot = size.width / count
        val barWidth = slot * 0.62f
        val radius = CornerRadius(barWidth / 2f)
        val progress = reveal()
        // Follows the reading direction like the month labels underneath.
        val rtl = layoutDirection == LayoutDirection.Rtl
        monthlyMs.forEachIndexed { index, value ->
            val position = if (rtl) count - 1 - index else index
            val left = slot * position + (slot - barWidth) / 2f
            drawRoundRect(
                color = palette.content.copy(alpha = 0.08f),
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius
            )
            val grow = revealFraction(progress, order = 3f + index * 0.35f)
            val height = (size.height * value / max * grow).coerceAtLeast(if (value > 0L) barWidth * grow else 0f)
            if (height > 0f) {
                drawRoundRect(
                    color = if (index == peakIndex) palette.content else palette.content.copy(alpha = 0.4f),
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
private fun ClockSlide(
    slide: RecapSlide.Clock,
    palette: RecapPalette,
    reveal: () -> Float,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // One label per hour of the day, midnight first like the clock's bars.
    val hourLabels = remember(locale, context) {
        // ICU formats its own skeleton patterns, which java.time cannot always parse.
        val skeleton = if (DateFormat.is24HourFormat(context)) "H" else "ha"
        val formatter = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale)
        val calendar = android.icu.util.Calendar.getInstance(locale)
        List(HOURS_PER_DAY) { hour ->
            calendar.clear()
            calendar.set(2000, android.icu.util.Calendar.JANUARY, 1, hour, 0)
            formatter.format(calendar.time)
        }
    }
    // The clock face is one node for screen readers, which hear every hour's listening.
    val resources = LocalResources.current
    val clockDescription = remember(slide.hourlyMs, hourLabels, locale, resources) {
        resources.getString(
            R.string.recap_cd_clock_chart,
            ListFormatter.getInstance(locale).format(recapChartEntries(resources, hourLabels, slide.hourlyMs))
        )
    }
    val favoriteDay = slide.favoriteDay?.let { day -> rememberDayName(day, locale) }

    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState, horizontalAlignment = Alignment.CenterHorizontally) {
        RecapKicker(
            text = stringResource(R.string.recap_clock_kicker),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 320.dp)
                .aspectRatio(1f)
                .clearAndSetSemantics { contentDescription = clockDescription }
                .recapReveal(reveal, order = 1, distance = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            ListeningClock(
                hourlyMs = slide.hourlyMs,
                palette = palette,
                reveal = reveal,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp)
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slide.personality.icon,
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
            // A clock face is not mirrored in right-to-left layouts, so neither are its labels.
            val labelStyle = MaterialTheme.typography.labelMedium
            val labelColor = palette.content.copy(alpha = 0.75f)
            Text(hourLabels[0], style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.TopCenter))
            Text(hourLabels[6], style = labelStyle, color = labelColor, modifier = Modifier.align(AbsoluteAlignment.CenterRight))
            Text(hourLabels[12], style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.BottomCenter))
            Text(hourLabels[18], style = labelStyle, color = labelColor, modifier = Modifier.align(AbsoluteAlignment.CenterLeft))
        }
        Spacer(Modifier.height(20.dp))
        RecapHeadline(
            text = stringResource(slide.personality.titleRes),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
        Spacer(Modifier.height(8.dp))
        RecapBody(
            text = stringResource(slide.personality.descriptionRes),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 4)
        )
        if (favoriteDay != null) {
            Spacer(Modifier.height(20.dp))
            RecapChip(
                text = stringResource(R.string.recap_clock_favorite_day, favoriteDay),
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 5)
            )
        }
    }
}

/** 24 radial bars, midnight at the top and running clockwise like a clock face. */
@Composable
private fun ListeningClock(
    hourlyMs: List<Long>,
    palette: RecapPalette,
    reveal: () -> Float,
    modifier: Modifier = Modifier
) {
    val max = hourlyMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val outer = size.minDimension / 2f
        val inner = outer * 0.36f
        val stroke = (2f * PI.toFloat() * inner / hourlyMs.size) * 0.62f
        val progress = reveal()
        hourlyMs.forEachIndexed { hour, value ->
            val angle = (hour / hourlyMs.size.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val direction = Offset(cos(angle), sin(angle))
            val start = center + direction * (inner + stroke / 2f)
            val trackEnd = center + direction * (outer - stroke / 2f)
            drawLine(
                color = palette.content.copy(alpha = 0.06f),
                start = start,
                end = trackEnd,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val grow = revealFraction(progress, order = 2f + hour * 0.12f)
            val length = (outer - inner - stroke) * (value.toFloat() / max) * grow
            if (value > 0L && grow > 0f) {
                drawLine(
                    color = if (value == max) palette.content else palette.content.copy(alpha = 0.55f),
                    start = start,
                    end = start + direction * length,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private val ListeningPersonality.icon: ImageVector
    get() = when (this) {
        ListeningPersonality.EARLY_BIRD -> Icons.Rounded.FreeBreakfast
        ListeningPersonality.DAYTIME -> Icons.Rounded.WbSunny
        ListeningPersonality.EVENING -> Icons.Rounded.WbTwilight
        ListeningPersonality.NIGHT_OWL -> Icons.Rounded.Bedtime
    }

internal val ListeningPersonality.titleRes: Int
    get() = when (this) {
        ListeningPersonality.EARLY_BIRD -> R.string.recap_personality_early_bird
        ListeningPersonality.DAYTIME -> R.string.recap_personality_daytime
        ListeningPersonality.EVENING -> R.string.recap_personality_evening
        ListeningPersonality.NIGHT_OWL -> R.string.recap_personality_night_owl
    }

private val ListeningPersonality.descriptionRes: Int
    get() = when (this) {
        ListeningPersonality.EARLY_BIRD -> R.string.recap_personality_early_bird_desc
        ListeningPersonality.DAYTIME -> R.string.recap_personality_daytime_desc
        ListeningPersonality.EVENING -> R.string.recap_personality_evening_desc
        ListeningPersonality.NIGHT_OWL -> R.string.recap_personality_night_owl_desc
    }

@Composable
private fun rememberDayName(day: DayOfWeek, locale: Locale): String = remember(day, locale) {
    // Standalone: shown as a value after a label, not inside a sentence.
    day.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

// endregion

// region Dedication, genres and decade

@Composable
private fun DedicationSlide(
    slide: RecapSlide.Dedication,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val locale = LocalConfiguration.current.locales[0]
    val biggestDay = remember(slide.biggestDay, locale) {
        // ICU, unlike java.time, also localizes the digits (e.g. Arabic-Indic ones). It gets an
        // instant, not calendar fields: a locale that asks for another calendar (such as
        // th-TH-u-ca-buddhist) then shows the same day in that calendar.
        val format = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.MEDIUM, locale).apply {
            timeZone = android.icu.util.TimeZone.GMT_ZONE
        }
        format.format(Date(slide.biggestDay.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))
    }
    val streak = slide.longestStreakDays.coerceAtLeast(1)

    Box(Modifier.fillMaxSize()) {
        RecapBackdropShape(
            shape = MaterialShapes.Sunny.toShape(),
            color = palette.accent.copy(alpha = 0.16f),
            active = isActive,
            size = 300.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 110.dp, y = 120.dp)
        )
        RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
            RecapKicker(
                text = stringResource(R.string.recap_dedication_kicker),
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 0)
            )
            Spacer(Modifier.height(8.dp))
            RecapCountUpText(
                target = streak.toLong(),
                progress = { revealSpan(reveal(), start = 0.08f, length = 0.7f) },
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 1)
            )
            Text(
                text = pluralStringResource(R.plurals.recap_streak_unit, streak),
                modifier = Modifier.recapReveal(reveal, order = 2),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = rememberRecapFlexFont(weight = 720, width = 114f),
                color = palette.content
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .recapReveal(reveal, order = 3),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecapStatTile(
                    label = stringResource(R.string.recap_dedication_longest_session),
                    value = formatRecapDuration(slide.longestSessionMs),
                    color = palette.content,
                    modifier = Modifier.weight(1f)
                )
                RecapStatTile(
                    label = stringResource(R.string.recap_dedication_biggest_day),
                    value = biggestDay,
                    detail = formatRecapDuration(slide.biggestDayMs),
                    color = palette.content,
                    modifier = Modifier.weight(1f)
                )
            }
            slide.firstSong?.let { first ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .recapReveal(reveal, order = 4)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.content.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RecapArtwork(
                        model = first.artUri,
                        shape = RoundedCornerShape(14.dp),
                        palette = palette,
                        targetSizePx = 192,
                        modifier = Modifier.size(56.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.recap_dedication_first_song),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.content.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = first.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = rememberRecapFlexFont(weight = 720, width = 106f),
                            color = palette.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        first.artist?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.content.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenresSlide(
    slide: RecapSlide.Genres,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val leader = slide.genres.first()
    val maxShare = leader.share.coerceAtLeast(0.0001f)
    val rankShapes = listOf(
        MaterialShapes.Cookie9Sided.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
        MaterialShapes.Sunny.toShape(),
        MaterialShapes.Cookie6Sided.toShape(),
        MaterialShapes.Pentagon.toShape()
    )

    Box(Modifier.fillMaxSize()) {
        RecapBackdropShape(
            shape = ShapeCache.expressiveClover,
            color = palette.accent.copy(alpha = 0.16f),
            active = isActive,
            size = 280.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-120).dp, y = 120.dp)
        )
        RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
            RecapKicker(
                text = stringResource(R.string.recap_genres_kicker),
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 0)
            )
            Spacer(Modifier.height(8.dp))
            RecapHeadline(
                text = leader.name,
                color = palette.content,
                maxFontSize = 56.sp,
                weight = 900,
                modifier = Modifier.recapReveal(reveal, order = 1)
            )
            Spacer(Modifier.height(8.dp))
            RecapBody(
                text = stringResource(R.string.recap_genres_share, percent.format(leader.share.toDouble())),
                color = palette.content,
                modifier = Modifier.recapReveal(reveal, order = 2)
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slide.genres.forEachIndexed { index, genre ->
                    val barFraction = genre.share / maxShare
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .recapReveal(reveal, order = 3 + index)
                            .clip(RoundedCornerShape(22.dp))
                            .drawBehind {
                                drawRect(palette.content.copy(alpha = 0.08f))
                                // Bars trail their rows slightly; the last one still lands by the end.
                                val grow = revealFraction(reveal(), order = 3.5f + index * 0.6f)
                                val width = size.width * (0.2f + 0.8f * barFraction) * grow
                                drawRoundRect(
                                    color = palette.content.copy(alpha = 0.14f),
                                    size = Size(width, size.height),
                                    topLeft = if (layoutDirection == LayoutDirection.Rtl) {
                                        Offset(size.width - width, 0f)
                                    } else {
                                        Offset.Zero
                                    },
                                    cornerRadius = CornerRadius(size.height / 2f)
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(rankShapes[index % rankShapes.size])
                                .background(if (index == 0) palette.accent else palette.content.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = NumberFormat.getIntegerInstance(locale).format(index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = rememberRecapFlexFont(weight = 820, width = 110f),
                                color = if (index == 0) palette.onAccent else palette.content
                            )
                        }
                        Text(
                            text = genre.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = rememberRecapFlexFont(weight = 680, width = 106f),
                            color = palette.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = percent.format(genre.share.toDouble()),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = rememberRecapFlexFont(weight = 720, width = 104f),
                            color = palette.content
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecadeSlide(
    slide: RecapSlide.Decade,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(slide.share, locale) {
        NumberFormat.getPercentInstance(locale).format(slide.share.toDouble())
    }
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState, horizontalAlignment = Alignment.CenterHorizontally) {
        RecapKicker(
            text = stringResource(R.string.recap_decade_kicker),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 320.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    val fraction = revealFraction(reveal(), order = 1f)
                    alpha = fraction
                    rotationZ = -40f * (1f - fraction)
                    scaleX = 0.7f + 0.3f * fraction
                    scaleY = 0.7f + 0.3f * fraction
                },
            contentAlignment = Alignment.Center
        ) {
            RecapMorphBlob(
                color = palette.accent,
                active = isActive,
                shapes = remember { listOf(MaterialShapes.Sunny, MaterialShapes.VerySunny, MaterialShapes.Cookie7Sided) },
                modifier = Modifier.fillMaxSize()
            )
            RecapHeadline(
                text = stringResource(R.string.recap_decade_value, slide.decade),
                color = palette.onAccent,
                maxFontSize = 72.sp,
                minFontSize = 28.sp,
                maxLines = 1,
                weight = 940,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        RecapBody(
            text = stringResource(R.string.recap_decade_detail, percent),
            color = palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
    }
}

// endregion

// region Top albums, artists and songs

@Composable
private fun AlbumsSlide(
    slide: RecapSlide.TopAlbums,
    palette: RecapPalette,
    reveal: () -> Float,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val leader = slide.albums.first()
    val fanned = slide.albums.take(3)
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
        RecapKicker(
            text = stringResource(R.string.recap_albums_kicker),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(20.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center
        ) {
            val coverSize = 190.dp
            val spread = (maxWidth - coverSize) / 2
            // Back covers first so the leader is drawn on top.
            fanned.indices.reversed().forEach { index ->
                val side = when (index) {
                    1 -> -1f
                    2 -> 1f
                    else -> 0f
                }
                val album = fanned[index]
                RecapArtwork(
                    model = album.artUri,
                    shape = RoundedCornerShape(if (index == 0) 28.dp else 22.dp),
                    palette = palette,
                    modifier = Modifier
                        .size(coverSize)
                        .graphicsLayer {
                            val fraction = revealFraction(reveal(), order = 1f + index * 0.6f)
                            alpha = fraction
                            translationX = side * spread.toPx() * 0.82f * fraction
                            rotationZ = side * 9f * fraction
                            val scale = if (index == 0) 0.85f + 0.15f * fraction else 0.78f
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        RecapHeadline(
            text = leader.title,
            color = palette.content,
            maxFontSize = 36.sp,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.recapReveal(reveal, order = 4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecapChip(text = formatRecapDuration(leader.durationMs), color = palette.content)
            RecapChip(
                text = pluralStringResource(R.plurals.stats_plays_count, leader.playCount, leader.playCount),
                color = palette.content
            )
        }
        RankedList(
            reveal = reveal,
            firstOrder = 5,
            rows = slide.albums.drop(1).mapIndexed { index, album ->
                RankedRowData(
                    rank = index + 2,
                    title = album.title,
                    subtitle = null,
                    trailing = formatRecapDuration(album.durationMs),
                    artUri = album.artUri
                )
            },
            artShape = RoundedCornerShape(10.dp),
            palette = palette
        )
        Spacer(Modifier.height(16.dp))
        RecapBody(
            text = pluralStringResource(R.plurals.recap_unique_albums, slide.uniqueAlbums, slide.uniqueAlbums),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 6)
        )
    }
}

@Composable
private fun ArtistsSlide(
    slide: RecapSlide.TopArtists,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val leader = slide.artists.first()
    val spin = rememberSpin(active = isActive, periodMillis = 30_000)
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
        RecapKicker(
            text = stringResource(R.string.recap_artists_kicker),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(20.dp))
        // The cookie turns while the picture inside counter-turns and stays upright.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(210.dp)
                .graphicsLayer {
                    val fraction = revealFraction(reveal(), order = 1f)
                    alpha = fraction
                    scaleX = 0.7f + 0.3f * fraction
                    scaleY = 0.7f + 0.3f * fraction
                    rotationZ = spin.value
                    shape = ShapeCache.expressiveAvatar
                    clip = true
                }
                .background(palette.accent)
        ) {
            RecapArtwork(
                model = leader.imageUri,
                shape = RectangleShape,
                palette = palette,
                monogram = rememberMonogram(leader.name),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -spin.value
                        scaleX = 1.16f
                        scaleY = 1.16f
                    }
            )
        }
        Spacer(Modifier.height(24.dp))
        RecapHeadline(
            text = leader.name,
            color = palette.content,
            maxFontSize = 44.sp,
            weight = 880,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.recapReveal(reveal, order = 4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecapChip(text = formatRecapDuration(leader.durationMs), color = palette.content)
            RecapChip(
                text = pluralStringResource(R.plurals.stats_tracks_count, leader.songCount, leader.songCount),
                color = palette.content
            )
        }
        RankedList(
            reveal = reveal,
            firstOrder = 5,
            rows = slide.artists.drop(1).mapIndexed { index, artist ->
                RankedRowData(
                    rank = index + 2,
                    title = artist.name,
                    subtitle = null,
                    trailing = formatRecapDuration(artist.durationMs),
                    artUri = artist.imageUri,
                    monogramSource = artist.name
                )
            },
            artShape = CircleShape,
            palette = palette
        )
        Spacer(Modifier.height(16.dp))
        RecapBody(
            text = pluralStringResource(R.plurals.recap_unique_artists, slide.uniqueArtists, slide.uniqueArtists),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 6)
        )
    }
}

@Composable
private fun SongsSlide(
    slide: RecapSlide.TopSongs,
    palette: RecapPalette,
    reveal: () -> Float,
    isActive: Boolean,
    contentPadding: PaddingValues,
    scrollState: ScrollState
) {
    val leader = slide.songs.first()
    val spin = rememberSpin(active = isActive, periodMillis = 6_000)
    // The disc starts tucked towards the sleeve, which sits on the reading-start side.
    val towardsSleeve = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    RecapSlideColumn(contentPadding = contentPadding, scrollState = scrollState) {
        RecapKicker(
            text = stringResource(R.string.recap_songs_kicker),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 0)
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 250.dp, height = 200.dp)
        ) {
            // A record slides out from behind the sleeve and spins while the slide is shown.
            VinylDisc(
                palette = palette,
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        val fraction = revealFraction(reveal(), order = 2f)
                        translationX = towardsSleeve * 60.dp.toPx() * (1f - fraction)
                        alpha = fraction
                        rotationZ = spin.value
                    }
            )
            RecapArtwork(
                model = leader.artUri,
                shape = RoundedCornerShape(26.dp),
                palette = palette,
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterStart)
                    .recapReveal(reveal, order = 1)
            )
        }
        Spacer(Modifier.height(24.dp))
        RecapHeadline(
            text = leader.title,
            color = palette.content,
            maxFontSize = 40.sp,
            weight = 860,
            modifier = Modifier.recapReveal(reveal, order = 3)
        )
        leader.artist?.let { artist ->
            Spacer(Modifier.height(4.dp))
            RecapBody(
                text = artist,
                color = palette.content,
                maxLines = 1,
                modifier = Modifier.recapReveal(reveal, order = 3)
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.recapReveal(reveal, order = 4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecapChip(
                text = pluralStringResource(R.plurals.stats_plays_count, leader.playCount, leader.playCount),
                color = palette.content
            )
            RecapChip(text = formatRecapDuration(leader.durationMs), color = palette.content)
        }
        RankedList(
            reveal = reveal,
            firstOrder = 5,
            rows = slide.songs.drop(1).mapIndexed { index, song ->
                RankedRowData(
                    rank = index + 2,
                    title = song.title,
                    subtitle = song.artist,
                    trailing = null,
                    artUri = song.artUri
                )
            },
            artShape = RoundedCornerShape(10.dp),
            palette = palette
        )
        Spacer(Modifier.height(16.dp))
        RecapBody(
            text = pluralStringResource(R.plurals.recap_unique_songs, slide.uniqueSongs, slide.uniqueSongs),
            color = palette.content,
            modifier = Modifier.recapReveal(reveal, order = 6)
        )
    }
}

@Composable
private fun VinylDisc(palette: RecapPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val radius = size.minDimension / 2f
        drawCircle(color = lerp(Color(0xFF121212), palette.accent, 0.14f), radius = radius)
        // Grooves, then the label with an off-centre mark so the spin is visible.
        for (step in 1..5) {
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = radius * (0.48f + step * 0.09f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        drawCircle(color = palette.accent, radius = radius * 0.34f)
        drawCircle(
            color = palette.onAccent.copy(alpha = 0.7f),
            radius = radius * 0.07f,
            center = center + Offset(radius * 0.18f, -radius * 0.12f)
        )
        drawCircle(color = palette.background, radius = radius * 0.05f)
    }
}

@Immutable
private data class RankedRowData(
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val trailing: String?,
    val artUri: String?,
    /** Name to draw initials from when [artUri] is missing; null keeps the music placeholder. */
    val monogramSource: String? = null
)

@Composable
private fun RankedList(
    reveal: () -> Float,
    firstOrder: Int,
    rows: List<RankedRowData>,
    artShape: Shape,
    palette: RecapPalette
) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(22.dp))
    Column(
        modifier = Modifier.recapReveal(reveal, order = firstOrder),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            RecapRankRow(
                rank = row.rank,
                title = row.title,
                subtitle = row.subtitle,
                trailing = row.trailing,
                palette = palette,
                leading = {
                    RecapArtwork(
                        model = row.artUri,
                        shape = artShape,
                        palette = palette,
                        monogram = row.monogramSource?.let { rememberMonogram(it) },
                        targetSizePx = 128,
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
        }
    }
}

// endregion
