package com.lostf1sh.pixelplayeross.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelPlayerDatabaseMigrationTest {

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME_23_TO_24_DRIFTED)
        context.deleteDatabase(DB_NAME_44_TO_45_NAVIDROME_DISC_NUMBER)
    }

    @Test
    fun migration23To24RepairsSongsWithoutDateAddedBeforeCreatingIndexes() {
        val openHelper = createDriftedVersion23Database(DB_NAME_23_TO_24_DRIFTED)
        val db = openHelper.writableDatabase

        try {
            PixelPlayerDatabase.MIGRATION_23_24.migrate(db)

            val columns = db.tableColumns("songs")
            assertTrue("date_added" in columns)

            db.query("SELECT date_added FROM songs WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }

            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_songs_date_added'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("index_songs_date_added", cursor.getString(0))
            }
        } finally {
            db.close()
            openHelper.close()
        }
    }

    @Test
    fun migration44To45MakesNavidromeDiscNumberNonNull() {
        val openHelper = createVersion44DatabaseWithNullableNavidromeDiscNumber(
            DB_NAME_44_TO_45_NAVIDROME_DISC_NUMBER
        )
        val db = openHelper.writableDatabase

        try {
            PixelPlayerDatabase.MIGRATION_44_45.migrate(db)

            assertTrue(db.isColumnNotNull("navidrome_songs", "disc_number"))
            db.query("SELECT disc_number FROM navidrome_songs WHERE id = 'song-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        } finally {
            db.close()
            openHelper.close()
        }
    }

    private fun createDriftedVersion23Database(
        databaseName: String
    ): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        val callback = object : SupportSQLiteOpenHelper.Callback(23) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS songs (
                            id INTEGER NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            artist_name TEXT NOT NULL,
                            artist_id INTEGER NOT NULL,
                            album_artist TEXT,
                            album_name TEXT NOT NULL,
                            album_id INTEGER NOT NULL,
                            content_uri_string TEXT NOT NULL,
                            album_art_uri_string TEXT,
                            duration INTEGER NOT NULL,
                            genre TEXT,
                            file_path TEXT NOT NULL,
                            parent_directory_path TEXT NOT NULL,
                            is_favorite INTEGER NOT NULL DEFAULT 0,
                            lyrics TEXT DEFAULT null,
                            track_number INTEGER NOT NULL DEFAULT 0,
                            year INTEGER NOT NULL DEFAULT 0,
                            mime_type TEXT,
                            bitrate INTEGER,
                            sample_rate INTEGER
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        INSERT INTO songs (
                            id,
                            title,
                            artist_name,
                            artist_id,
                            album_artist,
                            album_name,
                            album_id,
                            content_uri_string,
                            album_art_uri_string,
                            duration,
                            genre,
                            file_path,
                            parent_directory_path,
                            is_favorite,
                            lyrics,
                            track_number,
                            year,
                            mime_type,
                            bitrate,
                            sample_rate
                        ) VALUES (
                            1,
                            'Song',
                            'Artist',
                            10,
                            NULL,
                            'Album',
                            20,
                            'content://song/1',
                            NULL,
                            180000,
                            NULL,
                            '/music/song.mp3',
                            '/music',
                            0,
                            NULL,
                            1,
                            2024,
                            'audio/mpeg',
                            320000,
                            44100
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS favorites (
                            songId INTEGER NOT NULL PRIMARY KEY,
                            isFavorite INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS song_engagements (
                            song_id TEXT NOT NULL PRIMARY KEY,
                            play_count INTEGER NOT NULL DEFAULT 0,
                            total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                            last_played_timestamp INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
    }

    private fun createVersion44DatabaseWithNullableNavidromeDiscNumber(
        databaseName: String
    ): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        val callback = object : SupportSQLiteOpenHelper.Callback(44) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                        CREATE TABLE navidrome_songs (
                            id TEXT NOT NULL PRIMARY KEY,
                            navidrome_id TEXT NOT NULL,
                            playlist_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            artist TEXT NOT NULL,
                            artist_id TEXT,
                            album TEXT NOT NULL,
                            album_id TEXT,
                            cover_art_id TEXT,
                            duration INTEGER NOT NULL,
                            track_number INTEGER NOT NULL,
                            disc_number INTEGER,
                            year INTEGER NOT NULL,
                            genre TEXT,
                            bitRate INTEGER,
                            mime_type TEXT,
                            suffix TEXT,
                            path TEXT NOT NULL,
                            date_added INTEGER NOT NULL
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        INSERT INTO navidrome_songs (
                            id,
                            navidrome_id,
                            playlist_id,
                            title,
                            artist,
                            album,
                            duration,
                            track_number,
                            disc_number,
                            year,
                            path,
                            date_added
                        ) VALUES (
                            'song-1',
                            'navidrome-1',
                            'playlist-1',
                            'Song',
                            'Artist',
                            'Album',
                            180000,
                            1,
                            NULL,
                            2024,
                            '/music/song.mp3',
                            123456
                        )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
    }

    private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    private fun SupportSQLiteDatabase.isColumnNotNull(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val notNullIndex = cursor.getColumnIndex("notnull")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return cursor.getInt(notNullIndex) == 1
                }
            }
        }
        return false
    }

    companion object {
        private const val DB_NAME_23_TO_24_DRIFTED = "migration-test-23-to-24-drifted"
        private const val DB_NAME_44_TO_45_NAVIDROME_DISC_NUMBER =
            "migration-test-44-to-45-navidrome-disc-number"
    }
}
