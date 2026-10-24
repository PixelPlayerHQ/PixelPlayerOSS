package com.lostf1sh.pixelplayeross.data.stats

import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackSpan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The part of a calendar year a recap covers. */
internal data class RecapBounds(
    val startMillis: Long,
    /** Inclusive: the last millisecond of the year, or now while the year is still running. */
    val endMillis: Long,
    val isYearComplete: Boolean,
    /** Calendar days from January 1 to [endMillis], both included. */
    val daysInRange: Int
)

/** Release decade most of the tagged listening came from. */
internal data class DecadeShare(
    /** Start year of the decade, e.g. 1990. */
    val decade: Int,
    /** Share of all listening, tagged or not, from 0 to 1. */
    val share: Float
)

/** Years a recap can be built for; outside them epoch arithmetic is meaningless or overflows. */
internal val RecapYearRange = 1970..9999

private const val MIN_TAGGED_SHARE = 0.3f
private const val MIN_DECADE_SHARE_OF_TAGGED = 0.25f
private val PlausibleReleaseYears = 1900..2100

/**
 * Bounds of [year] in [zoneId] as of [nowMillis]: January 1 up to the end of the year, or up to
 * now while the year is still running. Null for a year that has not started yet.
 */
internal fun recapYearBounds(year: Int, zoneId: ZoneId, nowMillis: Long): RecapBounds? {
    if (year !in RecapYearRange) return null
    val firstDay = LocalDate.of(year, 1, 1)
    val start = firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
    if (start > nowMillis) return null
    val nextYearStart = firstDay.plusYears(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val isYearComplete = nowMillis >= nextYearStart
    val end = if (isYearComplete) nextYearStart - 1 else nowMillis
    val lastDay = Instant.ofEpochMilli(end).atZone(zoneId).toLocalDate()
    return RecapBounds(
        startMillis = start,
        endMillis = end,
        isYearComplete = isYearComplete,
        daysInRange = (ChronoUnit.DAYS.between(firstDay, lastDay) + 1).toInt()
    )
}

/** Listened time per local hour of the day (index 0 is midnight), splitting spans at hour edges. */
internal fun hourlyDurations(spans: List<PlaybackSpan>, zoneId: ZoneId): LongArray {
    val totals = LongArray(24)
    val rules = zoneId.rules
    spans.forEach { span ->
        var cursor = span.startMillis
        while (cursor < span.endMillis) {
            val instant = Instant.ofEpochMilli(cursor)
            val zoned = instant.atZone(zoneId)
            // The local hour changes at the next whole hour at the current offset, or earlier when
            // the offset itself changes. Both lie after the cursor, so every slice moves forward,
            // even across offset changes that are not whole hours (Lord Howe shifts by 30 minutes).
            val nextHour = zoned.toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .toInstant(zoned.offset)
                .toEpochMilli()
            val nextOffsetChange = rules.nextTransition(instant)?.instant?.toEpochMilli() ?: Long.MAX_VALUE
            val sliceEnd = minOf(span.endMillis, nextHour, nextOffsetChange)
            totals[zoned.hour] += sliceEnd - cursor
            cursor = sliceEnd
        }
    }
    return totals
}

/**
 * The part of the day with the most listening per hour: early bird 05–11, daytime 11–17,
 * evening 17–22 and night owl 22–05. Windows differ in length, so they are compared by their
 * hourly average rather than their total. Ties go to the earlier window.
 */
internal fun classifyPersonality(hourly: LongArray): ListeningPersonality {
    require(hourly.size == 24) { "Expected 24 hourly totals, got ${hourly.size}" }
    fun average(hours: List<Int>): Double = hours.sumOf { hourly[it] }.toDouble() / hours.size
    val windows = listOf(
        ListeningPersonality.EARLY_BIRD to (5..10).toList(),
        ListeningPersonality.DAYTIME to (11..16).toList(),
        ListeningPersonality.EVENING to (17..21).toList(),
        ListeningPersonality.NIGHT_OWL to listOf(22, 23, 0, 1, 2, 3, 4)
    )
    var best = windows.first()
    var bestAverage = average(best.second)
    windows.drop(1).forEach { window ->
        val windowAverage = average(window.second)
        if (windowAverage > bestAverage) {
            best = window
            bestAverage = windowAverage
        }
    }
    return best.first
}

/**
 * The release decade with the most listening, from listened time per release year. Untagged
 * years (0 or implausible) are ignored, so the result is null when too little listening is
 * tagged to say anything, or when no decade stands out among the tagged listening.
 */
internal fun dominantDecade(listenedMsByReleaseYear: Map<Int, Long>, totalMs: Long): DecadeShare? {
    if (totalMs <= 0L) return null
    val byDecade = listenedMsByReleaseYear
        .filterKeys { it in PlausibleReleaseYears }
        .entries
        .groupBy({ (it.key / 10) * 10 }, { it.value })
        .mapValues { (_, durations) -> durations.sum() }
    val taggedMs = byDecade.values.sum()
    if (taggedMs <= 0L || taggedMs < totalMs * MIN_TAGGED_SHARE) return null
    // Most listening first; ties go to the more recent decade.
    val (decade, decadeMs) = byDecade.entries
        .maxWithOrNull(compareBy<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
        ?.toPair()
        ?: return null
    if (decadeMs < taggedMs * MIN_DECADE_SHARE_OF_TAGGED) return null
    return DecadeShare(decade = decade, share = (decadeMs.toDouble() / totalMs).toFloat().coerceIn(0f, 1f))
}

/** Index of the largest value (the first one on ties), or null when nothing is above zero. */
internal fun peakIndex(values: List<Long>): Int? {
    val peak = values.maxOrNull()?.takeIf { it > 0L } ?: return null
    return values.indexOf(peak)
}
