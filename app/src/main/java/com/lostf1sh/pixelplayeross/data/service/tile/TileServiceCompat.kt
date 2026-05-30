package com.lostf1sh.pixelplayeross.data.service.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

@SuppressLint("StartActivityAndCollapseDeprecated")
internal fun TileService.startActivityAndCollapseCompat(
    intent: Intent,
    requestCode: Int
) {
    // The PendingIntent overload only exists on Android 14+, while older releases
    // still require the deprecated Intent overload.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
        return
    }

    @Suppress("DEPRECATION")
    startActivityAndCollapse(intent)
}
