package com.lostf1sh.pixelplayeross.data.listenbrainz

import android.os.SystemClock
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzDao
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzPendingListenEntity
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzSource
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.preferences.ListenBrainzPreferencesRepository
import com.lostf1sh.pixelplayeross.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges finalized listening sessions to the ListenBrainz submission queue.
 *
 * Fed by [com.lostf1sh.pixelplayeross.presentation.viewmodel.ListeningStatsTracker]: the listen
 * threshold is evaluated synchronously (cheap math, safe inside the tracker's synchronized
 * finalize), everything else — song resolution, toggle check, queue insert — runs on [scope].
 *
 * The ListenBrainz threshold is deliberately independent of the tracker's own stats floor, and
 * of the Subsonic scrobble in MusicService (which fires on raw track-end with no threshold).
 */
@Singleton
class ScrobbleManager @Inject constructor(
    private val musicDao: MusicDao,
    private val listenBrainzDao: ListenBrainzDao,
    private val listenBrainzRepository: ListenBrainzRepository,
    private val listenBrainzPreferences: ListenBrainzPreferencesRepository,
    @AppScope private val scope: CoroutineScope
) {
    private var lastPlayingNowSongId: String? = null
    private var lastPlayingNowRealtimeMs: Long = 0L

    init {
        // Flush listens left queued by a previous process (e.g. killed while offline).
        scope.launch {
            runCatching {
                if (listenBrainzRepository.isAuthorized() && listenBrainzDao.count() > 0) {
                    listenBrainzRepository.scheduleFlush()
                }
            }.onFailure { Timber.e(it, "Failed to schedule startup scrobble flush") }
        }
    }

    fun onSessionFinalized(
        songId: String,
        startedAtEpochMs: Long,
        listenedMs: Long,
        trackDurationMs: Long
    ) {
        if (!listenBrainzRepository.hasToken()) return
        if (!meetsListenThreshold(listenedMs, trackDurationMs)) return
        scope.launch {
            runCatching { enqueueListen(songId, startedAtEpochMs) }
                .onFailure { Timber.e(it, "Failed to enqueue ListenBrainz listen for song=%s", songId) }
        }
    }

    fun onPlayingNow(songId: String) {
        if (!listenBrainzRepository.isAuthorized()) return
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (songId == lastPlayingNowSongId && now - lastPlayingNowRealtimeMs < PLAYING_NOW_DEBOUNCE_MS) {
                return
            }
            lastPlayingNowSongId = songId
            lastPlayingNowRealtimeMs = now
        }
        scope.launch {
            runCatching {
                val song = resolveAdmittedSong(songId) ?: return@launch
                listenBrainzRepository.submitPlayingNow(
                    trackName = song.title,
                    artistName = song.artistName,
                    releaseName = song.albumName.takeIf { it.isNotBlank() },
                    durationMs = song.duration.takeIf { it > 0 },
                    recordingMbid = song.mbRecordingId
                )
            }.onFailure { Timber.d(it, "ListenBrainz playing-now failed for song=%s", songId) }
        }
    }

    private suspend fun enqueueListen(songId: String, startedAtEpochMs: Long) {
        val song = resolveAdmittedSong(songId) ?: return
        val inserted = listenBrainzRepository.enqueueListen(
            ListenBrainzPendingListenEntity(
                listenedAtMs = startedAtEpochMs,
                trackName = song.title,
                artistName = song.artistName,
                releaseName = song.albumName.takeIf { it.isNotBlank() },
                durationMs = song.duration.takeIf { it > 0 },
                recordingMbid = song.mbRecordingId,
                source = ListenBrainzSource.fromSourceType(song.sourceType),
                createdAtMs = System.currentTimeMillis()
            )
        )
        // While the token is known-invalid, listens keep queueing but flushing waits for reconnect.
        if (inserted && listenBrainzRepository.isAuthorized()) {
            listenBrainzRepository.scheduleFlush()
        }
    }

    /**
     * Resolves the song, or null when it no longer exists, lacks the metadata ListenBrainz
     * requires, or its playback source's scrobble toggle is off.
     */
    private suspend fun resolveAdmittedSong(songId: String): SongEntity? {
        val id = songId.toLongOrNull() ?: return null
        val song = musicDao.getSongByIdOnce(id) ?: return null
        if (song.title.isBlank() || song.artistName.isBlank()) return null
        val source = ListenBrainzSource.fromSourceType(song.sourceType)
        if (!listenBrainzPreferences.isSourceEnabled(source)) return null
        return song
    }

    companion object {
        private const val PLAYING_NOW_DEBOUNCE_MS = 30_000L
        private val FOUR_MINUTES_MS = TimeUnit.MINUTES.toMillis(4)

        /**
         * ListenBrainz listen rule: 4 minutes or half the track, whichever is lower;
         * plain 4-minute rule when the duration is unknown.
         */
        fun meetsListenThreshold(listenedMs: Long, trackDurationMs: Long): Boolean {
            val required = if (trackDurationMs > 0) {
                minOf(FOUR_MINUTES_MS, trackDurationMs / 2)
            } else {
                FOUR_MINUTES_MS
            }
            return listenedMs >= required
        }
    }
}
