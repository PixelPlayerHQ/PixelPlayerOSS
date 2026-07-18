package com.lostf1sh.pixelplayeross.data.navidrome

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lostf1sh.pixelplayeross.data.database.NavidromeDao
import com.lostf1sh.pixelplayeross.data.database.NavidromePendingFavoriteEntity
import com.lostf1sh.pixelplayeross.data.network.navidrome.NavidromeApiService
import com.lostf1sh.pixelplayeross.data.worker.NavidromeFavoritesPushWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Outbox for favorite changes on Navidrome songs: records pending star/unstar
 * ops and pushes them to the server when connectivity allows.
 */
@Singleton
class NavidromeFavoritesSyncManager @Inject constructor(
    private val api: NavidromeApiService,
    private val navidromeDao: NavidromeDao,
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "NavidromeFavSync"
        const val PUSH_WORK_NAME = "navidrome_favorites_push"
        const val MAX_ATTEMPTS = 8
    }

    suspend fun onFavoriteToggled(navidromeSongId: String, favorite: Boolean) {
        if (!api.hasCredentials()) return
        navidromeDao.upsertPendingFavorite(
            NavidromePendingFavoriteEntity(
                navidromeSongId = navidromeSongId,
                isStar = favorite
            )
        )
        schedulePush()
    }

    fun schedulePush() {
        val request = OneTimeWorkRequestBuilder<NavidromeFavoritesPushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(PUSH_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Pushes all pending ops. Returns true when the queue is fully drained.
     * Ops are deleted on success and dropped after MAX_ATTEMPTS failures so a
     * permanently failing song (e.g. deleted server-side) cannot block the queue.
     */
    suspend fun drainPendingFavorites(): Boolean {
        if (!api.hasCredentials()) {
            navidromeDao.clearPendingFavorites()
            return true
        }
        var allSucceeded = true
        navidromeDao.getPendingFavoritesOnce().forEach { op ->
            val result = if (op.isStar) api.star(id = op.navidromeSongId) else api.unstar(id = op.navidromeSongId)
            result.fold(
                onSuccess = { navidromeDao.deletePendingFavorite(op.navidromeSongId) },
                onFailure = { error ->
                    Timber.w("$TAG: push failed for ${op.navidromeSongId} (attempt ${op.attempts + 1}): ${error.message}")
                    if (op.attempts + 1 >= MAX_ATTEMPTS) {
                        navidromeDao.deletePendingFavorite(op.navidromeSongId)
                    } else {
                        navidromeDao.incrementPendingFavoriteAttempts(op.navidromeSongId)
                        allSucceeded = false
                    }
                }
            )
        }
        return allSucceeded
    }
}
