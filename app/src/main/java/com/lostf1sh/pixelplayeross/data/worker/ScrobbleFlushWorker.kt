package com.lostf1sh.pixelplayeross.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzDao
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzPendingListenEntity
import com.lostf1sh.pixelplayeross.data.listenbrainz.ListenBrainzRepository
import com.lostf1sh.pixelplayeross.data.listenbrainz.ListenBrainzSubmitResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Drains the ListenBrainz pending-listen queue in batches, oldest first.
 *
 * Terminal outcomes per batch: success and permanent rejection delete rows; an invalid token
 * pauses flushing entirely (the queue survives until the user reconnects); rate limits and
 * network failures retry with backoff.
 */
@HiltWorker
class ScrobbleFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val listenBrainzDao: ListenBrainzDao,
    private val repository: ListenBrainzRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!repository.isAuthorized()) return Result.success()

        repeat(MAX_BATCHES_PER_RUN) {
            val batch = listenBrainzDao.oldestPending(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            when (val result = repository.submitListens(batch)) {
                ListenBrainzSubmitResult.Success -> {
                    listenBrainzDao.deleteByIds(batch.map { it.id })
                }
                ListenBrainzSubmitResult.InvalidPayload -> {
                    if (!dropInvalidListens(batch)) {
                        listenBrainzDao.incrementAttempts(batch.map { it.id })
                        return Result.retry()
                    }
                }
                ListenBrainzSubmitResult.AuthFailed -> {
                    Timber.w("ListenBrainz token rejected; pausing scrobble flush")
                    return Result.success()
                }
                is ListenBrainzSubmitResult.TransientError -> {
                    listenBrainzDao.incrementAttempts(batch.map { it.id })
                    val retryAfterSeconds = result.retryAfterSeconds
                    return if (retryAfterSeconds != null) {
                        // Honor the server's retry window instead of the generic backoff.
                        repository.scheduleFlushAfter(retryAfterSeconds)
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
            }
        }
        // Batch budget exhausted; remaining rows flush on the next enqueue or app start.
        return Result.success()
    }

    /**
     * A batch-level 400 does not identify the offending listen, so resubmit individually:
     * valid rows submit and delete, invalid rows delete. Returns false when no progress was
     * made (transient failure mid-drain), signalling the caller to retry the whole batch.
     */
    private suspend fun dropInvalidListens(batch: List<ListenBrainzPendingListenEntity>): Boolean {
        var progressed = false
        for (listen in batch) {
            when (repository.submitListens(listOf(listen))) {
                ListenBrainzSubmitResult.Success -> {
                    listenBrainzDao.deleteByIds(listOf(listen.id))
                    progressed = true
                }
                ListenBrainzSubmitResult.InvalidPayload -> {
                    Timber.w(
                        "Dropping listen rejected by ListenBrainz: %s — %s",
                        listen.artistName, listen.trackName
                    )
                    listenBrainzDao.deleteByIds(listOf(listen.id))
                    progressed = true
                }
                ListenBrainzSubmitResult.AuthFailed,
                is ListenBrainzSubmitResult.TransientError -> return progressed
            }
        }
        return true
    }

    companion object {
        const val WORK_NAME = "listenbrainz_scrobble_flush"
        private const val BATCH_SIZE = 50
        private const val MAX_BATCHES_PER_RUN = 100

        fun request(initialDelaySeconds: Long = 0L): OneTimeWorkRequest {
            val builder = OneTimeWorkRequestBuilder<ScrobbleFlushWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            if (initialDelaySeconds > 0L) {
                builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            }
            return builder.build()
        }
    }
}
