package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wraps the session-facing player so play/pause ramp the volume over a short fade instead
 * of cutting the audio hard.
 *
 * The fade target is captured from the inner player at fade start: ReplayGain writes its
 * normalized volume directly on the engine's master player (not through this wrapper), so
 * tracking only wrapper-side [setVolume] calls would make every fade end at full volume and
 * defeat the normalization. [getVolume] reports the logical volume during a fade rather
 * than the transient mid-ramp value.
 */
class FadingPlayer(
    val innerPlayer: Player,
    private val scope: CoroutineScope,
) : ForwardingPlayer(innerPlayer) {

    private var fadeJob: Job? = null
    // Volume the player should sit at when no fade is running.
    private var logicalVolume = innerPlayer.volume

    override fun setVolume(volume: Float) {
        logicalVolume = volume
        if (fadeJob?.isActive != true) {
            super.setVolume(volume)
        }
    }

    override fun getVolume(): Float {
        return if (fadeJob?.isActive == true) logicalVolume else super.getVolume()
    }

    override fun play() {
        fadeJob?.cancel()
        if (isPlaying) {
            super.play()
            return
        }

        // Capture the volume ReplayGain (or the user) set directly on the engine player.
        logicalVolume = innerPlayer.volume
        val targetVolume = logicalVolume
        super.setVolume(0f)
        super.play()

        fadeJob = scope.launch(Dispatchers.Main) {
            val stepTime = FADE_DURATION_MS / FADE_STEPS
            for (i in 1..FADE_STEPS) {
                if (!isActive) break
                val volume = (i.toFloat() / FADE_STEPS) * targetVolume
                super.setVolume(volume)
                delay(stepTime)
            }
            super.setVolume(targetVolume)
        }
    }

    override fun pause() {
        fadeJob?.cancel()
        if (!isPlaying) {
            super.pause()
            return
        }

        val startVolume = innerPlayer.volume
        logicalVolume = startVolume
        fadeJob = scope.launch(Dispatchers.Main) {
            val stepTime = FADE_DURATION_MS / FADE_STEPS
            for (i in 1..FADE_STEPS) {
                if (!isActive) break
                val volume = ((FADE_STEPS - i).toFloat() / FADE_STEPS) * startVolume
                super.setVolume(volume)
                delay(stepTime)
            }
            super.setVolume(0f)
            super.pause()
            // Restore the logical volume so the paused player resumes from the right level.
            super.setVolume(startVolume)
        }
    }

    private companion object {
        const val FADE_DURATION_MS = 500L
        const val FADE_STEPS = 25
    }
}
