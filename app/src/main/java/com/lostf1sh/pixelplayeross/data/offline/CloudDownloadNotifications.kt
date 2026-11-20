package com.lostf1sh.pixelplayeross.data.offline

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lostf1sh.pixelplayeross.R

internal object CloudDownloadNotifications {
    private const val CHANNEL_ID = "cloud_download_results"
    private const val SUCCESS_ID = 0x5560
    private const val FAILURE_ID = 0x5561

    fun show(context: Context, trackTitle: String, @StringRes result: Int, failed: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.cloud_download_results_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(context.getString(result))
            .setContentText(trackTitle.ifBlank { context.getString(R.string.cloud_downloads_unknown_track) })
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(if (failed) FAILURE_ID else SUCCESS_ID, notification) }
    }
}
