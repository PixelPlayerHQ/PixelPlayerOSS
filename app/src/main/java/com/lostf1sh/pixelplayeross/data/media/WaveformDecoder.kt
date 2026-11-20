package com.lostf1sh.pixelplayeross.data.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Decodes a local audio file with the platform decoder and reduces it to a [WaveformData].
 * Blocking and CPU-heavy: call from a background dispatcher.
 */
internal object WaveformDecoder {

    private const val TIMEOUT_US = 10_000L

    /** Longer files (podcasts, DJ sets) aren't worth minutes of decoding for a transition editor. */
    private const val MAX_DURATION_US = 30L * 60 * 1_000_000

    /** Null when the file can't be decoded, is too long, or [isCancelled] turned true. */
    fun decode(path: String, bucketMs: Int, isCancelled: () -> Boolean): WaveformData? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            if (format.containsKey(MediaFormat.KEY_DURATION) &&
                format.getLong(MediaFormat.KEY_DURATION) > MAX_DURATION_US
            ) return null

            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var accumulator: PeakAccumulator? = null
            var isFloat = false
            var scratch = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            fun readOutputFormat(outputFormat: MediaFormat) {
                accumulator = PeakAccumulator(
                    sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    bucketMs = bucketMs,
                )
                isFloat = outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            }

            while (!outputDone) {
                if (isCancelled()) return null

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: return null
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readOutputFormat(codec.outputFormat)
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            if (accumulator == null) readOutputFormat(codec.outputFormat)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            output.order(ByteOrder.LITTLE_ENDIAN)
                            val count: Int
                            if (isFloat) {
                                val floats = output.asFloatBuffer()
                                count = floats.remaining()
                                if (scratch.size < count) scratch = ShortArray(count)
                                for (i in 0 until count) {
                                    scratch[i] = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                }
                            } else {
                                val shorts = output.asShortBuffer()
                                count = shorts.remaining()
                                if (scratch.size < count) scratch = ShortArray(count)
                                shorts.get(scratch, 0, count)
                            }
                            accumulator?.add(scratch, count)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }

            return accumulator?.finish()?.takeIf { it.peaks.isNotEmpty() }
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
