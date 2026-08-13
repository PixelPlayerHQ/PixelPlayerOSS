package com.lostf1sh.pixelplayeross.data.diagnostics

import android.os.SystemClock
import android.view.Choreographer

/**
 * Records large frame gaps while advanced diagnostics are enabled.
 *
 * Must be started/stopped on the main thread because [Choreographer] is thread-confined.
 */
class MainThreadStallMonitor(
    private val thresholdMs: Long = AdvancedPerformanceDiagnostics.FRAME_STALL_THRESHOLD_MS
) {
    private var running = false
    private var lastFrameNanos = 0L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val previous = lastFrameNanos
            if (previous > 0L) {
                val gapMs = (frameTimeNanos - previous) / 1_000_000L
                if (gapMs >= thresholdMs) {
                    AdvancedPerformanceDiagnostics.recordEvent(
                        type = AdvancedPerformanceDiagnostics.EventTypes.FRAME_STALL,
                        name = "main_thread_frame_gap",
                        details = mapOf("gapMs" to gapMs.toString()),
                        elapsedRealtimeMs = SystemClock.elapsedRealtime()
                    )
                }
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        if (!running) return
        running = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}
