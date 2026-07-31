package com.lostf1sh.pixelplayeross.data.offline

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class OfflineMediaDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.offline_cache_notification_channel,
    R.string.offline_cache_notification_channel_description,
) {
    @Inject lateinit var repository: OfflineMediaRepository

    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = repository.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.monochrome_player,
            contentIntent,
            getString(R.string.offline_cache_notification_message),
            downloads,
            notMetRequirements,
        )
    }

    private companion object {
        const val CHANNEL_ID = "offline_media_cache"
        const val NOTIFICATION_ID = 48
    }
}
