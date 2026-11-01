package com.lostf1sh.pixelplayeross.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
@OptIn(UnstableApi::class)
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

    /**
     * The volume a fade should return the player to. Outside a fade that is the engine
     * player's own volume, where ReplayGain (or the user) set it. Mid-fade the engine player
     * only holds a ramp step, so reading it would make every later fade end near silence.
     */
    private fun settledVolume(): Float =
        if (fadeJob?.isActive == true) logicalVolume else innerPlayer.volume

    override fun play() {
        val targetVolume = settledVolume()
        fadeJob?.cancel()
        logicalVolume = targetVolume
        if (isPlaying) {
            // A pause fade cut short here has left the volume partway down.
            super.setVolume(targetVolume)
            super.play()
            return
        }

        super.setVolume(0f)
        super.play()

        fadeJob = scope.launch(Dispatchers.Main) {
            val stepTime = FADE_DURATION_MS / FADE_STEPS
            for (i in 1..FADE_STEPS) {
                if (!isActive) break
                val volume = (i.toFloat() / FADE_STEPS) * logicalVolume
                super.setVolume(volume)
                delay(stepTime)
            }
            super.setVolume(logicalVolume)
        }
    }

    override fun pause() {
        val settledVolume = settledVolume()
        fadeJob?.cancel()
        logicalVolume = settledVolume
        if (!isPlaying) {
            super.pause()
            // A play fade cut short while buffering has left the volume partway up.
            super.setVolume(settledVolume)
            return
        }

        // Ramp down from where the volume is now, which is below the settled level when
        // this cuts a play fade short.
        val startVolume = innerPlayer.volume
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
            super.setVolume(logicalVolume)
        }
    }

    private companion object {
        const val FADE_DURATION_MS = 500L
        const val FADE_STEPS = 25
    }
}
