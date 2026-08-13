package com.lostf1sh.pixelplayeross.data.model

/**
 * Selects the Android audio output path used by the player.
 *
 * These modes describe how decoded PCM is handed to Android. Neither mode promises USB
 * exclusive or bit-perfect playback.
 */
enum class AudioOutputMode(val storageKey: String) {
    SYSTEM_DEFAULT("system_default"),
    PCM_FLOAT("pcm_float");

    val usesFloatOutput: Boolean
        get() = this == PCM_FLOAT

    companion object {
        fun fromStorageKey(
            value: String?,
            legacyHiFiEnabled: Boolean = false
        ): AudioOutputMode =
            entries.firstOrNull { it.storageKey == value }
                ?: if (legacyHiFiEnabled) PCM_FLOAT else SYSTEM_DEFAULT
    }
}
