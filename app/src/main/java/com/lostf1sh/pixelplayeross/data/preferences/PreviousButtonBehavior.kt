package com.lostf1sh.pixelplayeross.data.preferences

/**
 * Past this position the app's own previous controls restart the current track in
 * [PreviousButtonBehavior.RESTART_TRACK] mode instead of stepping back. System surfaces
 * (notification, headset keys, widget) keep Media3's shorter 3 s window in that mode.
 */
const val PREVIOUS_BUTTON_RESTART_THRESHOLD_MS = 10_000L

/** What pressing "previous" does once a song has been playing for a while. */
enum class PreviousButtonBehavior(val storageKey: String) {
    /** Restart the current track past the first few seconds; press again to go back. */
    RESTART_TRACK("restart_track"),

    /** Always step back to the previous queue item. */
    PREVIOUS_TRACK("previous_track");

    companion object {
        val DEFAULT = RESTART_TRACK

        fun fromStorageKey(value: String?): PreviousButtonBehavior =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}

/**
 * Whether previous at [positionMs] restarts the current track because of this behavior alone,
 * regardless of whether there is a track to go back to.
 */
fun PreviousButtonBehavior.restartsAtPosition(positionMs: Long): Boolean =
    this == PreviousButtonBehavior.RESTART_TRACK && positionMs > PREVIOUS_BUTTON_RESTART_THRESHOLD_MS

/** Whether previous restarts the current track; with nothing to go back to it always does. */
fun PreviousButtonBehavior.shouldRestartCurrentTrack(positionMs: Long, hasPrevious: Boolean): Boolean =
    !hasPrevious || restartsAtPosition(positionMs)
