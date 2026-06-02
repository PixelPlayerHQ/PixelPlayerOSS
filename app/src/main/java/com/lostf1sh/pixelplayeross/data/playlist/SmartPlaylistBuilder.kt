package com.lostf1sh.pixelplayeross.data.playlist

import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.model.SmartPlaylistRule
import com.lostf1sh.pixelplayeross.data.model.Song
import java.util.concurrent.TimeUnit

/**
 * Pure, side-effect-free selection logic for rule-based ("smart") playlists.
 *
 * Extracted from `PlaylistViewModel` so the boundary behaviour (the 30-day
 * "forgotten" threshold, the `playCount <= 2` "new gem" cutoff, the tie-break
 * ordering) can be unit-tested without a ViewModel, repository, or Android
 * runtime. The ViewModel is responsible only for fetching the inputs.
 */
object SmartPlaylistBuilder {

    /** Days of inactivity after which a favorite is considered "forgotten". */
    const val FORGOTTEN_FAVORITE_DAYS = 30L

    /** Maximum play count for a song to still qualify as a "new gem". */
    const val NEW_GEM_MAX_PLAY_COUNT = 2

    /**
     * Resolves the ordered list of song IDs for [rule] given the library and engagement data.
     *
     * @param now current epoch millis (injected so tests are deterministic).
     * @param limit requested size; clamped to `1..allSongs.size`.
     */
    fun buildSongIds(
        rule: SmartPlaylistRule,
        allSongs: List<Song>,
        engagements: Map<String, DailyMixManager.SongEngagementStats>,
        favoriteIds: Set<String>,
        now: Long,
        limit: Int,
    ): List<String> {
        if (allSongs.isEmpty()) return emptyList()

        val songById = allSongs.associateBy { it.id }
        val safeLimit = limit.coerceAtLeast(1).coerceAtMost(allSongs.size)

        val pickedSongs = when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> {
                engagements.entries
                    .filter { it.value.playCount > 0 || it.value.totalPlayDurationMs > 0L }
                    .sortedWith(
                        compareByDescending<Map.Entry<String, DailyMixManager.SongEngagementStats>> { it.value.playCount }
                            .thenByDescending { it.value.totalPlayDurationMs }
                            .thenByDescending { it.value.lastPlayedTimestamp }
                    )
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }

            SmartPlaylistRule.RECENTLY_PLAYED -> {
                engagements.entries
                    .filter { it.value.lastPlayedTimestamp > 0L }
                    .sortedByDescending { it.value.lastPlayedTimestamp }
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }

            SmartPlaylistRule.FORGOTTEN_FAVORITES -> {
                val staleThreshold = now - TimeUnit.DAYS.toMillis(FORGOTTEN_FAVORITE_DAYS)
                allSongs
                    .asSequence()
                    .filter { favoriteIds.contains(it.id) }
                    .sortedWith(
                        compareBy<Song> { engagements[it.id]?.lastPlayedTimestamp ?: 0L }
                            .thenBy { it.title.lowercase() }
                    )
                    .filter { song ->
                        (engagements[song.id]?.lastPlayedTimestamp ?: 0L) < staleThreshold
                    }
                    .take(safeLimit)
                    .toList()
            }

            SmartPlaylistRule.NEW_GEMS -> {
                allSongs
                    .asSequence()
                    .sortedWith(
                        compareByDescending<Song> { it.dateAdded }
                            .thenBy { engagements[it.id]?.playCount ?: 0 }
                    )
                    .filter { song -> (engagements[song.id]?.playCount ?: 0) <= NEW_GEM_MAX_PLAY_COUNT }
                    .take(safeLimit)
                    .toList()
            }
        }

        return pickedSongs.map { it.id }.distinct()
    }
}
