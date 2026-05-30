package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AudioDecoderPolicyTest {

    @Test
    fun selectPlatformDecoders_routesAlacToExtensionRenderer() {
        val decoders = listOf("c2.qti.alac.decoder", "c2.android.alac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_ALAC, decoders)

        assertThat(selected).isEmpty()
    }

    @Test
    fun selectPlatformDecoders_routesMidiToExtensionRenderer() {
        val decoders = listOf("platform-midi-decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_EXOPLAYER_MIDI, decoders)

        assertThat(selected).isEmpty()
    }

    @Test
    fun selectPlatformDecoders_preservesMedia3OrderForCoreFormats() {
        val decoders = listOf("c2.android.aac.decoder", "c2.qti.aac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_AAC, decoders)

        assertThat(selected).containsExactlyElementsIn(decoders).inOrder()
    }

    @Test
    fun isLikelyHardwareDecoder_marksSoftwareRenderersAsSoftware() {
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("OMX.google.aac.decoder")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.android.aac.decoder")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("ffmpegAudioRenderer")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("MidiRenderer(JSyn)")).isFalse()
    }

    @Test
    fun isLikelyHardwareDecoder_marksVendorCodecsAsHardware() {
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.qti.aac.decoder")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.qti.flac.decoder")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("OMX.qcom.audio.decoder.aac")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.sec.aac.decoder")).isTrue()
    }
}
