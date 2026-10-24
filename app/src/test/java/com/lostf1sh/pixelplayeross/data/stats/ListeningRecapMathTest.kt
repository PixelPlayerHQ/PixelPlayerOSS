package com.lostf1sh.pixelplayeross.data.stats

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackSpan
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively

class ListeningRecapMathTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun offsetMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, offset: ZoneOffset): Long =
        OffsetDateTime.of(year, month, day, hour, minute, 0, 0, offset).toInstant().toEpochMilli()

    @Test
    fun `recapYearBounds covers the whole year once it is over`() {
        val now = millis(LocalDateTime.of(2026, 3, 1, 12, 0))

        val bounds = recapYearBounds(2025, zone, now)!!

        assertThat(bounds.startMillis).isEqualTo(millis(LocalDateTime.of(2025, 1, 1, 0, 0)))
        assertThat(bounds.endMillis).isEqualTo(millis(LocalDateTime.of(2026, 1, 1, 0, 0)) - 1)
        assertThat(bounds.isYearComplete).isTrue()
        assertThat(bounds.daysInRange).isEqualTo(365)
    }

    @Test
    fun `recapYearBounds ends at now while the year is running`() {
        val now = millis(LocalDateTime.of(2026, 10, 23, 9, 30))

        val bounds = recapYearBounds(2026, zone, now)!!

        assertThat(bounds.endMillis).isEqualTo(now)
        assertThat(bounds.isYearComplete).isFalse()
        assertThat(bounds.daysInRange).isEqualTo(LocalDate.of(2026, 10, 23).dayOfYear)
    }

    @Test
    fun `recapYearBounds is null for a year that has not started or is out of range`() {
        val now = millis(LocalDateTime.of(2026, 10, 23, 9, 30))

        assertThat(recapYearBounds(2027, zone, now)).isNull()
        assertThat(recapYearBounds(0, zone, now)).isNull()
        assertThat(recapYearBounds(Int.MAX_VALUE, zone, now)).isNull()
    }

    @Test
    fun `hourlyDurations splits a span across hour boundaries`() {
        val start = millis(LocalDateTime.of(2025, 6, 1, 22, 40))
        val end = millis(LocalDateTime.of(2025, 6, 2, 0, 10))

        val hourly = hourlyDurations(listOf(PlaybackSpan(start, end)), zone)

        assertThat(hourly[22]).isEqualTo(TimeUnit.MINUTES.toMillis(20))
        assertThat(hourly[23]).isEqualTo(TimeUnit.MINUTES.toMillis(60))
        assertThat(hourly[0]).isEqualTo(TimeUnit.MINUTES.toMillis(10))
        assertThat(hourly.sum()).isEqualTo(end - start)
    }

    @Test
    fun `hourlyDurations counts both repeated hours when daylight saving time ends`() {
        val newYork = ZoneId.of("America/New_York")
        // 2025-11-02: 02:00 EDT falls back to 01:00 EST, so 01:00-02:00 happens twice.
        val start = offsetMillis(2025, 11, 2, 0, 30, ZoneOffset.ofHours(-4))
        val end = offsetMillis(2025, 11, 2, 2, 30, ZoneOffset.ofHours(-5))

        val hourly = hourlyDurations(listOf(PlaybackSpan(start, end)), newYork)

        assertThat(hourly[0]).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(hourly[1]).isEqualTo(TimeUnit.MINUTES.toMillis(120))
        assertThat(hourly[2]).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(hourly.sum()).isEqualTo(end - start)
    }

    @Test
    fun `hourlyDurations moves past a half-hour fall back without looping`() {
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        // 2025-04-06: 02:00 at +11:00 falls back to 01:30 at +10:30.
        val start = offsetMillis(2025, 4, 6, 1, 20, ZoneOffset.ofHours(11))
        val end = offsetMillis(2025, 4, 6, 2, 20, ZoneOffset.ofHoursMinutes(10, 30))

        val hourly = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            hourlyDurations(listOf(PlaybackSpan(start, end)), lordHowe)
        }

        // 01:20-02:00 at +11:00, then 01:30-02:00 again at +10:30.
        assertThat(hourly[1]).isEqualTo(TimeUnit.MINUTES.toMillis(70))
        assertThat(hourly[2]).isEqualTo(TimeUnit.MINUTES.toMillis(20))
        assertThat(hourly.sum()).isEqualTo(end - start)
    }

    @Test
    fun `hourlyDurations splits at a half-hour spring forward`() {
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        // 2025-10-05: 02:00 at +10:30 jumps to 02:30 at +11:00.
        val start = offsetMillis(2025, 10, 5, 1, 40, ZoneOffset.ofHoursMinutes(10, 30))
        val end = offsetMillis(2025, 10, 5, 3, 10, ZoneOffset.ofHours(11))

        val hourly = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            hourlyDurations(listOf(PlaybackSpan(start, end)), lordHowe)
        }

        assertThat(hourly[1]).isEqualTo(TimeUnit.MINUTES.toMillis(20))
        assertThat(hourly[2]).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(hourly[3]).isEqualTo(TimeUnit.MINUTES.toMillis(10))
        assertThat(hourly.sum()).isEqualTo(end - start)
    }

    @Test
    fun `classifyPersonality picks night owl for late listening including after midnight`() {
        val hourly = LongArray(24)
        hourly[23] = TimeUnit.MINUTES.toMillis(50)
        hourly[1] = TimeUnit.MINUTES.toMillis(50)
        hourly[2] = TimeUnit.MINUTES.toMillis(50)
        hourly[14] = TimeUnit.MINUTES.toMillis(60)

        assertThat(classifyPersonality(hourly)).isEqualTo(ListeningPersonality.NIGHT_OWL)
    }

    @Test
    fun `classifyPersonality compares hourly averages so longer windows are not favoured`() {
        val hourly = LongArray(24)
        // Evening: 5 hours of 30 minutes. Night: the same total spread over its 7 hours.
        (17..21).forEach { hourly[it] = TimeUnit.MINUTES.toMillis(30) }
        val nightShare = TimeUnit.MINUTES.toMillis(150) / 7
        listOf(22, 23, 0, 1, 2, 3, 4).forEach { hourly[it] = nightShare }

        assertThat(classifyPersonality(hourly)).isEqualTo(ListeningPersonality.EVENING)
    }

    @Test
    fun `classifyPersonality picks early bird for morning listening and breaks ties early`() {
        val morning = LongArray(24).also { it[7] = TimeUnit.MINUTES.toMillis(45) }
        assertThat(classifyPersonality(morning)).isEqualTo(ListeningPersonality.EARLY_BIRD)

        val tie = LongArray(24).also {
            it[8] = TimeUnit.MINUTES.toMillis(30)
            it[13] = TimeUnit.MINUTES.toMillis(30)
        }
        assertThat(classifyPersonality(tie)).isEqualTo(ListeningPersonality.EARLY_BIRD)
    }

    @Test
    fun `dominantDecade ignores untagged years and requires enough tagged listening`() {
        val hour = TimeUnit.HOURS.toMillis(1)

        val decade = dominantDecade(mapOf(1994 to 3 * hour, 1998 to 2 * hour, 2012 to hour, 0 to 4 * hour), 10 * hour)
        assertThat(decade?.decade).isEqualTo(1990)
        assertThat(decade?.share).isWithin(0.001f).of(0.5f)

        // Only 20% of the listening carries a release year: too little to say anything.
        assertThat(dominantDecade(mapOf(1994 to 2 * hour, 0 to 8 * hour), 10 * hour)).isNull()
        // Implausible years count as untagged.
        assertThat(dominantDecade(mapOf(3021 to 9 * hour, 1994 to hour), 10 * hour)).isNull()
    }

    @Test
    fun `dominantDecade is null when no decade stands out`() {
        val hour = TimeUnit.HOURS.toMillis(1)
        val evenlySpread = (1950..2020 step 10).associateWith { hour }

        assertThat(dominantDecade(evenlySpread, 8 * hour)).isNull()
    }

    @Test
    fun `peakIndex is the first largest value and null when all are zero`() {
        assertThat(peakIndex(listOf(0L, 5L, 9L, 9L, 1L))).isEqualTo(2)
        assertThat(peakIndex(listOf(0L, 0L, 0L))).isNull()
        assertThat(peakIndex(emptyList())).isNull()
    }
}
