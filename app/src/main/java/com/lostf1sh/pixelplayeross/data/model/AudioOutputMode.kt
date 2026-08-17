package com.lostf1sh.pixelplayeross.data.model

/**
 * Selects the Android audio output path used by the player.
 *
 * These modes describe how decoded PCM is handed to Android. No mode promises USB
 * exclusive or bit-perfect playback.
 */
enum class AudioOutputMode(val storageKey: String) {
    SYSTEM_DEFAULT("system_default"),
    DIRECT("direct"),
    PCM_FLOAT("pcm_float");

    val usesFloatOutput: Boolean
        get() = this == PCM_FLOAT

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
    }
}
