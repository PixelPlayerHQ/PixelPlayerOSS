package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatConfig
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import timber.log.Timber
import android.media.AudioAttributes as PlatformAudioAttributes

/** A mixer format offered by an output device, reduced to what format matching needs. */
internal data class MixerFormat(
    val encoding: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val bitPerfect: Boolean,
)

/**
 * Index in [offered] of the bit-perfect mixer format that carries PCM in [pipelineEncoding] at
 * [sampleRate] and [channelCount] without changing any sample value, or -1.
 *
 * Float from the decoder holds 16- and 24-bit samples exactly, so it can be widened into a 24- or
 * 32-bit integer container. 16-bit PCM can go out as is or widened the same way.
 */
internal fun selectBitPerfectMixerFormat(
    offered: List<MixerFormat>,
    sampleRate: Int,
    channelCount: Int,
    pipelineEncoding: Int,
): Int {
    val preference = when (pipelineEncoding) {
        C.ENCODING_PCM_16BIT -> listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        C.ENCODING_PCM_FLOAT -> listOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        else -> emptyList()
    }
    for (encoding in preference) {
        val index = offered.indexOfFirst {
            it.bitPerfect && it.encoding == encoding &&
                it.sampleRate == sampleRate && it.channelCount == channelCount
        }
        if (index >= 0) return index
    }
    return -1
}

