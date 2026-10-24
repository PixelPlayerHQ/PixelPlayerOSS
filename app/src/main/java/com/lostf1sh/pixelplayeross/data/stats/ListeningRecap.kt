package com.lostf1sh.pixelplayeross.data.stats

import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One calendar year of listening, built from the same history and aggregation as the Stats
 * screen ([summary] is its year summary) plus the extra numbers the recap story shows.
 */
data class ListeningRecap(
    val year: Int,
    /** False while [year] is still running; the recap then ends at the moment it was built. */
    val isYearComplete: Boolean,
    val summary: PlaybackStatsSummary,
    /** Calendar days from January 1 to the end of the recap, both included. */
    val daysInRange: Int,
    /** Distinct artists played, counting each artist of a multi-artist song. */
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    /** Listened time per month, January first; always 12 entries. */
    val monthlyDurationsMs: List<Long>,
    /** Listened time per local hour of the day, midnight first; always 24 entries. */
    val hourlyDurationsMs: List<Long>,
    val personality: ListeningPersonality,
    /** The weekday with the most listening, or null without any. */
    val favoriteDayOfWeek: DayOfWeek?,
    /** The date with the most listening, or null without any. */
    val biggestDay: LocalDate?,
    val biggestDayDurationMs: Long,
    /** The earliest play of the year whose song is still in the library. */
    val firstSong: RecapSong?,
    /** Start year of the release decade most of the tagged listening came from, e.g. 1990. */
    val topDecade: Int?,
    /** Share of all listening that came from [topDecade], from 0 to 1. */
    val topDecadeShare: Float
)

data class RecapSong(
    val songId: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?
)

/** When in the day someone mostly listens; see [classifyPersonality]. */
enum class ListeningPersonality {
    EARLY_BIRD,
    DAYTIME,
    EVENING,
    NIGHT_OWL
}

/** Total listened time in one local calendar year. */
data class RecapYearTotal(
    val year: Int,
    val listenedMs: Long
)
