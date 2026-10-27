package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BitPerfectUsbOutputTest {

    private fun bitPerfect(encoding: Int, rate: Int = 96_000, channels: Int = 2) =
        MixerFormat(encoding, rate, channels, bitPerfect = true)

    @Test
    fun selection_widensFloatIntoTheSmallestIntegerContainer() {
        val offered = listOf(
            bitPerfect(C.ENCODING_PCM_32BIT),
            bitPerfect(C.ENCODING_PCM_16BIT),
            bitPerfect(C.ENCODING_PCM_24BIT),
        )

        assertThat(selectBitPerfectMixerFormat(offered, 96_000, 2, C.ENCODING_PCM_FLOAT)).isEqualTo(2)
        assertThat(selectBitPerfectMixerFormat(offered.take(2), 96_000, 2, C.ENCODING_PCM_FLOAT)).isEqualTo(0)
    }

    @Test
    fun selection_neverNarrowsFloatTo16Bit() {
        val offered = listOf(bitPerfect(C.ENCODING_PCM_16BIT))

        assertThat(selectBitPerfectMixerFormat(offered, 96_000, 2, C.ENCODING_PCM_FLOAT)).isEqualTo(-1)
        assertThat(selectBitPerfectMixerFormat(offered, 96_000, 2, C.ENCODING_PCM_16BIT)).isEqualTo(0)
    }

    @Test
    fun selection_requiresBitPerfectBehaviourAndTheTracksRateAndChannels() {
        val offered = listOf(
            MixerFormat(C.ENCODING_PCM_24BIT, 96_000, 2, bitPerfect = false),
            bitPerfect(C.ENCODING_PCM_24BIT, rate = 48_000),
            bitPerfect(C.ENCODING_PCM_24BIT, channels = 1),
        )

        assertThat(selectBitPerfectMixerFormat(offered, 96_000, 2, C.ENCODING_PCM_FLOAT)).isEqualTo(-1)
        assertThat(selectBitPerfectMixerFormat(offered, 48_000, 2, C.ENCODING_PCM_FLOAT)).isEqualTo(1)
    }

    @Test
    fun widening_keepsEvery24BitFloatSampleExactInPackedLittleEndian() {
        val written = mutableListOf<ByteArray>()
        val widening = PcmWideningAudioOutput(acceptingOutput(written), C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT)
        val samples = intArrayOf(0, 1, -1, 8_388_607, -8_388_608, 0x123456)

        widening.write(floatBuffer(samples.map { it / 8_388_608f }), 1, 0)

        val bytes = written.single()
        val decoded = (samples.indices).map { i ->
            (bytes[3 * i + 2].toInt() shl 16) or
                ((bytes[3 * i + 1].toInt() and 0xFF) shl 8) or
                (bytes[3 * i].toInt() and 0xFF)
        }
        assertThat(decoded).containsExactlyElementsIn(samples.toList()).inOrder()
    }

    @Test
    fun widening_shifts16BitInto32BitAndClampsFullScaleFloat() {
        val written = mutableListOf<ByteArray>()
        PcmWideningAudioOutput(acceptingOutput(written), C.ENCODING_PCM_16BIT, C.ENCODING_PCM_32BIT)
            .write(shortBuffer(1.toShort(), Short.MIN_VALUE), 1, 0)
        PcmWideningAudioOutput(acceptingOutput(written), C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT)
            .write(floatBuffer(listOf(1f)), 1, 0)

        assertThat(ints(written[0])).containsExactly(1 shl 16, Int.MIN_VALUE).inOrder()
        assertThat(ints(written[1])).containsExactly(Int.MAX_VALUE)
    }

    @Test
    fun partialWrite_advancesTheSourceByWholeFramesAndReusesTheWidenedCopy() {
        val output = mockk<AudioOutput>(relaxed = true)
        val received = mutableListOf<ByteBuffer>()
        var calls = 0
        every { output.write(any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            received += buffer
            calls++
            // First call takes one 24-bit stereo frame (6 bytes), the second takes the rest.
            buffer.position(if (calls == 1) buffer.position() + 6 else buffer.limit())
            calls > 1
        }
        val widening = PcmWideningAudioOutput(output, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT)
        val source = floatBuffer(listOf(0.1f, 0.2f, 0.3f, 0.4f))

        assertThat(widening.write(source, 1, 0)).isFalse()
        assertThat(source.position()).isEqualTo(8) // one float stereo frame
        assertThat(widening.write(source, 1, 0)).isTrue()

        assertThat(source.hasRemaining()).isFalse()
        assertThat(received[1]).isSameInstanceAs(received[0])
    }

    private fun acceptingOutput(written: MutableList<ByteArray>): AudioOutput = mockk(relaxed = true) {
        every { write(any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            written += ByteArray(buffer.remaining()).also { buffer.get(it) }
            true
        }
    }

    private fun floatBuffer(values: List<Float>): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putFloat(it) }
            flip()
        }

    private fun shortBuffer(vararg values: Short): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putShort(it) }
            flip()
        }

    private fun ints(bytes: ByteArray): List<Int> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return List(bytes.size / 4) { buffer.getInt() }
    }
}
