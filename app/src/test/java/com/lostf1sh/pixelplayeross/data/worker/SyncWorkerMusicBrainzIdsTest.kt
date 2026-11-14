package com.lostf1sh.pixelplayeross.data.worker

import com.lostf1sh.pixelplayeross.data.database.SongEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncWorkerMusicBrainzIdsTest {

    private fun song(recording: String?, release: String?, artist: String?) = SongEntity(
        id = 1L,
        title = "Title",
        artistName = "Artist",
        artistId = 1L,
        albumName = "Album",
        albumId = 1L,
        contentUriString = "content://media/external/audio/media/1",
        albumArtUriString = null,
        duration = 1_000L,
        genre = null,
        filePath = "/music/a.opus",
        parentDirectoryPath = "/music",
        mbRecordingId = recording,
        mbReleaseId = release,
        mbArtistId = artist
    )

    @Test
    fun `rescan without embedded ids keeps the stored ids`() {
        val stored = song("rec", "rel", "art")

        val merged = song(null, null, null).withMusicBrainzIdsFallback(stored)

        assertEquals(Triple("rec", "rel", "art"), Triple(merged.mbRecordingId, merged.mbReleaseId, merged.mbArtistId))
    }

    @Test
    fun `embedded ids replace stored ids as a group`() {
        val stored = song("old-rec", "old-rel", "old-art")

        val merged = song("new-rec", null, null).withMusicBrainzIdsFallback(stored)

        assertEquals(Triple("new-rec", null, null), Triple(merged.mbRecordingId, merged.mbReleaseId, merged.mbArtistId))
    }
}
