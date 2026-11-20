package com.lostf1sh.pixelplayeross.data.model

import android.os.Build

/**
 * Selects the Android audio output path used by the player.
 *
 * These modes describe how decoded PCM is handed to Android. Only [BIT_PERFECT_USB] asks for
 * bit-perfect output, and only a USB DAC on a phone whose audio HAL supports it can deliver it.
 */
enum class AudioOutputMode(val storageKey: String) {
    SYSTEM_DEFAULT("system_default"),
    DIRECT("direct"),
    PCM_FLOAT("pcm_float"),
    BIT_PERFECT_USB("bit_perfect_usb");

    /** Float keeps hi-res samples exact until the bit-perfect output widens them for the DAC. */
    val usesFloatOutput: Boolean
        get() = this == PCM_FLOAT || this == BIT_PERFECT_USB

    /** Uses Media3's stock AudioSink/AudioTrack path. */
    val usesUnmodifiedMedia3AudioSink: Boolean
        get() = this == DIRECT

    companion object {
        fun fromStorageKey(
            value: String?,
            legacyHiFiEnabled: Boolean = false
        ): AudioOutputMode =
            entries.firstOrNull { it.storageKey == value }
                ?: if (legacyHiFiEnabled) PCM_FLOAT else SYSTEM_DEFAULT

        /** Preferred mixer attributes arrived in Android 14; the mode also needs float AudioTracks. */
        fun isBitPerfectUsbAvailable(sdkInt: Int = Build.VERSION.SDK_INT, pcmFloatSupported: Boolean): Boolean =
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && pcmFloatSupported
    }
}
