package com.lostf1sh.pixelplayeross.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import com.lostf1sh.pixelplayeross.data.observer.MediaStoreObserver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID

/**
 * Data class representing the progress of the sync operation.
 */
data class SyncProgress(
    val isRunning: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val isCompleted: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE
) {
    enum class SyncPhase {
        IDLE,
        FETCHING_MEDIASTORE,
        PROCESSING_FILES,
        SAVING_TO_DATABASE,
        SCANNING_LRC,
        CLEANING_CACHE,
        SYNCING_CLOUD,
        COMPLETING
    }

    val progress: Float
        get() = if (totalCount > 0) currentCount.toFloat() / totalCount else 0f

    val hasProgress: Boolean
        get() = totalCount > 0
    }

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaStoreObserver: MediaStoreObserver
) {
    private val workManager = WorkManager.getInstance(context)
    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaStoreAutoSyncJob: Job? = null
    private val autoSyncLock = Any()
    // In-memory only: lives in this @Singleton for the process lifetime, so no leak.
    @Volatile
    private var lastForegroundSyncTime = 0L
    @Volatile
    private var currentSyncWorkId: UUID? = null

    // Exposes a simple Flow<Boolean>.
    val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
                // Fresh enqueued work is about to start. Retried enqueued work is in
                // backoff and does no work, so it should not keep the UI in syncing state.
                val isFreshlyEnqueued = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0
                }
                isRunning || isFreshlyEnqueued
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    /**
     * Emits once each time the library sync finishes in a FAILED state with no other
     * run still active. Used to surface a one-shot "sync failed" message to the user,
     * which the [syncProgress] flow alone cannot do (it silently reverts to idle).
     */
    val syncFailed: Flow<Unit> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                latestForegroundSyncWork(workInfos)?.state == WorkInfo.State.FAILED
            }
            .distinctUntilChanged()
            .filter { it }
            .map { }
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 0
            )

    init {
        observeStorageChanges()
        observeAppForeground()
        schedulePeriodicMaintenance()
    }

    /**
     * Schedules the once-a-day heavy maintenance (LRC/cache/cloud). Uses a dedicated unique
     * name distinct from [SyncWorker.WORK_NAME], so it never drives the foreground sync
     * indicator. KEEP preserves the existing schedule across launches.
     */
    private fun schedulePeriodicMaintenance() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.periodicMaintenanceWork()
        )
    }

    /**
     * Flow that exposes the detailed sync progress including song count.
     */
    val syncProgress: Flow<SyncProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                val enqueuedWork = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }

                when {
                    runningWork != null -> {
                        val current = runningWork.progress.getInt(SyncWorker.PROGRESS_CURRENT, 0)
                        val total = runningWork.progress.getInt(SyncWorker.PROGRESS_TOTAL, 0)
                        val phaseOrdinal = runningWork.progress.getInt(SyncWorker.PROGRESS_PHASE, 0)
                        val phase = try {
                            SyncProgress.SyncPhase.entries[phaseOrdinal]
                        } catch (e: IndexOutOfBoundsException) {
                            SyncProgress.SyncPhase.IDLE
                        }
                        SyncProgress(
                            isRunning = true,
                            currentCount = current,
                            totalCount = total,
                            isCompleted = false,
                            phase = phase
                        )
                    }
                    succeededWork != null -> {
                        val total = readCompletedSongCount(succeededWork)
                        SyncProgress(
                            isRunning = false,
                            currentCount = total,
                            totalCount = total,
                            isCompleted = true,
                            phase = SyncProgress.SyncPhase.COMPLETING
                        )
                    }
                    enqueuedWork != null -> {
                        if (enqueuedWork.runAttemptCount == 0) {
                            SyncProgress(isRunning = true, isCompleted = false, phase = SyncProgress.SyncPhase.IDLE)
                        } else {
                            SyncProgress()
                        }
                    }
                    else -> SyncProgress()
                }
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    /**
     * Emits `true` while the worker is in the early "library changes" phases —
     * scanning MediaStore for added/removed/modified files and writing them to the
     * unified DB. This is what powers the pull-to-refresh indicator: the UI only
     * needs to confirm that local additions/deletions have landed.
     */
    val isFetchingChanges: Flow<Boolean> = syncProgress
        .map { progress ->
            progress.isRunning && progress.phase in CHANGE_PHASES
        }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    /**
     * Emits `true` while the worker is performing background maintenance that does
     * not gate the user's pull-to-refresh gesture: LRC scanning, album-art cache
     * cleanup, and cloud-source synchronization. This drives the slim linear
     * indicator under [LibraryActionRow].
     */
    val isPerformingMaintenance: Flow<Boolean> = syncProgress
        .map { progress ->
            progress.isRunning && progress.phase in MAINTENANCE_PHASES
        }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    fun sync() {
        sharingScope.launch {
            val now = System.currentTimeMillis()
            val lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()
            val shouldRunSync =
                lastSyncTimestamp <= 0L || (now - lastSyncTimestamp) >= MIN_SYNC_INTERVAL_MS

            if (!shouldRunSync) {
                val ageSeconds = (now - lastSyncTimestamp) / 1000
                Timber.tag(TAG).d("Skipping startup sync (last sync ${ageSeconds}s ago)")
                return@launch
            }

            Timber.tag(TAG).i("Startup sync requested - Scheduling Incremental Sync")
            enqueueSyncWork(
                request = SyncWorker.incrementalSyncWork(),
                policy = ExistingWorkPolicy.KEEP,
                notifyObserver = false
            )
        }
    }

    /**
     * Performs an incremental sync, only processing files that have changed
     * since the last sync. Much faster for large libraries with few changes.
     * This is the recommended sync method for pull-to-refresh actions.
     */
    fun incrementalSync() {
        Timber.tag(TAG).i("Incremental sync requested - Scheduling incremental worker")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Performs a full library rescan, ignoring the last sync timestamp.
     * Use this when the user explicitly wants to force a complete rescan.
     */
    fun fullSync(deepScan: Boolean = true) {
        Timber.tag(TAG).i("Full sync requested - Scheduling full sync worker")
        enqueueSyncWork(
            request = SyncWorker.fullSyncWork(deepScan = deepScan),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Rebuilds local MediaStore-backed songs from scratch while preserving cloud sources.
     * Local imported lyrics, favorites, and user metadata edits are removed for the rebuilt songs.
     * Use when local library data is corrupted or songs are missing.
     */
    fun rebuildDatabase() {
        Timber.tag(TAG).i("Rebuild database requested - Scheduling rebuild worker")
        enqueueSyncWork(
            request = SyncWorker.rebuildDatabaseWork(),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Schedules a fresh local incremental sync, replacing any pending foreground sync work.
     * The worker detects directory-rule version changes, so this still becomes a full local pass
     * when the caller just changed the selected folders.
     */
    fun forceRefresh() {
        Timber.tag(TAG).i("Manual local refresh requested - Scheduling incremental worker")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    private fun readCompletedSongCount(workInfo: WorkInfo): Int {
        val intValue = workInfo.outputData.getInt(SyncWorker.OUTPUT_TOTAL_SONGS, -1)
        if (intValue >= 0) return intValue

        return workInfo.outputData
            .getLong(SyncWorker.OUTPUT_TOTAL_SONGS, 0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun latestForegroundSyncWork(workInfos: List<WorkInfo>): WorkInfo? {
        val activeWork = workInfos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (activeWork != null) {
            currentSyncWorkId = activeWork.id
            return activeWork
        }

        return currentSyncWorkId?.let { id ->
            workInfos.firstOrNull { it.id == id }
        }
    }

    private fun observeStorageChanges() {
        sharingScope.launch {
            mediaStoreObserver.externalMediaStoreChanges.collect {
                scheduleLocalAutoSync()
            }
        }
    }

    private fun scheduleLocalAutoSync() {
        synchronized(autoSyncLock) {
            mediaStoreAutoSyncJob?.cancel()
            mediaStoreAutoSyncJob = sharingScope.launch {
                runLocalAutoSyncAfterDebounce()
            }
        }
    }

    private suspend fun runLocalAutoSyncAfterDebounce() {
        delay(MEDIASTORE_CHANGE_DEBOUNCE_MS)
        Timber.tag(TAG).i("Storage change detected - scheduling local incremental sync")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.KEEP,
            notifyObserver = false
        )
    }

    private fun observeAppForeground() {
        // ProcessLifecycleOwner is application-scoped; the observer and this @Singleton both
        // live for the whole process, so registering once here cannot leak.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                maybeRunForegroundCatchUpSync()
            }
        })
    }

    /**
     * Fast, maintenance-free incremental sync triggered when the app returns to the
     * foreground. Catches files MediaStore indexed while we were backgrounded (the
     * ContentObserver is only registered in the foreground). Guarded by an in-memory
     * cooldown so quick minimize/restore cycles don't pile up redundant work.
     */
    private fun maybeRunForegroundCatchUpSync() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncTime < FOREGROUND_SYNC_COOLDOWN_MS) {
            Timber.tag(TAG).d("Skipping foreground catch-up sync (cooldown active)")
            return
        }
        lastForegroundSyncTime = now
        Timber.tag(TAG).i("Foreground catch-up - scheduling local incremental sync")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.KEEP,
            notifyObserver = false
        )
    }

    private fun enqueueSyncWork(
        request: OneTimeWorkRequest,
        policy: ExistingWorkPolicy,
        notifyObserver: Boolean = true
    ) {
        currentSyncWorkId = request.id
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            policy,
            request
        )
        if (notifyObserver) {
            // Keep reactive MediaStore-based views in sync with manual refresh actions.
            mediaStoreObserver.forceRescan()
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val MEDIASTORE_CHANGE_DEBOUNCE_MS = 1_500L
        private const val FOREGROUND_SYNC_COOLDOWN_MS = 60_000L

        private val CHANGE_PHASES = setOf(
            SyncProgress.SyncPhase.IDLE,
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE,
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE
        )

        private val MAINTENANCE_PHASES = setOf(
            SyncProgress.SyncPhase.SCANNING_LRC,
            SyncProgress.SyncPhase.CLEANING_CACHE,
            SyncProgress.SyncPhase.SYNCING_CLOUD,
            SyncProgress.SyncPhase.COMPLETING
        )
    }
}
