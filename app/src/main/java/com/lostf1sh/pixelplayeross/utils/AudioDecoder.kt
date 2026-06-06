package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

object AudioDecoder {

    private const val TIMEOUT_US = 1000L
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    suspend fun decodeToFloatArray(context: Context, uri: Uri, requiredSamples: Int): Result<FloatArray> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        runCatching {
            val activeExtractor = MediaExtractor().also { extractor = it }
            activeExtractor.setDataSource(context, uri, null)

            val trackIndex = findAudioTrack(activeExtractor)
            if (trackIndex == -1) {
                error("No audio track found in the file.")
            }
            activeExtractor.selectTrack(trackIndex)
            val format = activeExtractor.getTrackFormat(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val activeDecoder = MediaCodec.createDecoderByType(mime).also { decoder = it }
            activeDecoder.configure(format, null, null, 0)
            activeDecoder.start()

            val pcmData = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false

            while (!isEndOfStream && pcmData.size < requiredSamples) { // --- MODIFIED: stop condition ---
                val inputBufferIndex = activeDecoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = activeDecoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder input buffer was null, ending decode early")
                        activeDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                        continue
                    }
                    val sampleSize = activeExtractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        activeDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                    } else {
                        activeDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, activeExtractor.sampleTime, 0)
                        activeExtractor.advance()
                    }
                }

                var outputBufferIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = activeDecoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder output buffer was null, skipping chunk")
                        activeDecoder.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                        continue
                    }
                    pcmData.addAll(byteBufferToFloatArray(outputBuffer, format).asList())
                    activeDecoder.releaseOutputBuffer(outputBufferIndex, false)

                    // If we already have enough samples, exit the inner loop
                    if (pcmData.size >= requiredSamples) break

                    outputBufferIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            activeDecoder.stop()

            Timber.tag("AudioDecoder").d("Successfully decoded ${pcmData.size} samples.")

            // --- MODIFIED: pad with silence if the song is shorter than required ---
            if (pcmData.size < requiredSamples) {
                val padding = FloatArray(requiredSamples - pcmData.size) { 0f }
                pcmData.addAll(padding.asList())
            }

            // Return the array at the exact size
            pcmData.toFloatArray().copyOf(requiredSamples)
        }.also {
            // Release native resources on both success and failure paths.
            runCatching { decoder?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun byteBufferToFloatArray(buffer: ByteBuffer, format: MediaFormat): FloatArray {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        buffer.rewind()

        return when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shortBuffer = buffer.asShortBuffer()
                FloatArray(shortBuffer.remaining()) {
                    shortBuffer.get().toFloat() / Short.MAX_VALUE
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                FloatArray(floatBuffer.remaining()) { floatBuffer.get() }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }
}
