package com.lostf1sh.pixelplayeross.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope

/**
 * Wraps the session-facing player, ensuring volume levels remain synchronized
 * and playback commands (play/pause) execute immediately without coroutine delays
 * that could race against playback completion listeners (STATE_ENDED / onMediaItemTransition).
 */
@OptIn(UnstableApi::class)
class FadingPlayer(
    val innerPlayer: Player,
    private val scope: CoroutineScope,
) : ForwardingPlayer(innerPlayer) {

    private var logicalVolume = innerPlayer.volume

    init {
        innerPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Ensure volume is restored to logical level on every track transition
                super@FadingPlayer.setVolume(logicalVolume)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    super@FadingPlayer.setVolume(logicalVolume)
                }
            }
        })
    }

    override fun setVolume(volume: Float) {
        logicalVolume = volume
        super.setVolume(volume)
    }

    override fun getVolume(): Float {
        return super.getVolume()
    }

    override fun play() {
        super.setVolume(logicalVolume)
        super.play()
    }

    override fun pause() {
        super.pause()
        super.setVolume(logicalVolume)
    }
}
