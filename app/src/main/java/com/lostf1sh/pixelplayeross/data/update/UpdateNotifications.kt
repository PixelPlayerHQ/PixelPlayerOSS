package com.lostf1sh.pixelplayeross.data.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.MainActivityIntentContract
import com.lostf1sh.pixelplayeross.R

/** Notifications for the updater: "update available", download progress, "finish installing". */
internal object UpdateNotifications {
    private const val CHANNEL_ID = "app_updates"
    private const val AVAILABLE_ID = 0x5550
    private const val CONFIRM_ID = 0x5551
    const val DOWNLOAD_ID = 0x5552

    /** Posts "update available"; returns false when the user would not see it. */
    fun showUpdateAvailable(context: Context, versionName: String): Boolean {
        return post(
            context,
            AVAILABLE_ID,
            builder(context)
                .setContentTitle(context.getString(R.string.updates_notification_title))
                .setContentText(context.getString(R.string.updates_notification_text, versionName))
                .setContentIntent(openUpdates(context))
                .build()
        )
    }

    /** Ongoing notification for the foreground service that keeps a download alive in the background. */
    fun downloadProgress(context: Context, versionName: String, progress: Float?): Notification {
        ensureChannel(context)
        val percent = progress?.let { (it * 100).toInt() }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(context.getString(R.string.updates_status_downloading, versionName))
            .setProgress(100, percent ?: 0, percent == null)
            .setContentIntent(openUpdates(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    @SuppressLint("MissingPermission") // Checked in canPost.
    fun updateDownloadProgress(context: Context, versionName: String, progress: Float?) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(DOWNLOAD_ID, downloadProgress(context, versionName, progress))
    }

    private fun openUpdates(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        AVAILABLE_ID,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivityIntentContract.ACTION_OPEN_UPDATES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Hands the system install prompt to the user when it can't be opened directly: the app is
     * in the background (activity starts are blocked) or the process was recreated and lost it.
     */
    fun showInstallConfirmation(context: Context, confirmIntent: Intent): Boolean {
        val openPrompt = PendingIntent.getActivity(
            context,
            CONFIRM_ID,
            Intent(confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return post(
            context,
            CONFIRM_ID,
            builder(context)
                .setContentTitle(context.getString(R.string.updates_notification_confirm_title))
                .setContentText(context.getString(R.string.updates_notification_confirm_text))
                .setContentIntent(openPrompt)
                .build()
        )
    }

    fun dismissInstallConfirmation(context: Context) {
        NotificationManagerCompat.from(context).cancel(CONFIRM_ID)
    }

    private fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.monochrome_player)
        .setAutoCancel(true)

    @SuppressLint("MissingPermission") // Checked in canPost.
    private fun post(context: Context, id: Int, notification: Notification): Boolean {
        if (!canPost(context)) return false
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private fun canPost(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = ensureChannel(context)
        // A blocked channel or app accepts notify() silently; the user would never see it.
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun ensureChannel(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.updates_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        return manager
    }
}
