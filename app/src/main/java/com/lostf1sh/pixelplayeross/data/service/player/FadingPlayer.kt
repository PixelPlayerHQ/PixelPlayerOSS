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
 *
 * A pause is reported as soon as its fade starts, not when the engine player stops at the
 * end of it. Controllers show a pause optimistically; a session that went on reporting
 * "playing" for the length of the fade overwrote that, so the pause button answered half a
 * second late and a second tap inside the fade paused again instead of resuming.
 */
@OptIn(UnstableApi::class)
class FadingPlayer(
    val innerPlayer: Player,
    private val scope: CoroutineScope,
) : ForwardingPlayer(innerPlayer) {

    private var fadeJob: Job? = null
    // Volume the player should sit at when no fade is running.
    private var logicalVolume = innerPlayer.volume
    // True while a pause fade ramps down: the pause is decided, only the audio trails it.
    private var isFadingToPause = false
    private val listeners = LinkedHashSet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getPlayWhenReady(): Boolean = !isFadingToPause && super.getPlayWhenReady()

    override fun isPlaying(): Boolean = !isFadingToPause && super.isPlaying()

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady && isFadingToPause) {
            // The engine player is still playing, so it would ignore this and the fade
            // would go on to pause it.
            play()
        } else {
            super.setPlayWhenReady(playWhenReady)
        }
    }

    /**
     * Enters or leaves the pause fade and tells listeners what that changes in the reported
     * state. The engine player emits nothing here: it keeps playing until the fade ends, and
     * never stopped if the fade is cut short.
     */
    private fun setFadingToPause(fading: Boolean) {
        if (isFadingToPause == fading) return
        val playWhenReadyBefore = playWhenReady
        val isPlayingBefore = isPlaying
        isFadingToPause = fading
        val playWhenReadyNow = playWhenReady
        val isPlayingNow = isPlaying
        if (playWhenReadyBefore == playWhenReadyNow && isPlayingBefore == isPlayingNow) return
        for (listener in listeners.toList()) {
            if (playWhenReadyBefore != playWhenReadyNow) {
                listener.onPlayWhenReadyChanged(
                    playWhenReadyNow,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
            }
            if (isPlayingBefore != isPlayingNow) {
                listener.onIsPlayingChanged(isPlayingNow)
            }
        }
    }

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
        if (innerPlayer.isPlaying) {
            // A pause fade cut short here has left the volume partway down.
            super.setVolume(targetVolume)
            setFadingToPause(false)
            super.play()
            return
        }

        setFadingToPause(false)
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
        if (!innerPlayer.isPlaying) {
            super.pause()
            // A play fade cut short while buffering has left the volume partway up.
            super.setVolume(settledVolume)
            setFadingToPause(false)
            return
        }

        setFadingToPause(true)

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
            setFadingToPause(false)
        }
    }

    private companion object {
        const val FADE_DURATION_MS = 500L
        const val FADE_STEPS = 25
    }
}
