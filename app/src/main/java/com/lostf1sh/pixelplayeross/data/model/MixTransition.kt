package com.lostf1sh.pixelplayeross.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** Preset a [MixTransition] was built from. Any manual keyframe edit flips it to [CUSTOM]. */
enum class MixPreset { AUTO, CUSTOM, FADE, RISE, CUT }

/**
 * One point on a volume line. [t] is the position inside the mix window (0 = window start,
 * 1 = window end), [v] the volume multiplier at that point; both are in 0..1.
 */
data class VolumeKeyframe(val t: Float, val v: Float)

/**
 * A DJ-style transition between two consecutive songs of a mix playlist.
 *
 * @param outPointMs position in the outgoing song where the mix window starts.
 * @param inStartMs position in the incoming song at the moment the window starts.
 * @param windowMs length of the window; the outgoing song is silent after it.
 * @param bars window length in bars when it was chosen in bars (null = chosen in seconds).
 * @param outVolume volume line of the outgoing song, sorted by [VolumeKeyframe.t].
 * @param inVolume volume line of the incoming song, sorted by [VolumeKeyframe.t].
 */
data class MixTransition(
    val outPointMs: Long,
    val inStartMs: Long = 0L,
    val windowMs: Long,
    val bars: Int? = null,
    val outVolume: List<VolumeKeyframe> = DEFAULT_OUT_VOLUME,
    val inVolume: List<VolumeKeyframe> = DEFAULT_IN_VOLUME,
    val preset: MixPreset = MixPreset.AUTO,
    /**
     * True when the pair's row also holds a genuine, separately chosen crossfade setting (it had
     * one before the mix was authored). That setting is used again whenever the playlist is not a
     * mix. False for a row made only for the mix, whose placeholder settings must never be used.
     * Missing from older stored data, which reads as false.
     */
    val keepsPairSettings: Boolean = false,
) {
    /**
     * Returns this transition made safe to play: finite non-negative times, a window of at least
     * [MIN_WINDOW_MS], and volume lines that are sorted, clamped to 0..1, never empty and with
     * their end points fixed (see [pinEnds]).
     * Stored data (old backups, hand-edited JSON) goes through this before reaching the engine.
     */
    fun sanitized(): MixTransition = copy(
        outPointMs = outPointMs.coerceAtLeast(0L),
        inStartMs = inStartMs.coerceAtLeast(0L),
        windowMs = windowMs.coerceAtLeast(MIN_WINDOW_MS),
        bars = bars?.takeIf { it > 0 },
        outVolume = outVolume.sanitizedLine(DEFAULT_OUT_VOLUME).pinEnds(first = 1f, last = 0f),
        inVolume = inVolume.sanitizedLine(DEFAULT_IN_VOLUME).pinEnds(first = 0f, last = 1f),
    )

    companion object {
        const val MIN_WINDOW_MS = 500L

        val DEFAULT_OUT_VOLUME = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(1f, 0f))
        val DEFAULT_IN_VOLUME = listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(1f, 1f))

        private val gson: Gson = GsonBuilder().create()

        /** JSON stored in `transition_rules.mixJson`. */
        fun toJson(mix: MixTransition): String = gson.toJson(mix)

        /** Parses stored JSON; null for blank or corrupt input so callers fall back to Auto. */
        fun fromJson(json: String?): MixTransition? {
            if (json.isNullOrBlank()) return null
            // Defaults and cleanup are inside the guard too: parsed-but-malformed data (a null
            // keyframe, say) must fall back to an automatic mix, not throw when it is read.
            return runCatching {
                // Gson bypasses Kotlin defaults for fields missing from the JSON, leaving them null.
                gson.fromJson(json, MixTransition::class.java)
                    ?.fieldsOrDefaults()
                    ?.sanitized()
            }.getOrNull()
        }

        @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
        private fun MixTransition.fieldsOrDefaults(): MixTransition = copy(
            outVolume = if (outVolume == null) DEFAULT_OUT_VOLUME else outVolume,
            inVolume = if (inVolume == null) DEFAULT_IN_VOLUME else inVolume,
            preset = if (preset == null) MixPreset.CUSTOM else preset,
        )

        /**
         * The line's two ends sit on the window's edges and join the unmixed audio, so they are
         * fixed: the outgoing song starts at full volume and ends silent, the incoming one starts
         * silent and ends at full volume. A different end value would be a step in volume when the
         * window opens or closes (the engine plays the incoming song at full volume afterwards).
         */
        private fun List<VolumeKeyframe>.pinEnds(first: Float, last: Float): List<VolumeKeyframe> =
            toMutableList().also {
                it[0] = VolumeKeyframe(0f, first)
                it[it.lastIndex] = VolumeKeyframe(1f, last)
            }

        private fun List<VolumeKeyframe>.sanitizedLine(fallback: List<VolumeKeyframe>): List<VolumeKeyframe> {
            // Gson can put a null where the JSON has one, whatever the Kotlin type says.
            @Suppress("UNCHECKED_CAST")
            val cleaned = (this as List<VolumeKeyframe?>).filterNotNull()
                .filter { it.t.isFinite() && it.v.isFinite() }
                .map { VolumeKeyframe(it.t.coerceIn(0f, 1f), it.v.coerceIn(0f, 1f)) }
                .sortedBy { it.t }
            return if (cleaned.size >= 2) cleaned else fallback
        }
    }
}
