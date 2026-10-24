package com.lostf1sh.pixelplayeross.presentation.model

import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Parcelize
data class RecentlyPlayedSongUiModel(
    val song: Song,
    val lastPlayedTimestamp: Long
) : Parcelable

fun mapRecentlyPlayedSongs(
    playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>,
    songs: List<Song>,
    range: StatsTimeRange? = null,
    nowMillis: Long = System.currentTimeMillis(),
    maxItems: Int = Int.MAX_VALUE
): List<RecentlyPlayedSongUiModel> {
    if (maxItems <= 0 || playbackHistory.isEmpty() || songs.isEmpty()) return emptyList()

    val songById = songs.associateBy { it.id }
    val recentSongIds = collectRecentlyPlayedSongIds(
        playbackHistory = playbackHistory,
        range = range,
        nowMillis = nowMillis,
        maxItems = maxItems
    )

    if (recentSongIds.isEmpty()) return emptyList()

    val recentSongIdSet = recentSongIds.toHashSet()
    val (startBound, endBound) = range.resolveBounds(
        nowMillis = nowMillis.coerceAtLeast(0L),
        zoneId = ZoneId.systemDefault()
    )

    val seenSongIds = HashSet<String>()
    val deduped = ArrayList<RecentlyPlayedSongUiModel>(maxItems.coerceAtMost(playbackHistory.size))

    val sortedHistory = playbackHistory.sortedWith(
        compareByDescending<PlaybackStatsRepository.PlaybackHistoryEntry> { it.timestamp }
            .thenBy { it.songId }
    )

    for (entry in sortedHistory) {
        if (deduped.size >= maxItems) break
        val safeTimestamp = entry.timestamp.coerceAtLeast(0L)
        if (safeTimestamp > endBound) continue
        if (startBound != null && safeTimestamp < startBound) continue
        if (!seenSongIds.add(entry.songId)) continue
        if (entry.songId !in recentSongIdSet) continue

        val song = songById[entry.songId] ?: continue
        deduped += RecentlyPlayedSongUiModel(
            song = song,
            lastPlayedTimestamp = safeTimestamp
        )
    }

    return deduped
}

/**
 * Drops [songId] from a recently played list, keeping the order of the other songs. Home uses
 * this after a song is deleted, because its pill row is a snapshot that stays frozen while Home
 * is visible.
 */
fun List<RecentlyPlayedSongUiModel>.withoutSong(songId: String): List<RecentlyPlayedSongUiModel> {
    if (none { it.song.id == songId }) return this
    return filterNot { it.song.id == songId }
}

/**
 * Swaps in the newest data for each song that is also in [latest], keeping this list's order,
 * entries and timestamps. Home's snapshot then reflects edits such as a new title without
 * reordering pills while they are on screen. Songs missing from [latest] are kept as they are.
 */
fun List<RecentlyPlayedSongUiModel>.withUpdatedSongs(
    latest: List<RecentlyPlayedSongUiModel>
): List<RecentlyPlayedSongUiModel> {
    if (isEmpty() || latest.isEmpty()) return this
    val latestSongById = latest.associate { it.song.id to it.song }
    var changed = false
    val updated = map { item ->
        val latestSong = latestSongById[item.song.id]
        if (latestSong == null || latestSong == item.song) {
            item
        } else {
            changed = true
            item.copy(song = latestSong)
        }
    }
    return if (changed) updated else this
}

fun collectRecentlyPlayedSongIds(
    playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>,
    range: StatsTimeRange? = null,
    nowMillis: Long = System.currentTimeMillis(),
    maxItems: Int = Int.MAX_VALUE
): List<String> {
    if (maxItems <= 0 || playbackHistory.isEmpty()) return emptyList()

    val (startBound, endBound) = range.resolveBounds(
        nowMillis = nowMillis.coerceAtLeast(0L),
        zoneId = ZoneId.systemDefault()
    )

    val seenSongIds = LinkedHashSet<String>()
    val sortedHistory = playbackHistory.sortedWith(
        compareByDescending<PlaybackStatsRepository.PlaybackHistoryEntry> { it.timestamp }
            .thenBy { it.songId }
    )

    for (entry in sortedHistory) {
        if (seenSongIds.size >= maxItems) break
        val safeTimestamp = entry.timestamp.coerceAtLeast(0L)
        if (safeTimestamp > endBound) continue
        if (startBound != null && safeTimestamp < startBound) continue
        seenSongIds.add(entry.songId)
    }

    return seenSongIds.toList()
}

private fun StatsTimeRange?.resolveBounds(
    nowMillis: Long,
    zoneId: ZoneId
): Pair<Long?, Long> {
    val safeNow = nowMillis.coerceAtLeast(0L)
    val zonedNow = java.time.Instant.ofEpochMilli(safeNow).atZone(zoneId)

    return when (this) {
        StatsTimeRange.DAY -> {
            val start = zonedNow.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.WEEK -> {
            val startOfWeek = zonedNow.toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val start = startOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.MONTH -> {
            val startOfMonth = zonedNow.toLocalDate().withDayOfMonth(1)
            val start = startOfMonth.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.YEAR -> {
            val startOfYear = zonedNow.toLocalDate().withDayOfYear(1)
            val start = startOfYear.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.ALL, null -> {
            null to safeNow
        }
    }
}
