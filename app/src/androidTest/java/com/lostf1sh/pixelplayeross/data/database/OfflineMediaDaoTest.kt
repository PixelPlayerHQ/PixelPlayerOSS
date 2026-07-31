package com.lostf1sh.pixelplayeross.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineMediaDaoTest {
    private lateinit var database: PixelPlayerDatabase
    private lateinit var dao: OfflineMediaDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        dao = database.offlineMediaDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun removingLargeCollectionDoesNotUseTrackIdsAsBindParameters() = runTest {
        val targetCollection = collection("target")
        val sharedCollection = collection("shared")
        val tracks = List(1_001) { index -> track("track-$index") }
        database.withTransaction {
            dao.upsertCollection(targetCollection)
            dao.upsertCollection(sharedCollection)
            dao.upsertTracks(tracks)
            dao.upsertCrossRefs(
                tracks.mapIndexed { index, track ->
                    CachedCollectionTrackCrossRef(targetCollection.collectionId, track.trackId, index)
                } + CachedCollectionTrackCrossRef(sharedCollection.collectionId, tracks.first().trackId, 0)
            )
        }

        val removedTracks = database.withTransaction {
            val exclusiveTracks = dao.tracksExclusiveToCollection(targetCollection.collectionId)
            dao.deleteTracksExclusiveToCollection(targetCollection.collectionId)
            dao.deleteCollection(targetCollection.collectionId)
            exclusiveTracks
        }

        assertEquals(1_000, removedTracks.size)
        assertEquals(listOf(tracks.first().trackId), dao.observeTracks().first().map(CachedTrackEntity::trackId))
        assertEquals(listOf(sharedCollection.collectionId), dao.observeCollections().first().map(CachedCollectionEntity::collectionId))
    }

    private fun collection(id: String) = CachedCollectionEntity(
        collectionId = id,
        collectionType = "PLAYLIST",
        sourceId = id,
        title = id,
        subtitle = null,
        artworkUri = null,
        createdAtMs = 0L,
    )

    private fun track(id: String) = CachedTrackEntity(
        trackId = id,
        cacheKey = "cache-$id",
        songId = id,
        title = id,
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        albumArtist = null,
        path = "",
        contentUri = "navidrome://$id",
        artworkUri = null,
        durationMs = 60_000L,
        genre = null,
        trackNumber = 1,
        discNumber = null,
        year = 2026,
        dateAdded = 0L,
        dateModified = 0L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100,
        navidromeId = id,
        jellyfinId = null,
        isRemote = true,
        addedAtMs = 0L,
    )
}
