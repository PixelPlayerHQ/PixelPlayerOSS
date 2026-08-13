package com.lostf1sh.pixelplayeross.data.diagnostics

import android.os.Trace
import java.util.ArrayDeque

/**
 * Opt-in recorder for beta lag investigations.
 *
 * Normal users keep this disabled. Hot call sites should call [recordEvent] freely:
 * the disabled path is one volatile read and an immediate return.
 */
object AdvancedPerformanceDiagnostics {
    const val MAX_EVENTS = 120
    const val DEFAULT_SESSION_DURATION_MS = 24L * 60L * 60L * 1_000L
    const val FRAME_STALL_THRESHOLD_MS = 100L

    object EventTypes {
        const val USER_MARK = "user_mark"
        const val FRAME_STALL = "frame_stall"
        const val PLAYBACK = "playback"
        const val AUDIO_EFFECT = "audio_effect"
        const val OFFLOAD = "offload"
        const val WORKER = "worker"
        const val ARTWORK = "artwork"
        const val UI = "ui"
    }

    data class DiagnosticEvent(
        val elapsedRealtimeMs: Long,
        val type: String,
        val name: String,
        val details: Map<String, String> = emptyMap()
    )

    data class Snapshot(
        val enabled: Boolean,
        val sessionStartedEpochMs: Long?,
        val expiresAtEpochMs: Long?,
        val droppedEventCount: Long,
        val events: List<DiagnosticEvent>
    )

    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>(MAX_EVENTS)

    @Volatile
    private var enabled = false

    @Volatile
    private var sessionStartedEpochMs: Long? = null

    @Volatile
    private var expiresAtEpochMs: Long? = null

    private var droppedEventCount = 0L

    val isEnabled: Boolean
        get() = enabled

    fun startSession(
        startedAtEpochMs: Long = System.currentTimeMillis(),
        durationMs: Long = DEFAULT_SESSION_DURATION_MS
    ) {
        val expiresAt = startedAtEpochMs + durationMs.coerceAtLeast(1L)
        synchronized(lock) {
            events.clear()
            droppedEventCount = 0L
            sessionStartedEpochMs = startedAtEpochMs
            expiresAtEpochMs = expiresAt
            enabled = true
        }
    }

    fun configureSession(
        enabled: Boolean,
        startedAtEpochMs: Long?,
        expiresAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        synchronized(lock) {
            val active = enabled &&
                startedAtEpochMs != null &&
                expiresAtEpochMs != null &&
                nowEpochMs < expiresAtEpochMs
            if (!active) {
                clearLocked()
                return false
            }

            val sessionChanged = sessionStartedEpochMs != startedAtEpochMs ||
                this.expiresAtEpochMs != expiresAtEpochMs
            if (sessionChanged) {
                events.clear()
                droppedEventCount = 0L
            }
            sessionStartedEpochMs = startedAtEpochMs
            this.expiresAtEpochMs = expiresAtEpochMs
            this.enabled = true
            return true
        }
    }

    fun stopSession() {
        synchronized(lock) {
            clearLocked()
        }
    }

    fun recordEvent(
        type: String,
        name: String,
        details: Map<String, String> = emptyMap(),
        elapsedRealtimeMs: Long = android.os.SystemClock.elapsedRealtime(),
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!isActiveLocked(nowEpochMs)) {
                clearLocked()
                return
            }
            if (events.size >= MAX_EVENTS) {
                events.pollFirst()
                droppedEventCount += 1
            }
            events.addLast(
                DiagnosticEvent(
                    elapsedRealtimeMs = elapsedRealtimeMs,
                    type = type.take(MAX_FIELD_CHARS),
                    name = name.take(MAX_FIELD_CHARS),
                    details = details.sanitizeDetails()
                )
            )
        }
    }

    inline fun recordEventIfEnabled(
        type: String,
        name: String,
        elapsedRealtimeMs: Long? = null,
        details: () -> Map<String, String> = { emptyMap() }
    ) {
        if (!isEnabled) return
        if (elapsedRealtimeMs == null) {
            recordEvent(type = type, name = name, details = details())
        } else {
            recordEvent(
                type = type,
                name = name,
                details = details(),
                elapsedRealtimeMs = elapsedRealtimeMs
            )
        }
    }

    fun markLagNow(
        note: String? = null,
        elapsedRealtimeMs: Long = android.os.SystemClock.elapsedRealtime(),
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        recordEvent(
            type = EventTypes.USER_MARK,
            name = "lag_mark",
            details = note
                ?.takeIf { it.isNotBlank() }
                ?.let { mapOf("note" to it) }
                ?: emptyMap(),
            elapsedRealtimeMs = elapsedRealtimeMs,
            nowEpochMs = nowEpochMs
        )
    }

    fun <T> trace(sectionName: String, block: () -> T): T {
        if (!enabled) return block()
        Trace.beginSection(sectionName.take(MAX_TRACE_SECTION_CHARS))
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    suspend fun <T> traceSuspend(sectionName: String, block: suspend () -> T): T {
        if (!enabled) return block()
        Trace.beginSection(sectionName.take(MAX_TRACE_SECTION_CHARS))
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun snapshot(nowEpochMs: Long = System.currentTimeMillis()): Snapshot {
        synchronized(lock) {
            if (!isActiveLocked(nowEpochMs)) {
                clearLocked()
            }
            return Snapshot(
                enabled = enabled,
                sessionStartedEpochMs = sessionStartedEpochMs,
                expiresAtEpochMs = expiresAtEpochMs,
                droppedEventCount = droppedEventCount,
                events = events.toList()
            )
        }
    }

    fun resetForTest() {
        synchronized(lock) {
            clearLocked()
        }
    }

    private fun isActiveLocked(nowEpochMs: Long): Boolean =
        enabled && expiresAtEpochMs?.let { nowEpochMs < it } == true

    private fun clearLocked() {
        enabled = false
        sessionStartedEpochMs = null
        expiresAtEpochMs = null
        droppedEventCount = 0L
        events.clear()
    }

    private fun Map<String, String>.sanitizeDetails(): Map<String, String> {
        if (isEmpty()) return emptyMap()
        return entries
            .take(MAX_DETAIL_ENTRIES)
            .associate { (key, value) ->
                key.take(MAX_FIELD_CHARS) to value.take(MAX_DETAIL_VALUE_CHARS)
            }
    }

    private const val MAX_TRACE_SECTION_CHARS = 120
    private const val MAX_FIELD_CHARS = 80
    private const val MAX_DETAIL_VALUE_CHARS = 240
    private const val MAX_DETAIL_ENTRIES = 8
}
