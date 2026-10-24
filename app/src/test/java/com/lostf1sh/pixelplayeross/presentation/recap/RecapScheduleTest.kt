package com.lostf1sh.pixelplayeross.presentation.recap

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.stats.RecapYearTotal
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

class RecapScheduleTest {

    private val enough = TimeUnit.HOURS.toMillis(5)
    private val tooLittle = TimeUnit.MINUTES.toMillis(29)

    @Test
    fun `home card runs from december first to january fifteenth`() {
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2026, 11, 30))).isNull()
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2026, 12, 1))).isEqualTo(2026)
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2026, 12, 31))).isEqualTo(2026)
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2027, 1, 1))).isEqualTo(2026)
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2027, 1, 15))).isEqualTo(2026)
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2027, 1, 16))).isNull()
        assertThat(RecapSchedule.homeCardYear(LocalDate.of(2026, 10, 23))).isNull()
    }

    @Test
    fun `running year reads so far until december`() {
        assertThat(RecapSchedule.isSoFar(2026, LocalDate.of(2026, 10, 23))).isTrue()
        assertThat(RecapSchedule.isSoFar(2026, LocalDate.of(2026, 12, 2))).isFalse()
        assertThat(RecapSchedule.isSoFar(2025, LocalDate.of(2026, 10, 23))).isFalse()
    }

    @Test
    fun `stats entries put the year that just ended first until december`() {
        val totals = listOf(RecapYearTotal(2025, enough), RecapYearTotal(2026, enough))

        assertThat(RecapSchedule.statsEntries(LocalDate.of(2026, 10, 23), totals)).containsExactly(
            RecapEntry(year = 2025, isSoFar = false),
            RecapEntry(year = 2026, isSoFar = true)
        ).inOrder()
        assertThat(RecapSchedule.statsEntries(LocalDate.of(2026, 12, 5), totals)).containsExactly(
            RecapEntry(year = 2026, isSoFar = false),
            RecapEntry(year = 2025, isSoFar = false)
        ).inOrder()
    }

    @Test
    fun `stats entries drop years with too little listening and years before the previous one`() {
        val totals = listOf(
            RecapYearTotal(2023, enough),
            RecapYearTotal(2024, enough),
            RecapYearTotal(2025, tooLittle),
            RecapYearTotal(2026, enough)
        )

        assertThat(RecapSchedule.statsEntries(LocalDate.of(2026, 3, 1), totals)).containsExactly(
            RecapEntry(year = 2026, isSoFar = true)
        )
        assertThat(RecapSchedule.statsEntries(LocalDate.of(2026, 3, 1), emptyList())).isEmpty()
    }

    @Test
    fun `enough listening means at least thirty minutes in that year`() {
        val totals = listOf(RecapYearTotal(2025, RecapSchedule.MIN_RECAP_LISTENING_MS))

        assertThat(RecapSchedule.hasEnoughListening(2025, totals)).isTrue()
        assertThat(RecapSchedule.hasEnoughListening(2026, totals)).isFalse()
    }
}
