package com.lostf1sh.pixelplayeross.presentation.recap

import androidx.compose.runtime.Immutable
import com.lostf1sh.pixelplayeross.data.stats.RecapYearTotal
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

/** A recap the app offers to open. */
@Immutable
data class RecapEntry(
    val year: Int,
    /** True for a recap of the running year before December, which reads "so far". */
    val isSoFar: Boolean
)

/** When recaps are offered, and for which years. Pure, so it is driven by explicit dates. */
internal object RecapSchedule {

    /** Less listening than this makes too thin a story to offer. */
    val MIN_RECAP_LISTENING_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /** The seasonal Home card stays up to and including this day of January. */
    private const val HOME_CARD_LAST_JANUARY_DAY = 15

    /**
     * The year the seasonal Home card offers on [today]: the running year through December,
     * then the year that just ended for the first half of January. Null the rest of the year.
     */
    fun homeCardYear(today: LocalDate): Int? = when {
        today.month == Month.DECEMBER -> today.year
        today.month == Month.JANUARY && today.dayOfMonth <= HOME_CARD_LAST_JANUARY_DAY -> today.year - 1
        else -> null
    }

    /**
     * Whether a recap of [year] made on [today] only covers part of it. December already
     * counts as the whole year, so the seasonal recap is not labelled "so far".
     */
    fun isSoFar(year: Int, today: LocalDate): Boolean =
        year == today.year && today.month != Month.DECEMBER

    /** Whether [year] has enough listening in [totals] for a recap. */
    fun hasEnoughListening(year: Int, totals: List<RecapYearTotal>): Boolean =
        (totals.firstOrNull { it.year == year }?.listenedMs ?: 0L) >= MIN_RECAP_LISTENING_MS

    /**
     * Recaps the Stats screen offers on [today], most relevant first. Only the running and the
     * previous year qualify: history is kept for two years, so anything older is partial.
     * The year that just ended leads until December, when the running year takes over.
     */
    fun statsEntries(today: LocalDate, totals: List<RecapYearTotal>): List<RecapEntry> {
        val order = if (today.month == Month.DECEMBER) {
            listOf(today.year, today.year - 1)
        } else {
            listOf(today.year - 1, today.year)
        }
        return order
            .filter { hasEnoughListening(it, totals) }
            .map { RecapEntry(year = it, isSoFar = isSoFar(it, today)) }
    }
}
