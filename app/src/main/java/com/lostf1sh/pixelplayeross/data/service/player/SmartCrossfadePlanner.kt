package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.Curve
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How the outgoing and incoming tracks' tempos relate, after octave folding. */
enum class BpmCompatibility {
    /** Tempos within ~4% of each other (or a half/double multiple) — beat-matchable. */
    ALIGNED,

    /** Tempos within ~10% — close enough for an extended musical blend, not beat-locked. */
    COMPATIBLE,

    /** Tempos too far apart; a long overlap would sound like two songs fighting. */
    INCOMPATIBLE,

    /** BPM missing for at least one side. */
    UNKNOWN,
}

/**
 * A tempo-informed transition, produced by [SmartCrossfadePlanner.plan]. All fields are final
 * values (already clamped against track duration and guard windows); the scheduler applies
 * them as-is.
 */
data class SmartCrossfadePlan(
    /** Settings to feed the engine — bar-quantized duration, smooth curves. */
    val settings: TransitionSettings,
    /** Absolute outgoing-track position (ms) at which to fire the crossfade. */
    val transitionPointMs: Long,
    val compatibility: BpmCompatibility,
    val outgoingBpm: Float,
    val incomingBpm: Float,
)

/**
 * Pure planning logic for the intelligent crossfade mode: given the tagged tempos of the
 * outgoing and incoming tracks, decides whether they are compatible and, if so, shapes the
 * crossfade so it breathes with the music — whole-bar fade lengths at the outgoing tempo,
 * with smooth S-curves on both sides.
 *
 * Never performs IO; callers pass BPM values obtained from `TrackBpmRepository`. When tempo
 * data is missing or the tempos clash, [plan] returns null and the caller keeps the standard
 * crossfade path — smart mode can only ever *refine* a transition, never degrade one.
 *
 * Tag-embedded BPM carries no beat-phase information, so the fade length is musically sized
 * but the fire point is not snapped onto a beat grid.
 */
object SmartCrossfadePlanner {

    /** Folded tempo ratio within ±4% of 1.0 counts as beat-matchable. */
    const val ALIGNED_TOLERANCE = 0.04f

    /** Folded tempo ratio within ±10% still earns an extended blend. */
    const val COMPATIBLE_TOLERANCE = 0.10f

    /** Hard ceiling for a smart fade; matches the crossfade duration preference cap. */
    const val MAX_SMART_FADE_MS = 12_000L

    /** Never lengthen a fade beyond this multiple of the user's configured duration. */
    const val MAX_BASE_DURATION_MULTIPLIER = 2.0f

    private const val BEATS_PER_BAR = 4

    /**
     * Folds `from/to` to the nearest octave (the DJ half/double-time trick): a 70 BPM ballad
     * against a 140 BPM track folds to 1.0. Result lies in [1/√2, √2].
     */
    fun foldedTempoRatio(fromBpm: Float, toBpm: Float): Float {
        val ratio = fromBpm / toBpm
        val octaves = (ln(ratio.toDouble()) / ln(2.0)).roundToInt()
        return (ratio / 2.0.pow(octaves)).toFloat()
    }

    fun compatibility(outgoingBpm: Float?, incomingBpm: Float?): BpmCompatibility {
        val fromBpm = outgoingBpm?.takeIf { it > 0f } ?: return BpmCompatibility.UNKNOWN
        val toBpm = incomingBpm?.takeIf { it > 0f } ?: return BpmCompatibility.UNKNOWN
        val distance = abs(foldedTempoRatio(fromBpm, toBpm) - 1f)
        return when {
            distance <= ALIGNED_TOLERANCE -> BpmCompatibility.ALIGNED
            distance <= COMPATIBLE_TOLERANCE -> BpmCompatibility.COMPATIBLE
            else -> BpmCompatibility.INCOMPATIBLE
        }
    }

    /**
     * Computes the smart transition for one track pair, or null when tempo data doesn't
     * support one (caller then uses its standard crossfade math unchanged).
     *
     * @param base resolved settings the plan refines (its duration is the user's intent).
     * @param trackDurationMs outgoing track duration as reported by the player.
     * @param minFadeMs / [guardWindowMs] the scheduler's existing floor and end-guard,
     *   passed in so plan output always respects the same invariants.
     */
    fun plan(
        base: TransitionSettings,
        trackDurationMs: Long,
        outgoingBpm: Float?,
        incomingBpm: Float?,
        minFadeMs: Long,
        guardWindowMs: Long,
    ): SmartCrossfadePlan? {
        val compat = compatibility(outgoingBpm, incomingBpm)
        if (compat != BpmCompatibility.ALIGNED && compat != BpmCompatibility.COMPATIBLE) return null
        if (trackDurationMs < minFadeMs + guardWindowMs) return null

        val fromBpm = outgoingBpm ?: return null
        val toBpm = incomingBpm ?: return null

        // Quantize the user's fade length UP to whole bars of the outgoing tempo, so the blend
        // spans a musical phrase instead of cutting mid-bar. Clamps keep it within the user's
        // intent (≤2× their setting), the preference ceiling, and the track's real end.
        val barMs = BEATS_PER_BAR * 60_000.0 / fromBpm
        val wholeBars = ceil(base.durationMs / barMs).toLong().coerceAtLeast(1L)
        val maxFadeMs = (trackDurationMs - guardWindowMs).coerceAtLeast(minFadeMs)
        val fadeMs = (wholeBars * barMs).roundToLong()
            .coerceAtMost((base.durationMs * MAX_BASE_DURATION_MULTIPLIER).roundToLong())
            .coerceAtMost(MAX_SMART_FADE_MS)
            .coerceAtMost(maxFadeMs)
            .coerceAtLeast(minFadeMs)

        return SmartCrossfadePlan(
            settings = base.copy(
                durationMs = fadeMs.toInt(),
                curveIn = Curve.S_CURVE,
                curveOut = Curve.S_CURVE,
            ),
            transitionPointMs = trackDurationMs - fadeMs,
            compatibility = compat,
            outgoingBpm = fromBpm,
            incomingBpm = toBpm,
        )
    }
}
