package com.lostf1sh.pixelplayeross.data.stream


/**
 * Shared data class for bulk sync operations across cloud music repositories.
 */
data class BulkSyncResult(
    val playlistCount: Int,
    val syncedSongCount: Int,
    val failedPlaylistCount: Int
)

/**
 * Shared utility functions for cloud music repositories.
 */
object CloudMusicUtils {

    /** Split a raw artist string like "A, B & C" into individual names. */
    fun parseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+、]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    /**
     * Servers report a track without a disc tag as disc 0. Album screens treat a missing disc as
     * disc 1, so 0 is stored as null to keep those tracks in the first disc group.
     */
    fun normalizeDiscNumber(rawDiscNumber: Int): Int? = rawDiscNumber.takeIf { it > 0 }
}
