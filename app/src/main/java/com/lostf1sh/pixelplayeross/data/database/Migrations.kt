package com.lostf1sh.pixelplayeross.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Returns true if [table] already has a column named [column].
 *
 * Platform Auto Backup can restore a database that reports the right schema version but whose
 * columns have drifted, so migrations guard each `ALTER` with this check instead of assuming a
 * bare `ADD COLUMN` is safe (see the migration notes in CLAUDE.md / CONTRIBUTING).
 */
private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, ddl: String) {
    if (!hasColumn(table, column)) {
        execSQL("ALTER TABLE `$table` ADD COLUMN $ddl")
    }
}

/**
 * v1 -> v2: album-artist support for the unified library (issue #8).
 *
 * - `songs.album_artist_id`: id of the *effective* album artist (the song's `album_artist` when
 *   present, otherwise its primary track artist). Source-independent, so the "Group by Album
 *   Artist" Artists tab can collapse on it at runtime without forcing a re-sync.
 * - `navidrome_songs.album_artist` / `jellyfin_songs.album_artist`: carry the server's
 *   album-artist tag through the cloud cache so the unified projection can populate the above.
 *
 * Additive and idempotent — each column is added only when missing. `album_artist_id` is then
 * backfilled to the existing primary artist so the collapsed Artists tab is populated before the
 * next library sync recomputes precise values (e.g. collapsing compilations under one artist).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("songs", "album_artist_id", "`album_artist_id` INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("navidrome_songs", "album_artist", "`album_artist` TEXT")
        db.addColumnIfMissing("jellyfin_songs", "album_artist", "`album_artist` TEXT")

        db.execSQL("UPDATE songs SET album_artist_id = artist_id WHERE album_artist_id = 0")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_album_artist_id` ON `songs` (`album_artist_id`)")
    }
}

/**
 * v2 -> v3: opt-in ListenBrainz scrobbling + MusicBrainz identifier storage.
 *
 * - `listenbrainz_pending_listens`: offline scrobble queue. Rows snapshot track metadata at
 *   enqueue time and are deleted on successful submission or permanent rejection.
 * - `songs.mb_recording_id` / `mb_release_id` / `mb_artist_id`: MusicBrainz identifiers, read
 *   from embedded file tags or applied via tag lookup. Scrobbles carry the recording MBID when
 *   known for better server-side matching.
 *
 * Additive and idempotent, per the Auto Backup drift guard above.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `listenbrainz_pending_listens` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `listened_at_ms` INTEGER NOT NULL,
                    `track_name` TEXT NOT NULL,
                    `artist_name` TEXT NOT NULL,
                    `release_name` TEXT,
                    `duration_ms` INTEGER,
                    `recording_mbid` TEXT,
                    `source` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL DEFAULT 0,
                    `created_at_ms` INTEGER NOT NULL
                )
            """.trimIndent()
        )

        db.addColumnIfMissing("songs", "mb_recording_id", "`mb_recording_id` TEXT")
        db.addColumnIfMissing("songs", "mb_release_id", "`mb_release_id` TEXT")
        db.addColumnIfMissing("songs", "mb_artist_id", "`mb_artist_id` TEXT")
    }
}

/** v3 -> v4: metadata and reference counts for user-selected offline collections. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `cached_collections` (
                    `collection_id` TEXT NOT NULL,
                    `collection_type` TEXT NOT NULL,
                    `source_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `subtitle` TEXT,
                    `artwork_uri` TEXT,
                    `created_at_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`collection_id`)
                )
            """.trimIndent()
        )
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `cached_tracks` (
                    `track_id` TEXT NOT NULL,
                    `cache_key` TEXT,
                    `song_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `artist_id` INTEGER NOT NULL,
                    `album` TEXT NOT NULL,
                    `album_id` INTEGER NOT NULL,
                    `album_artist` TEXT,
                    `path` TEXT NOT NULL,
                    `content_uri` TEXT NOT NULL,
                    `artwork_uri` TEXT,
                    `duration_ms` INTEGER NOT NULL,
                    `genre` TEXT,
                    `track_number` INTEGER NOT NULL,
                    `disc_number` INTEGER,
                    `year` INTEGER NOT NULL,
                    `date_added` INTEGER NOT NULL,
                    `date_modified` INTEGER NOT NULL,
                    `mime_type` TEXT,
                    `bitrate` INTEGER,
                    `sample_rate` INTEGER,
                    `navidrome_id` TEXT,
                    `jellyfin_id` TEXT,
                    `is_remote` INTEGER NOT NULL,
                    `added_at_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`track_id`)
                )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_tracks_cache_key` " +
                "ON `cached_tracks` (`cache_key`)"
        )
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `cached_collection_tracks` (
                    `collection_id` TEXT NOT NULL,
                    `track_id` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    PRIMARY KEY(`collection_id`, `track_id`),
                    FOREIGN KEY(`collection_id`) REFERENCES `cached_collections`(`collection_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`track_id`) REFERENCES `cached_tracks`(`track_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_collection_tracks_track_id` " +
                "ON `cached_collection_tracks` (`track_id`)"
        )
    }
}
