package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/** Volume-line shapes for each [MixPreset]. */
object MixPresets {

    /** Outgoing and incoming volume lines of a preset. */
    data class Lines(val outgoing: List<VolumeKeyframe>, val incoming: List<VolumeKeyframe>)

    /** [MixPreset.CUSTOM] has no shape of its own; it reads as AUTO until the user edits it. */
    fun lines(preset: MixPreset): Lines = when (preset) {
        MixPreset.AUTO, MixPreset.CUSTOM -> smoothCrossfade()
        // Outgoing song fades out in the first half, the incoming one fades in during the second.
        MixPreset.FADE -> Lines(
            outgoing = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 0f), VolumeKeyframe(1f, 0f)),
            incoming = listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(0.5f, 0f), VolumeKeyframe(1f, 1f)),
        )
        // Outgoing stays up and drops late while the incoming song builds slowly, then swells.
        MixPreset.RISE -> Lines(
            outgoing = listOf(VolumeKeyframe(0f, 1f), VolumeKeyframe(0.7f, 1f), VolumeKeyframe(1f, 0f)),
            incoming = listOf(VolumeKeyframe(0f, 0f), VolumeKeyframe(0.6f, 0.25f), VolumeKeyframe(1f, 1f)),
        )
        // Hard swap in the middle of the window.
        MixPreset.CUT -> Lines(
            outgoing = listOf(
                VolumeKeyframe(0f, 1f), VolumeKeyframe(0.5f, 1f),
                VolumeKeyframe(0.5f, 0f), VolumeKeyframe(1f, 0f),
            ),
            incoming = listOf(
                VolumeKeyframe(0f, 0f), VolumeKeyframe(0.5f, 0f),
                VolumeKeyframe(0.5f, 1f), VolumeKeyframe(1f, 1f),
            ),
        )
    }

    /** [mix] with the lines of [preset] applied. */
    fun apply(mix: MixTransition, preset: MixPreset): MixTransition {
        val l = lines(preset)
        return mix.copy(outVolume = l.outgoing, inVolume = l.incoming, preset = preset)
    }

    private fun smoothCrossfade(): Lines {
        // Few points keep the line easy to edit; interpolation between them is smooth enough.
        val steps = 4
        val s = (0..steps).map { i ->
            val t = i.toFloat() / steps
            t to ((1 - cos(PI * t)) / 2).toFloat()
        }
        return Lines(
            outgoing = s.map { (t, v) -> VolumeKeyframe(t, 1f - v) },
            incoming = s.map { (t, v) -> VolumeKeyframe(t, v) },
        )
    }
}

/** Builds and fits mix transitions. Pure; callers pass tempos and durations in. */
object MixPlanner {

    private const val BEATS_PER_BAR = 4
    private const val MAX_AUTO_WINDOW_MS = 30_000L
    private const val FALLBACK_WINDOW_MS = 8_000L

    /** Smallest playable window; matches the engine's minimum fade. */
    const val MIN_WINDOW_MS = 500L

    /** Silence kept after the window so the outgoing song never runs into its own end. */
    const val END_GUARD_MS = 150L

    /**
     * The transition used for a pair the user hasn't edited: the window sits at the end of the
     * outgoing song, as many bars as the tempos allow (16 beat-matchable, 8 close, 4 apart),
     * or 8 seconds when a tempo is unknown, with a smooth crossfade.
     */
    fun auto(trackDurationMs: Long, outgoingBpm: Float?, incomingBpm: Float?): MixTransition {
        val bpm = outgoingBpm?.takeIf { it > 0f }
        val (windowMs, bars) = if (bpm != null) {
            val barMs = BEATS_PER_BAR * 60_000.0 / bpm
            val bars = when (SmartCrossfadePlanner.compatibility(outgoingBpm, incomingBpm)) {
                BpmCompatibility.ALIGNED -> 16
                BpmCompatibility.COMPATIBLE -> 8
                else -> 4
            }
            (bars * barMs).roundToLong().coerceAtMost(MAX_AUTO_WINDOW_MS) to bars
        } else {
            FALLBACK_WINDOW_MS to null
        }
        val window = windowMs.coerceAtMost((trackDurationMs - END_GUARD_MS).coerceAtLeast(MIN_WINDOW_MS))
        return MixPresets.apply(
            MixTransition(
                outPointMs = (trackDurationMs - window).coerceAtLeast(0L),
                inStartMs = 0L,
                windowMs = window,
                bars = bars,
            ),
            MixPreset.AUTO,
        )
    }

    /** Why [smart] chose the transition it did (shown in logs; handy when a pair sounds odd). */
    enum class AutoReason { TEMPO_MATCHED, NATURAL_OUTRO, SOFT_INTRO, BOTH_PUNCHY, DEFAULT }

    /** A transition picked by [smart] and the reason for it. */
    data class AutoPlan(val mix: MixTransition, val reason: AutoReason)

