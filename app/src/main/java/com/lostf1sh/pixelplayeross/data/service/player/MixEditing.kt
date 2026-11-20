package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe

/** Pure edit operations behind the mix editor's gestures; each returns the edited value. */
object MixEditing {

    /** Keyframes closer than this in time are treated as the same spot. */
    const val MIN_KEYFRAME_GAP = 0.02f

    /**
     * Moves keyframe [index] to ([t], [v]); it can't cross its neighbours. The first and last
     * keyframes are fixed (they join the unmixed audio at the window's edges) and don't move.
     */
    fun moveKeyframe(line: List<VolumeKeyframe>, index: Int, t: Float, v: Float): List<VolumeKeyframe> {
        if (index !in line.indices || isEdge(line, index)) return line
        val lo = line[index - 1].t
        val hi = line[index + 1].t
        return line.toMutableList().also { it[index] = VolumeKeyframe(t.coerceIn(lo, hi), v.coerceIn(0f, 1f)) }
    }

    private fun isEdge(line: List<VolumeKeyframe>, index: Int) = index == 0 || index == line.lastIndex

    /** Inserts a keyframe at ([t], [v]); a no-op when one already sits within [MIN_KEYFRAME_GAP]. */
    fun addKeyframe(line: List<VolumeKeyframe>, t: Float, v: Float): List<VolumeKeyframe> {
        val time = t.coerceIn(0f, 1f)
        if (line.any { kotlin.math.abs(it.t - time) < MIN_KEYFRAME_GAP }) return line
        return (line + VolumeKeyframe(time, v.coerceIn(0f, 1f))).sortedBy { it.t }
    }

    /** Removes an interior keyframe; the two edge keyframes can't be removed. */
    fun removeKeyframe(line: List<VolumeKeyframe>, index: Int): List<VolumeKeyframe> {
        if (index <= 0 || index >= line.lastIndex) return line
        return line.toMutableList().also { it.removeAt(index) }
    }

    /**
     * Index of the movable (interior) keyframe within [maxDt] of [t] and [maxDv] of [v], nearest
     * first; -1 if none. The fixed end keyframes are never hit, so a touch there slides the song.
     */
    fun hitKeyframe(line: List<VolumeKeyframe>, t: Float, v: Float, maxDt: Float, maxDv: Float): Int {
        var best = -1
        var bestScore = Float.MAX_VALUE
        line.forEachIndexed { i, k ->
            if (isEdge(line, i)) return@forEachIndexed
            val dt = kotlin.math.abs(k.t - t) / maxDt
            val dv = kotlin.math.abs(k.v - v) / maxDv
            if (dt <= 1f && dv <= 1f) {
                val score = dt * dt + dv * dv
                if (score < bestScore) { best = i; bestScore = score }
            }
        }
        return best
    }

    /** Slides the window along the outgoing song, keeping the whole mix inside it. */
    fun shiftOutPoint(mix: MixTransition, deltaMs: Long, outgoingDurationMs: Long): MixTransition =
        (MixPlanner.fit(mix.copy(outPointMs = mix.outPointMs + deltaMs), outgoingDurationMs) ?: mix)

    /** Slides the incoming song under the window; it can't start before 0 or leave no room for the window. */
    fun shiftInStart(mix: MixTransition, deltaMs: Long, incomingDurationMs: Long): MixTransition {
        val maxStart = (incomingDurationMs - mix.windowMs - MixPlanner.END_GUARD_MS).coerceAtLeast(0L)
        return mix.copy(inStartMs = (mix.inStartMs + deltaMs).coerceIn(0L, maxStart))
    }

    /** Applies a new window length (e.g. from the bars chip) and refits it. */
    fun withWindow(mix: MixTransition, option: MixPlanner.WindowOption, outgoingDurationMs: Long): MixTransition {
        // Keep the window's end anchored: a longer window starts earlier, a shorter one later.
        val end = mix.outPointMs + mix.windowMs
        val resized = mix.copy(
            windowMs = option.windowMs,
            bars = option.bars,
            outPointMs = end - option.windowMs,
        )
        return MixPlanner.fit(resized, outgoingDurationMs) ?: mix
    }

    /** Marks [mix] as hand-edited. */
    fun custom(mix: MixTransition): MixTransition =
        if (mix.preset == MixPreset.CUSTOM) mix else mix.copy(preset = MixPreset.CUSTOM)
}