/**
 * Plays through a USB DAC's bit-perfect mixer (Android 14+) when the phone and DAC offer one for
 * the track's format, and through the normal path otherwise.
 *
 * Before each AudioTrack is created, the matching `MIXER_BEHAVIOR_BIT_PERFECT` attributes are set
 * as the app's preferred mixer for media on that device, so Android opens the output at the track's
 * own rate and format and passes the samples to the DAC untouched. DefaultAudioSink only outputs
 * 16-bit or float PCM, while USB DACs take integers, so float is widened to the DAC's 24- or 32-bit
 * container on the way into the AudioTrack ([PcmWideningAudioOutput]).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(UnstableApi::class)
class BitPerfectUsbAudioOutputProvider(
    context: Context,
    private val delegate: AudioOutputProvider = AudioTrackAudioOutputProvider.Builder(context).build(),
) : AudioOutputProvider {

    private companion object {
        const val TAG = "BitPerfectUsb"
        const val MAX_PLANS = 8
        val USB_TYPES = setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
        )
    }

    private class Plan(
        val device: AudioDeviceInfo,
        val attributes: PlatformAudioAttributes,
        val mixer: AudioMixerAttributes,
        val outputConfig: OutputConfig,
    )

    private class AppliedMixer(val device: AudioDeviceInfo, val attributes: PlatformAudioAttributes)

    private val audioManager = context.getSystemService(AudioManager::class.java)

    // Keyed by the config handed to DefaultAudioSink, which passes it back to getAudioOutput.
    private val plans = HashMap<OutputConfig, Plan>()
    private var appliedMixer: AppliedMixer? = null

    override fun getFormatSupport(formatConfig: FormatConfig) = delegate.getFormatSupport(formatConfig)

    override fun getOutputConfig(formatConfig: FormatConfig): OutputConfig {
        val config = delegate.getOutputConfig(formatConfig)
        return try {
            planBitPerfectOutput(formatConfig, config)
        } catch (e: Exception) {
            // Never fail playback over the bit-perfect attempt; the normal path still works.
            Timber.tag(TAG).w(e, "Bit-perfect setup failed; using the normal path")
            config
        }
    }

    private fun planBitPerfectOutput(formatConfig: FormatConfig, config: OutputConfig): OutputConfig {
        val format = formatConfig.format
        if (config.isOffload || config.isTunneling || format.sampleMimeType != MimeTypes.AUDIO_RAW) return config

        val attributes = formatConfig.audioAttributes.platformAudioAttributes
        val device = formatConfig.preferredDevice?.takeIf { it.type in USB_TYPES }
            ?: audioManager.getAudioDevicesForAttributes(attributes).firstOrNull { it.type in USB_TYPES }
        if (device == null) {
            Timber.tag(TAG).d("No USB output for media; using the normal path")
            return config
        }

        val offered = audioManager.getSupportedMixerAttributes(device)
        val index = selectBitPerfectMixerFormat(
            offered.map { it.toMixerFormat() },
            format.sampleRate,
            format.channelCount,
            format.pcmEncoding,
        )
        if (index < 0) {
            Timber.tag(TAG).i(
                "%s offers no bit-perfect mixer for %d Hz, %d ch, encoding %d. Offered: %s",
                device.productName, format.sampleRate, format.channelCount, format.pcmEncoding,
                offered.joinToString { it.toMixerFormat().toString() }
            )
            return config
        }

        val mixer = offered[index]
        val mixerEncoding = mixer.format.encoding
        if (plans.size >= MAX_PLANS) plans.clear()
        if (mixerEncoding == format.pcmEncoding) {
            plans[config] = Plan(device, attributes, mixer, config)
            return config
        }
        // The AudioTrack runs in the DAC's integer format; DefaultAudioSink keeps seeing its own.
        val widened = delegate.getOutputConfig(formatConfig.withPcmEncoding(mixerEncoding))
        val forSink = widened.buildUpon()
            .setEncoding(format.pcmEncoding)
            .setBufferSize(
                widened.bufferSize / PcmWideningAudioOutput.bytesPerSample(mixerEncoding) *
                    PcmWideningAudioOutput.bytesPerSample(format.pcmEncoding)
            )
            .build()
        plans[forSink] = Plan(device, attributes, mixer, widened)
        return forSink
    }

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val plan = plans[config]
        if (plan == null) {
            clearPreferredMixer()
            return delegate.getAudioOutput(config)
        }
        applyPreferredMixer(plan)
        val output = delegate.getAudioOutput(plan.outputConfig)
        return if (plan.outputConfig.encoding == config.encoding) {
            output
        } else {
            PcmWideningAudioOutput(output, config.encoding, plan.outputConfig.encoding)
        }
    }

    override fun addListener(listener: AudioOutputProvider.Listener) = delegate.addListener(listener)

    override fun removeListener(listener: AudioOutputProvider.Listener) = delegate.removeListener(listener)

    override fun setClock(clock: Clock) = delegate.setClock(clock)

    override fun release() {
        clearPreferredMixer()
        plans.clear()
        delegate.release()
    }

    private fun applyPreferredMixer(plan: Plan) {
        try {
            val current = audioManager.getPreferredMixerAttributes(plan.attributes, plan.device)
            if (current != plan.mixer) {
                val applied = audioManager.setPreferredMixerAttributes(plan.attributes, plan.device, plan.mixer)
                Timber.tag(TAG).i(
                    "Preferred mixer on %s -> %s: %s",
                    plan.device.productName, plan.mixer.toMixerFormat(), if (applied) "applied" else "rejected"
                )
                if (!applied) return
            }
            appliedMixer = AppliedMixer(plan.device, plan.attributes)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to set the preferred mixer")
        }
    }

    private fun clearPreferredMixer() {
        val applied = appliedMixer ?: return
        appliedMixer = null
        runCatching { audioManager.clearPreferredMixerAttributes(applied.attributes, applied.device) }
            .onFailure { Timber.tag(TAG).w(it, "Failed to clear the preferred mixer") }
        Timber.tag(TAG).i("Preferred mixer cleared on %s", applied.device.productName)
    }

    private fun AudioMixerAttributes.toMixerFormat() = MixerFormat(
        encoding = format.encoding,
        sampleRate = format.sampleRate,
        channelCount = format.channelCount,
        bitPerfect = mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
    )

    private fun FormatConfig.withPcmEncoding(encoding: Int): FormatConfig =
        FormatConfig.Builder(format.buildUpon().setPcmEncoding(encoding).build())
            .setAudioAttributes(audioAttributes)
            .setPreferredDevice(preferredDevice)
            .setEnableHighResolutionPcmOutput(enableHighResolutionPcmOutput)
            .setEnablePlaybackParameters(enablePlaybackParameters)
            .setEnableOffload(false)
            .setAudioSessionId(audioSessionId)
            .setVirtualDeviceId(virtualDeviceId)
            .setEnableTunneling(false)
            .setPreferredBufferSize(preferredBufferSize)
            .build()
}
