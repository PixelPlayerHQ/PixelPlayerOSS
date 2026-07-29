package com.lostf1sh.pixelplayeross.data.service

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackServiceLifecyclePolicyTest {

    @Test
    fun `normal controller connection does not start temporary foreground notification`() {
        assertFalse(
            shouldStartTemporaryForegroundOnCreate(
                hasPendingMediaButtonStart = false
            )
        )
    }

    @Test
    fun `cold media button start gets temporary foreground protection`() {
        assertTrue(
            shouldStartTemporaryForegroundOnCreate(
                hasPendingMediaButtonStart = true
            )
        )
    }

    @Test
    fun `enabled background playback survives task removal while buffering`() {
        assertEquals(
            TaskRemovalAction.KEEP_RUNNING,
            taskRemovalAction(
                keepPlayingInBackground = true,
                isPlaybackOngoing = true,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_BUFFERING,
            )
        )
    }

    @Test
    fun `disabled background playback stops active playback on task removal`() {
        assertEquals(
            TaskRemovalAction.STOP_AND_PRESERVE,
            taskRemovalAction(
                keepPlayingInBackground = false,
                isPlaybackOngoing = true,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
            )
        )
    }

    @Test
    fun `non-foreground service teardown remains with Media3`() {
        assertEquals(
            TaskRemovalAction.DEFER_TO_MEDIA3,
            taskRemovalAction(
                keepPlayingInBackground = true,
                isPlaybackOngoing = false,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_BUFFERING,
            )
        )
    }

    @Test
    fun `paused playback teardown remains with Media3`() {
        assertEquals(
            TaskRemovalAction.DEFER_TO_MEDIA3,
            taskRemovalAction(
                keepPlayingInBackground = false,
                isPlaybackOngoing = false,
                playWhenReady = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
            )
        )
    }
}
