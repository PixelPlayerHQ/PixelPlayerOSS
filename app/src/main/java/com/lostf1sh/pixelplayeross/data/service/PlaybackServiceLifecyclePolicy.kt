package com.lostf1sh.pixelplayeross.data.service

import androidx.media3.common.Player

internal enum class TaskRemovalAction {
    KEEP_RUNNING,
    STOP_AND_PRESERVE,
    DEFER_TO_MEDIA3,
}

internal fun shouldStartTemporaryForegroundOnCreate(
    hasPendingMediaButtonStart: Boolean,
): Boolean = hasPendingMediaButtonStart

internal fun taskRemovalAction(
    keepPlayingInBackground: Boolean,
    isPlaybackOngoing: Boolean,
    playWhenReady: Boolean,
    mediaItemCount: Int,
    playbackState: Int,
): TaskRemovalAction {
    val hasPlaybackIntent =
        playWhenReady &&
            mediaItemCount > 0 &&
            playbackState != Player.STATE_IDLE &&
            playbackState != Player.STATE_ENDED

    return when {
        !keepPlayingInBackground && hasPlaybackIntent ->
            TaskRemovalAction.STOP_AND_PRESERVE
        keepPlayingInBackground && isPlaybackOngoing && hasPlaybackIntent ->
            TaskRemovalAction.KEEP_RUNNING
        else ->
            TaskRemovalAction.DEFER_TO_MEDIA3
    }
}
