package com.lostf1sh.pixelplayeross.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = PixelPlayerDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrationFromFiveToSixPreservesRowsAndAllowsRepeatedSongs() {
        migrationHelper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL(
                "INSERT INTO playlist_songs (playlist_id, song_id, sort_order) VALUES " +
                    "('playlist-1', 'intro', 0), " +
                    "('playlist-1', 'chorus', 1), " +
                    "('playlist-1', 'outro', 2)"
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            name = DATABASE_NAME,
            version = 6,
            validateDroppedTables = true,
            MIGRATION_5_6,
        ).use { database ->
            database.execSQL(
                "INSERT INTO playlist_songs (playlist_id, song_id, sort_order) " +
                    "VALUES ('playlist-1', 'chorus', 3)"
            )

            assertThat(database.playlistSongIds("playlist-1"))
                .containsExactly("intro", "chorus", "outro", "chorus")
                .inOrder()
        }
    }

    private fun SupportSQLiteDatabase.playlistSongIds(playlistId: String): List<String> {
        return query(
            "SELECT song_id FROM playlist_songs WHERE playlist_id = ? ORDER BY sort_order",
            arrayOf(playlistId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "playlist-migration-test"
    }
}
