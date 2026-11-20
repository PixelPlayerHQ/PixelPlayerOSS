package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe

/**
 * Evaluates a mix volume line. Shared by the playback engine and the editor preview so what
 * the user auditions is exactly what plays.
 */
object MixEnvelope {

    /**
     * Volume (0..1) of [line] at [progress] (0 = window start, 1 = window end), linearly
     * interpolated between keyframes. [line] must be sorted by time. Two keyframes at the same
     * time make a hard step: the later one wins from that time on. An empty line is full volume.
     */
    fun volumeAt(line: List<VolumeKeyframe>, progress: Float): Float {
        if (line.isEmpty()) return 1f
        val p = progress.coerceIn(0f, 1f)
        if (p <= line.first().t) return line.first().v.coerceIn(0f, 1f)
        if (p >= line.last().t) return line.last().v.coerceIn(0f, 1f)

        for (i in 1 until line.size) {
            val next = line[i]
            if (p < next.t) {
                val prev = line[i - 1]
                val span = next.t - prev.t
                if (span <= 0f) return next.v.coerceIn(0f, 1f)
                val f = (p - prev.t) / span
                return (prev.v + (next.v - prev.v) * f).coerceIn(0f, 1f)
            }
        }
        return line.last().v.coerceIn(0f, 1f)
    }
}
