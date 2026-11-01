package com.lostf1sh.pixelplayeross.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyfinDaoTest {

    private lateinit var db: PixelPlayerDatabase
    private lateinit var dao: JellyfinDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        dao = db.jellyfinDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun distinctSongsHaveOneRowPerSongAndPreferTheLibraryRow() = runTest {
        dao.insertSongs(
            listOf(
                row(playlistId = LIBRARY, jellyfinId = "in-library", title = "Library title", dateAdded = 3L),
                row(playlistId = "mix", jellyfinId = "in-library", title = "Stale playlist title", dateAdded = 3L),
                row(playlistId = "mix", jellyfinId = "playlists-only", title = "Only in playlists", dateAdded = 2L),
                row(playlistId = "road-trip", jellyfinId = "playlists-only", title = "Only in playlists", dateAdded = 2L),
                row(playlistId = LIBRARY, jellyfinId = "oldest", title = "Oldest", dateAdded = 1L)
            )
        )

        val songs = dao.getDistinctJellyfinSongs(LIBRARY)

        assertEquals(listOf("in-library", "playlists-only", "oldest"), songs.map { it.jellyfinId })
        assertEquals("Library title", songs.first().title)
        assertEquals(LIBRARY, songs.first().playlistId)
    }

    private fun row(playlistId: String, jellyfinId: String, title: String, dateAdded: Long) =
        JellyfinSongEntity(
            id = "${playlistId}_$jellyfinId",
            jellyfinId = jellyfinId,
            playlistId = playlistId,
            title = title,
            artist = "Artist",
            artistId = null,
            album = "Album",
            albumId = "album",
            duration = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2020,
            genre = null,
            bitRate = 320,
            mimeType = "audio/flac",
            path = "/music/$jellyfinId.flac",
            dateAdded = dateAdded
        )

    private companion object {
        const val LIBRARY = "__library__"
    }
}
