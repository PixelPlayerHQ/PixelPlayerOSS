package com.lostf1sh.pixelplayeross.data.equalizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Runs [EqualizerDsp] on decoded PCM before it reaches [sink].
 *
 * Sits in front of the sink rather than in its AudioProcessor chain because DefaultAudioSink skips
 * that chain for hi-res input when float output is on, and converts to 16 bit before it otherwise.
 * Here every PCM encoding is processed at its own precision, in every output mode. While the
 * effects are neutral the decoder's buffer is forwarded as is, so output stays bit-exact.
 * Encoded input (offload, passthrough) is never touched.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioSink(
    sink: AudioSink,
    private val settings: StateFlow<AudioEffectsSettings>,
) : ForwardingAudioSink(sink) {

    private val dsp = EqualizerDsp()
    private var encoding = C.ENCODING_INVALID // ENCODING_INVALID: pass input through
    private var channelCount = 0
    private var samples = DoubleArray(0)
    private var processed: ByteBuffer = ByteBuffer.allocateDirect(0)

    // A sink that returns false must be handed the same buffer again, so remember what we sent.
    private var pendingInput: ByteBuffer? = null
    private var pendingForwarded: ByteBuffer? = null

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val isPcm = inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW &&
            bytesPerSample(inputFormat.pcmEncoding) > 0 &&
            inputFormat.channelCount > 0
        encoding = if (isPcm) inputFormat.pcmEncoding else C.ENCODING_INVALID
        channelCount = inputFormat.channelCount
        if (isPcm) dsp.configure(inputFormat.sampleRate, channelCount)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val forwarded = if (buffer === pendingInput) pendingForwarded!! else prepare(buffer)
        pendingInput = buffer
        pendingForwarded = forwarded
        if (!super.handleBuffer(forwarded, presentationTimeUs, encodedAccessUnitCount)) return false
        pendingInput = null
        pendingForwarded = null
        if (forwarded !== buffer) buffer.position(buffer.limit())
        return true
    }

    override fun flush() {
        super.flush()
        clearPending()
    }

    override fun reset() {
        super.reset()
        clearPending()
    }

    private fun clearPending() {
        pendingInput = null
        pendingForwarded = null
        dsp.reset()
    }

    private fun prepare(input: ByteBuffer): ByteBuffer {
        if (encoding == C.ENCODING_INVALID) return input
        dsp.update(settings.value)
        if (dsp.isBypassed) return input

        val sampleBytes = bytesPerSample(encoding)
        val frames = input.remaining() / (sampleBytes * channelCount)
        val count = frames * channelCount
        if (samples.size < count) samples = DoubleArray(count)
        if (processed.capacity() < input.remaining()) {
            processed = ByteBuffer.allocateDirect(input.remaining()).order(ByteOrder.nativeOrder())
        }
        processed.clear() // drop the previous buffer's limit before writing past it
        val order = if (isBigEndian(encoding)) ByteOrder.BIG_ENDIAN else ByteOrder.nativeOrder()
        val source = input.duplicate().order(order)
        val target = processed.duplicate().order(order)
        val base = input.position()

        read(source, base, count)
        dsp.process(samples, frames)
        write(target, count)
        // Decoders emit whole frames; copy a stray tail verbatim rather than dropping it.
        for (i in count * sampleBytes until input.remaining()) target.put(i, source.get(base + i))

        processed.position(0)
        processed.limit(input.remaining())
        return processed
    }

    private fun read(source: ByteBuffer, base: Int, count: Int) {
        val s = samples
        when (encoding) {
            C.ENCODING_PCM_8BIT ->
                for (i in 0 until count) s[i] = ((source.get(base + i).toInt() and 0xFF) - 128) / 128.0
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                for (i in 0 until count) s[i] = source.getShort(base + 2 * i) / 32_768.0
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                val bigEndian = isBigEndian(encoding)
                for (i in 0 until count) {
                    val o = base + 3 * i
                    val low = if (bigEndian) source.get(o + 2) else source.get(o)
                    val high = if (bigEndian) source.get(o) else source.get(o + 2)
                    val bits = (high.toInt() shl 16) or
                        ((source.get(o + 1).toInt() and 0xFF) shl 8) or
                        (low.toInt() and 0xFF)
                    s[i] = bits / 8_388_608.0
                }
            }
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                for (i in 0 until count) s[i] = source.getInt(base + 4 * i) / 2_147_483_648.0
            C.ENCODING_PCM_FLOAT ->
                for (i in 0 until count) s[i] = source.getFloat(base + 4 * i).toDouble()
            C.ENCODING_PCM_DOUBLE ->
                for (i in 0 until count) s[i] = source.getDouble(base + 8 * i)
        }
    }

    private fun write(target: ByteBuffer, count: Int) {
        val s = samples
        when (encoding) {
            C.ENCODING_PCM_8BIT ->
                for (i in 0 until count) {
                    target.put(i, ((s[i] * 128).roundToInt().coerceIn(-128, 127) + 128).toByte())
                }
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                for (i in 0 until count) {
                    target.putShort(2 * i, (s[i] * 32_768).roundToInt().coerceIn(-32_768, 32_767).toShort())
                }
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                val bigEndian = isBigEndian(encoding)
                for (i in 0 until count) {
                    val bits = (s[i] * 8_388_608).roundToInt().coerceIn(-8_388_608, 8_388_607)
                    val o = 3 * i
                    target.put(if (bigEndian) o + 2 else o, bits.toByte())
                    target.put(o + 1, (bits shr 8).toByte())
                    target.put(if (bigEndian) o else o + 2, (bits shr 16).toByte())
                }
            }
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                for (i in 0 until count) {
                    val bits = (s[i] * 2_147_483_648.0).roundToLong()
                        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    target.putInt(4 * i, bits.toInt())
                }
            C.ENCODING_PCM_FLOAT -> for (i in 0 until count) target.putFloat(4 * i, s[i].toFloat())
            C.ENCODING_PCM_DOUBLE -> for (i in 0 until count) target.putDouble(8 * i, s[i])
        }
    }

    private fun isBigEndian(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
        C.ENCODING_PCM_DOUBLE -> 8
        else -> 0
    }
}
