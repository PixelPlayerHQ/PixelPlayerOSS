package com.lostf1sh.pixelplayeross.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EqualizerAudioSinkTest {

    private val forwarded = mutableListOf<ByteBuffer>()
    private val delegate = mockk<AudioSink>(relaxed = true)
    private val settings = MutableStateFlow(AudioEffectsSettings())
    private val sink = EqualizerAudioSink(delegate, settings)

    @Test
    fun neutralSettings_forwardTheDecoderBufferItself() {
        acceptEveryBuffer()
        sink.configure(pcmFormat(C.ENCODING_PCM_16BIT), 0, null)
        val input = pcm16(1_000, -1_000)

        assertThat(sink.handleBuffer(input, 0, 1)).isTrue()

        assertThat(forwarded.single()).isSameInstanceAs(input)
    }

    @Test
    fun activeEffects_forwardAProcessedCopyAndConsumeTheInput() {
        acceptEveryBuffer()
        settings.value = AudioEffectsSettings(equalizerEnabled = true, bandLevelsDb = List(10) { -15 })
        sink.configure(pcmFormat(C.ENCODING_PCM_16BIT), 0, null)
        val input = pcm16(*ShortArray(512) { 10_000 })

        assertThat(sink.handleBuffer(input, 0, 1)).isTrue()

        assertThat(forwarded.single()).isNotSameInstanceAs(input)
        assertThat(forwarded.single().remaining()).isEqualTo(512 * 2)
        assertThat(input.hasRemaining()).isFalse()
    }

    @Test
    fun reusedOutputBuffer_acceptsAnInputLargerThanThePreviousOne() {
        acceptEveryBuffer()
        settings.value = AudioEffectsSettings(loudnessEnabled = true, loudnessGainMb = 600)
        sink.configure(pcmFormat(C.ENCODING_PCM_16BIT), 0, null)

        for (frames in listOf(512, 4, 64)) {
            assertThat(sink.handleBuffer(pcm16(*ShortArray(frames * 2) { 100 }), 0, 1)).isTrue()
            assertThat(forwarded.last().remaining()).isEqualTo(frames * 2 * 2)
        }
    }

    @Test
    fun rejectedBuffer_isRetriedWithTheSameProcessedCopy() {
        every { delegate.handleBuffer(any(), any(), any()) } answers {
            forwarded += firstArg<ByteBuffer>()
            forwarded.size > 1
        }
        settings.value = AudioEffectsSettings(loudnessEnabled = true, loudnessGainMb = 600)
        sink.configure(pcmFormat(C.ENCODING_PCM_16BIT), 0, null)
        val input = pcm16(100, 200, 300, 400)

        assertThat(sink.handleBuffer(input, 0, 1)).isFalse()
        val firstAttempt = bytesOf(forwarded[0])
        assertThat(sink.handleBuffer(input, 0, 1)).isTrue()

        assertThat(forwarded[1]).isSameInstanceAs(forwarded[0])
        assertThat(bytesOf(forwarded[1])).isEqualTo(firstAttempt)
    }

    @Test
    fun encodedInput_isNeverTouched() {
        acceptEveryBuffer()
        settings.value = AudioEffectsSettings(loudnessEnabled = true, loudnessGainMb = 1000)
        sink.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(2).setSampleRate(48_000).build(), 0, null)
        val input = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))

        sink.handleBuffer(input, 0, 1)

        assertThat(forwarded.single()).isSameInstanceAs(input)
    }

    @Test
    fun everyPcmEncoding_roundTripsBitExactlyWhenTheSignalIsUnchanged() {
        acceptEveryBuffer()
        // Widening leaves mono (L == R) content alone, so the active chain must reproduce it exactly.
        settings.value = AudioEffectsSettings(virtualizerEnabled = true, virtualizerStrength = 1000)
        val encodings = listOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_DOUBLE,
        )
        for (encoding in encodings) {
            forwarded.clear()
            sink.configure(pcmFormat(encoding), 0, null)
            val bytes = monoStereoFrames(encoding)

            sink.handleBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()), 0, 1)

            assertThat(bytesOf(forwarded.single())).isEqualTo(bytes)
        }
    }

    private fun acceptEveryBuffer() {
        every { delegate.handleBuffer(any(), any(), any()) } answers {
            forwarded += firstArg<ByteBuffer>()
            true
        }
    }

    private fun pcmFormat(encoding: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(encoding)
        .setChannelCount(2)
        .setSampleRate(48_000)
        .build()

    private fun pcm16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun pcm16(vararg samples: Int): ByteBuffer = pcm16(*ShortArray(samples.size) { samples[it].toShort() })

    /** Four stereo frames with identical channels, written raw in [encoding]'s own layout. */
    private fun monoStereoFrames(encoding: Int): ByteArray {
        val frameValues = listOf(0x12, -0x34, 0x56, -0x78)
        val out = java.io.ByteArrayOutputStream()
        for (value in frameValues) repeat(2) {
            val sample: ByteArray = when (encoding) {
                C.ENCODING_PCM_8BIT -> byteArrayOf((value + 128).toByte())
                C.ENCODING_PCM_16BIT -> le(value * 0x101, 2)
                C.ENCODING_PCM_16BIT_BIG_ENDIAN -> le(value * 0x101, 2).reversedArray()
                C.ENCODING_PCM_24BIT -> le(value * 0x10101, 3)
                C.ENCODING_PCM_24BIT_BIG_ENDIAN -> le(value * 0x10101, 3).reversedArray()
                C.ENCODING_PCM_32BIT -> le(value * 0x1010101, 4)
                C.ENCODING_PCM_32BIT_BIG_ENDIAN -> le(value * 0x1010101, 4).reversedArray()
                C.ENCODING_PCM_FLOAT ->
                    ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value / 200f).array()
                else -> ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putDouble(value / 200.0).array()
            }
            out.write(sample)
        }
        return out.toByteArray()
    }

    private fun le(value: Int, bytes: Int): ByteArray = ByteArray(bytes) { (value shr (8 * it)).toByte() }

    private fun bytesOf(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }
}
