package com.lostf1sh.pixelplayeross.data.musicbrainz

import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MusicBrainzRepository @Inject constructor(
    private val api: MusicBrainzApiService,
    private val musicDao: MusicDao
) {
    suspend fun search(song: Song): List<MusicBrainzMatch> = withContext(Dispatchers.IO) {
        api.searchRecording(
            title = song.title,
            artist = song.displayArtist,
            album = song.album,
            durationMs = song.duration.takeIf { it > 0L }
        )
    }

    suspend fun apply(song: Song, match: MusicBrainzMatch) = withContext(Dispatchers.IO) {
        val songId = song.id.toLongOrNull()
            ?: musicDao.getSongIdByContentUri(song.contentUriString)
            ?: error("Song is not present in the unified library")
        musicDao.applyMusicBrainzMatch(
            songId = songId,
            title = match.title,
            artist = match.artist,
            album = match.album,
            year = match.year,
            recordingId = match.recordingId,
            releaseId = match.releaseId,
            artistId = match.artistId
        )
    }
}
