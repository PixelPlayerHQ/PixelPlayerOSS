package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
internal object AudioDecoderPolicy {
    private const val AUDIO_MIDI = "audio/midi"
    private val extensionOnlyMimeTypes = setOf(
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_EXOPLAYER_MIDI,
        AUDIO_MIDI
    )

    fun shouldUseExtensionRenderer(mimeType: String): Boolean {
        return extensionOnlyMimeTypes.any { it.equals(mimeType, ignoreCase = true) }
    }

    fun <T> selectPlatformDecoders(mimeType: String, decoderInfos: List<T>): List<T> {
        return if (shouldUseExtensionRenderer(mimeType)) {
            emptyList()
        } else {
            decoderInfos
        }
    }

    fun isLikelyHardwareDecoder(decoderName: String): Boolean {
        val normalized = decoderName.lowercase(Locale.US)
        val knownSoftwareTokens = listOf(
            "omx.google.",
            "c2.android.",
            "ffmpeg",
            "midi",
            "jsyn",
            "libgav1",
            "dav1d"
        )
        if (knownSoftwareTokens.any(normalized::contains)) return false

        return normalized.startsWith("omx.") ||
            normalized.startsWith("c2.") ||
            normalized.contains(".qti.") ||
            normalized.contains(".qcom.") ||
            normalized.contains(".sec.") ||
            normalized.contains(".mtk.") ||
            normalized.contains(".exynos.") ||
            normalized.contains(".dolby.")
    }
}
