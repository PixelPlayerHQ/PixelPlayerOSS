package com.lostf1sh.pixelplayeross.data.navidrome.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents a song from a Navidrome/Subsonic server.
 *
 * Based on the Subsonic API "Child" entity.
 *
 * @property id The unique identifier of the song on the server
 * @property title The song title
 * @property artist The artist name
 * @property artistId The artist ID on the server (optional)
 * @property album The album name
 * @property albumId The album ID on the server (optional)
 * @property coverArt The cover art ID (used to construct cover art URL)
 * @property duration The duration in milliseconds
 * @property trackNumber The track number (optional)
 * @property discNumber The disc number (optional)
 * @property year The release year (optional)
 * @property genre The genre (optional)
 * @property bitRate The bitrate in kbps (optional)
 * @property contentType The MIME type (e.g., "audio/mpeg")
 * @property suffix The file suffix (e.g., "mp3", "flac")
 * @property path The file path on the server
 * @property size The file size in bytes (optional)
 * @property playCount The play count (optional)
 * @property created When the song was added to the server library, in epoch milliseconds (0 if unknown)
 */
@Immutable
@Parcelize
data class NavidromeSong(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val albumArtist: String? = null,
    val album: String,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val bitRate: Int? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val path: String = "",
    val size: Long? = null,
    val playCount: Int = 0,
    val created: Long = 0L
) : Parcelable {
    companion object {
        fun empty() = NavidromeSong(
            id = "",
            title = "",
            artist = "",
            artistId = null,
            albumArtist = null,
            album = "",
            albumId = null,
            coverArt = null,
            duration = 0L,
            trackNumber = 0,
            discNumber = 0,
            year = 0,
            genre = null,
            bitRate = null,
            contentType = null,
            suffix = null,
            path = "",
            size = null,
            playCount = 0,
            created = 0L
        )
    }

    /**
     * The date-added timestamp to store locally: the server's `created` time, or [fallbackMs]
     * when the server did not report one.
     */
    fun dateAddedOr(fallbackMs: Long): Long = created.takeIf { it > 0L } ?: fallbackMs

    /**
     * Returns the MIME type, with fallback based on file suffix.
     */
    val resolvedMimeType: String
        get() = when {
            contentType != null -> contentType
            suffix != null -> when (suffix.lowercase()) {
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "ogg", "oga" -> "audio/ogg"
                "m4a", "mp4", "aac" -> "audio/mp4"
                "wav" -> "audio/wav"
                "wma" -> "audio/x-ms-wma"
                else -> "audio/mpeg"
            }
            else -> "audio/mpeg"
        }
}
