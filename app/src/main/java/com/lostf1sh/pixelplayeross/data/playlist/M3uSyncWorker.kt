package com.lostf1sh.pixelplayeross.data.playlist

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylist
import com.lostf1sh.pixelplayeross.data.preferences.M3uSyncPreferences
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.di.AppScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltWorker
class M3uSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: M3uSyncRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try {
        repository.syncNow()
        Result.success()
    } catch (error: IOException) {
        Timber.w(error, "M3U sync hit a temporary storage error")
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    } catch (error: SecurityException) {
        Timber.w(error, "M3U sync folder permission was revoked")
        Result.failure()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

/** Schedules periodic, foreground and playlist-change reconciliation without leaking UI state. */
@Singleton
@OptIn(FlowPreview::class)
class M3uSyncCoordinator @Inject constructor(
    private val workManager: WorkManager,
    private val preferences: M3uSyncPreferences,
    private val playlistRepository: PlaylistPreferencesRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    @Volatile
    private var enabled = false

    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            preferences.configFlow
                .map { it.treeUri != null }
                .distinctUntilChanged()
                .collect { isEnabled ->
                    enabled = isEnabled
                    if (isEnabled) {
                        schedulePeriodic()
                        requestNow()
                    } else {
                        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                    }
                }
        }

        scope.launch {
            combine(
                preferences.configFlow.map { it.treeUri != null },
                playlistRepository.userPlaylistsFlow.map { playlists ->
                    playlists
                        .filter {
                            !it.isQueueGenerated && !it.isSmartPlaylist && it.source == "LOCAL"
                        }
                        .map { playlist -> playlist.id to playlist.lastModified }
                },
            ) { isEnabled, revision -> isEnabled to revision }
                .debounce(PLAYLIST_CHANGE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (isEnabled, _) ->
                    if (isEnabled) requestNow()
                }
        }
    }

    fun onAppForeground() {
        if (enabled) requestNow()
    }

    fun requestNow() {
        if (!enabled) return
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            // A request that arrives while a worker is running must become a durable follow-up.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<M3uSyncWorker>()
                .setConstraints(storageConstraints())
                .build(),
        )
    }

    private fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<M3uSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(storageConstraints())
                .build(),
        )
    }

    private fun storageConstraints(): Constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()

    private companion object {
        const val PERIODIC_WORK_NAME = "automatic_m3u_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "automatic_m3u_sync_immediate"
        const val PLAYLIST_CHANGE_DEBOUNCE_MS = 1_500L
    }
}