    /**
     * The transition for a pair the user hasn't edited, chosen from how the two songs actually
     * start and end instead of one fixed fade:
     *  - tempos known and compatible: a whole number of bars, smooth crossfade;
     *  - the outgoing song already fades out: a [AutoReason.NATURAL_OUTRO]-long window over that
     *    fade, the incoming song building in (so the two fades don't double up);
     *  - the incoming song has a long soft intro: a longer smooth crossfade over the intro;
     *  - both songs hit hard at the join: a short fade down and up, rather than two loud songs
     *    on top of each other;
     *  - otherwise 8 seconds, smooth.
     * The window always ends where the outgoing music really ends (its trailing silence is
     * skipped) and the incoming song starts where its sound begins (its leading silence is too).
     */
    fun smart(
        outgoing: MixProfile,
        incoming: MixProfile,
        outgoingBpm: Float?,
        incomingBpm: Float?,
    ): AutoPlan {
        val compatibility = SmartCrossfadePlanner.compatibility(outgoingBpm, incomingBpm)
        val tempoMatched = compatibility == BpmCompatibility.ALIGNED || compatibility == BpmCompatibility.COMPATIBLE

        var bars: Int? = null
        val windowMs: Long
        val shape: MixPreset
        val reason: AutoReason
        when {
            tempoMatched -> {
                val barMs = BEATS_PER_BAR * 60_000.0 / outgoingBpm!!
                bars = if (compatibility == BpmCompatibility.ALIGNED) 16 else 8
                windowMs = (bars * barMs).roundToLong().coerceAtMost(MAX_AUTO_WINDOW_MS)
                shape = MixPreset.AUTO
                reason = AutoReason.TEMPO_MATCHED
            }
            outgoing.outroMs >= NATURAL_OUTRO_MS -> {
                windowMs = outgoing.outroMs.coerceIn(NATURAL_OUTRO_MS, MAX_OUTRO_WINDOW_MS)
                shape = MixPreset.RISE
                reason = AutoReason.NATURAL_OUTRO
            }
            incoming.introMs >= SOFT_INTRO_MS -> {
                windowMs = incoming.introMs.coerceIn(MIN_INTRO_WINDOW_MS, MAX_INTRO_WINDOW_MS)
                shape = MixPreset.AUTO
                reason = AutoReason.SOFT_INTRO
            }
            outgoing.outroMs < ABRUPT_END_MS && incoming.introMs < PUNCHY_INTRO_MS -> {
                windowMs = PUNCHY_WINDOW_MS
                shape = MixPreset.FADE
                reason = AutoReason.BOTH_PUNCHY
            }
            else -> {
                windowMs = FALLBACK_WINDOW_MS
                shape = MixPreset.AUTO
                reason = AutoReason.DEFAULT
            }
        }

        val musicEnd = outgoing.musicEndMs
        val window = windowMs.coerceAtMost((musicEnd - END_GUARD_MS).coerceAtLeast(MIN_WINDOW_MS))
        val base = MixTransition(
            outPointMs = (musicEnd - window).coerceAtLeast(0L),
            inStartMs = incoming.leadSilenceMs.coerceAtMost(MAX_LEAD_SKIP_MS),
            windowMs = window,
            bars = bars,
        )
        // Shown as "Auto" in the editor whatever shape was picked: the user didn't choose it.
        return AutoPlan(MixPresets.apply(base, shape).copy(preset = MixPreset.AUTO), reason)
    }

    private const val NATURAL_OUTRO_MS = 4_000L
    private const val MAX_OUTRO_WINDOW_MS = 12_000L
    private const val ABRUPT_END_MS = 1_500L
    private const val SOFT_INTRO_MS = 5_000L
    private const val MIN_INTRO_WINDOW_MS = 6_000L
    private const val MAX_INTRO_WINDOW_MS = 14_000L
    private const val PUNCHY_INTRO_MS = 2_000L
    private const val PUNCHY_WINDOW_MS = 4_000L

    /** Never skip more than this of an incoming song's leading silence: a long gap may be intentional. */
    private const val MAX_LEAD_SKIP_MS = 8_000L

    /** A window length the bars chip offers: [bars] when tempo is known, otherwise plain seconds. */
    data class WindowOption(val windowMs: Long, val bars: Int?)

    /** 4/8/16/32 bars (or seconds without a tempo) that fit inside the outgoing song. */
    fun windowOptions(outgoingBpm: Float?, outgoingDurationMs: Long): List<WindowOption> {
        val bpm = outgoingBpm?.takeIf { it > 0f }
        val maxWindow = outgoingDurationMs - END_GUARD_MS
        return listOf(4, 8, 16, 32)
            .map { n ->
                if (bpm != null) WindowOption((n * BEATS_PER_BAR * 60_000.0 / bpm).roundToLong(), n)
                else WindowOption(n * 1_000L, null)
            }
            .filter { it.windowMs in MIN_WINDOW_MS..maxWindow }
    }

    /**
     * Fits [mix] inside an outgoing song of [trackDurationMs] and, when known, an incoming song of
     * [incomingDurationMs]: the window can't be shorter than [MIN_WINDOW_MS] or run past the end of
     * either song, so it is shortened or moved earlier, and the incoming start is pulled back so
     * the incoming song lasts the whole window. Returns null when a song is too short for any mix.
     */
    fun fit(mix: MixTransition, trackDurationMs: Long, incomingDurationMs: Long? = null): MixTransition? {
        val usable = trackDurationMs - END_GUARD_MS
        if (usable < MIN_WINDOW_MS) return null
        var maxWindow = usable
        if (incomingDurationMs != null) {
            val incomingUsable = incomingDurationMs - END_GUARD_MS
            if (incomingUsable < MIN_WINDOW_MS) return null
            maxWindow = minOf(maxWindow, incomingUsable)
        }
        val window = mix.windowMs.coerceIn(MIN_WINDOW_MS, maxWindow)
        val outPoint = mix.outPointMs.coerceIn(0L, usable - window)
        // The incoming song plays from inStartMs for the whole window: it must have that much song.
        val inStart = if (incomingDurationMs != null) {
            mix.inStartMs.coerceIn(0L, (incomingDurationMs - END_GUARD_MS - window).coerceAtLeast(0L))
        } else {
            mix.inStartMs
        }
        return mix.copy(outPointMs = outPoint, inStartMs = inStart, windowMs = window)
    }
}
