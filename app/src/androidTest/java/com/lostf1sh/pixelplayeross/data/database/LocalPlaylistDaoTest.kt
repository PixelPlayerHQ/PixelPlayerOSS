package com.lostf1sh.pixelplayeross.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlaylistDaoTest {

    private lateinit var database: PixelPlayerDatabase
    private lateinit var playlistDao: LocalPlaylistDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.localPlaylistDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun repeatedTracksRemainAtEachPlaylistPosition() = runTest {
        playlistDao.replacePlaylistSongs(
            playlistId = "playlist-1",
            songIds = listOf("intro", "chorus", "chorus", "outro"),
        )

        val storedIds = playlistDao.observePlaylistSongs("playlist-1")
            .first()
            .map(PlaylistSongEntity::songId)

        assertThat(storedIds)
            .containsExactly("intro", "chorus", "chorus", "outro")
            .inOrder()
    }
}
