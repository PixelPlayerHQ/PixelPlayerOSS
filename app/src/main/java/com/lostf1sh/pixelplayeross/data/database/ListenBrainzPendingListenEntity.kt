package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A listen awaiting submission to ListenBrainz. The table is the pending queue: rows are deleted
 * on successful submission or permanent rejection, so no status column exists.
 *
 * Metadata is snapshotted at enqueue time — the song may be edited or deleted before the queue
 * flushes, and ListenBrainz should receive what was actually played.
 */
@Entity(tableName = "listenbrainz_pending_listens")
data class ListenBrainzPendingListenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Epoch millis of when the listen started (ListenBrainz `listened_at` semantics). */
    @ColumnInfo(name = "listened_at_ms") val listenedAtMs: Long,
    @ColumnInfo(name = "track_name") val trackName: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "release_name") val releaseName: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "recording_mbid") val recordingMbid: String? = null,
    /** One of [ListenBrainzSource] — which per-source toggle admitted this listen. */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "attempts", defaultValue = "0") val attempts: Int = 0,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

/** Playback source labels stored in [ListenBrainzPendingListenEntity.source]. */
object ListenBrainzSource {
    const val LOCAL = "LOCAL"
    const val NAVIDROME = "NAVIDROME"
    const val JELLYFIN = "JELLYFIN"

    fun fromSourceType(sourceType: Int): String = when (sourceType) {
        SourceType.NAVIDROME -> NAVIDROME
        SourceType.JELLYFIN -> JELLYFIN
        else -> LOCAL
    }
}
