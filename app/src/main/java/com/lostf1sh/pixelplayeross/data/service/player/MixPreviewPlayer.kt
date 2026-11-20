package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Auditions a [MixTransition] in the editor on two scratch players, so the user's queue and the
 * main engine are never touched. It plays a short lead-in, the mix window with the same volume
 * lines the engine uses ([MixEnvelope]), and a short tail of the incoming song.
 *
 * Main-thread only. Call [stop] (or [release]) when done; it frees both players.
 */
class MixPreviewPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var outgoing: ExoPlayer? = null
    private var incoming: ExoPlayer? = null
    private var job: Job? = null

    private val _positionMs = MutableStateFlow<Long?>(null)

    /** Playhead as milliseconds from the window start (negative during the lead-in); null when stopped. */
    val positionMs: StateFlow<Long?> = _positionMs.asStateFlow()

    val isPlaying: Boolean get() = job?.isActive == true

    fun start(outgoingUri: Uri, incomingUri: Uri, mix: MixTransition) {
        stop()
        val leadIn = minOf(LEAD_IN_MS, mix.outPointMs)

        val out = buildPlayer(handleAudioFocus = true).apply {
            setMediaItem(MediaItem.fromUri(outgoingUri))
            seekTo(mix.outPointMs - leadIn)
            volume = 1f
            prepare()
            playWhenReady = true
        }
        val inc = buildPlayer(handleAudioFocus = false).apply {
            setMediaItem(MediaItem.fromUri(incomingUri))
            seekTo(mix.inStartMs)
            volume = 0f
            prepare() // stays paused until the window opens
        }
        outgoing = out
        incoming = inc

        job = scope.launch {
            var incomingStarted = false
            var outgoingStopped = false
            while (isActive) {
                val outPos = out.currentPosition
                val rel = if (!outgoingStopped) {
                    outPos - mix.outPointMs
                } else {
                    inc.currentPosition - mix.inStartMs
                }
                _positionMs.value = rel

                if (rel >= 0 && !incomingStarted) {
                    inc.play()
                    incomingStarted = true
                }

                if (rel >= 0 && rel < mix.windowMs) {
                    val progress = rel.toFloat() / mix.windowMs
                    out.volume = MixEnvelope.volumeAt(mix.outVolume, progress)
                    inc.volume = MixEnvelope.volumeAt(mix.inVolume, progress)
                } else if (rel >= mix.windowMs && !outgoingStopped) {
                    out.volume = 0f
                    out.pause()
                    inc.volume = MixEnvelope.volumeAt(mix.inVolume, 1f)
                    outgoingStopped = true
                }

                val outgoingEnded = out.playbackState == Player.STATE_ENDED
                if (rel >= mix.windowMs + TAIL_MS || (outgoingEnded && !incomingStarted)) break
                delay(FRAME_MS)
            }
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        release()
        _positionMs.value = null
    }

    fun release() {
        outgoing?.release()
        incoming?.release()
        outgoing = null
        incoming = null
    }

    private fun buildPlayer(handleAudioFocus: Boolean): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus,
            )
        }

    private companion object {
        const val LEAD_IN_MS = 4_000L
        const val TAIL_MS = 4_000L
        const val FRAME_MS = 30L
    }
}
