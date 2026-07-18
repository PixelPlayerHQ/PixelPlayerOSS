package com.lostf1sh.pixelplayeross.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeFavoritesSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class NavidromeFavoritesPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val favoritesSyncManager: NavidromeFavoritesSyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (favoritesSyncManager.drainPendingFavorites()) Result.success() else Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "NavidromeFavoritesPushWorker: push failed")
            Result.retry()
        }
    }
}
