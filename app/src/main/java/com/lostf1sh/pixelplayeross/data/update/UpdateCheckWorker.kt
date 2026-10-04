package com.lostf1sh.pixelplayeross.data.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.di.AppScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Daily background check that posts a notification once per newly found release. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateManager: AppUpdateManager,
    private val preferences: UserPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val update = try {
            updateManager.findUpdate()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.w(error, "Background update check failed")
            return Result.retry()
        }
        val available = update as? UpdateStatus.Available ?: return Result.success()
        // Channel switches need the user's hands; only same-channel updates are worth a nudge.
        if (available.requiresReinstall) return Result.success()

        val versionName = available.release.versionName
        if (preferences.getLastNotifiedUpdateVersion() == versionName) return Result.success()
        if (UpdateNotifications.showUpdateAvailable(applicationContext, versionName)) {
            preferences.setLastNotifiedUpdateVersion(versionName)
        }
        return Result.success()
    }
}

/** Keeps the periodic update check in step with the user's auto-check preference. */
@Singleton
class UpdateCheckScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val updateManager: AppUpdateManager,
    @AppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            updateManager.autoCheckEnabled.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    workManager.enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                            .setConstraints(
                                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                            )
                            .build()
                    )
                } else {
                    workManager.cancelUniqueWork(WORK_NAME)
                }
            }
        }
    }

    private companion object {
        const val WORK_NAME = "app_update_check"
    }
}
