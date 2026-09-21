package com.lostf1sh.pixelplayeross.utils

import android.Manifest
import android.os.Build

/** Explicit selected-photo handling avoids Android 14's temporary false full-access grant. */
@android.annotation.SuppressLint("InlinedApi") // Guarded by the SDK argument, injectable for tests.
internal fun folderArtworkPermissions(sdkVersion: Int): List<String> = when {
    sdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    sdkVersion >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
