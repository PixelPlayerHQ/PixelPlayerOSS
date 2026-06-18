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

        // Seed from the existing primary artist so the collapsed tab is non-empty immediately.
        db.execSQL("UPDATE songs SET album_artist_id = artist_id WHERE album_artist_id = 0")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_album_artist_id` ON `songs` (`album_artist_id`)")
    }
}
