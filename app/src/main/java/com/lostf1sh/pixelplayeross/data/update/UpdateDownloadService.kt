package com.lostf1sh.pixelplayeross.data.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the process and its network access alive while an update downloads. Without it,
 * leaving the app mid-download lets the system cache the process, which cuts its sockets.
 * The download itself stays in [AppUpdateManager]; this service mirrors its progress and
 * stops as soon as the status leaves [UpdateStatus.Downloading].
 */
class UpdateDownloadService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdateDownloadServiceEntryPoint {
        fun appUpdateManager(): AppUpdateManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = EntryPointAccessors.fromApplication(applicationContext, UpdateDownloadServiceEntryPoint::class.java)
            .appUpdateManager()
        val downloading = manager.status.value as? UpdateStatus.Downloading
        // startForeground must run even when there is nothing left to do, or the system
        // treats the startForegroundService() call as a broken promise.
        ServiceCompat.startForeground(
            this,
            UpdateNotifications.DOWNLOAD_ID,
            UpdateNotifications.downloadProgress(this, downloading?.release?.versionName.orEmpty(), downloading?.progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (downloading == null) {
            stop()
            return START_NOT_STICKY
        }
        scope.launch {
            manager.status
                .takeWhile { it is UpdateStatus.Downloading }
                .collect { status ->
                    status as UpdateStatus.Downloading
                    UpdateNotifications.updateDownloadProgress(this@UpdateDownloadService, status.release.versionName, status.progress)
                }
            stop()
        }
        return START_NOT_STICKY
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Call from a user action while the app is visible; FGS starts from the background are refused. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, UpdateDownloadService::class.java))
            }.onFailure { Timber.w(it, "Could not keep the update download in the foreground") }
        }
    }
}
