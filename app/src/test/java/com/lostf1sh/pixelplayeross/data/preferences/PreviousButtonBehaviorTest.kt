package com.lostf1sh.pixelplayeross.data.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PreviousButtonBehaviorTest {

    @Test
    fun `storage keys round trip`() {
        PreviousButtonBehavior.entries.forEach { behavior ->
            assertThat(PreviousButtonBehavior.fromStorageKey(behavior.storageKey)).isEqualTo(behavior)
        }
    }

    @Test
    fun `missing or unknown storage key falls back to restarting the track`() {
        assertThat(PreviousButtonBehavior.DEFAULT).isEqualTo(PreviousButtonBehavior.RESTART_TRACK)
        assertThat(PreviousButtonBehavior.fromStorageKey(null)).isEqualTo(PreviousButtonBehavior.RESTART_TRACK)
        assertThat(PreviousButtonBehavior.fromStorageKey("")).isEqualTo(PreviousButtonBehavior.RESTART_TRACK)
        assertThat(PreviousButtonBehavior.fromStorageKey("skip")).isEqualTo(PreviousButtonBehavior.RESTART_TRACK)
    }

    @Test
    fun `restart track mode restarts only past the threshold`() {
        val behavior = PreviousButtonBehavior.RESTART_TRACK

        assertThat(behavior.restartsAtPosition(0L)).isFalse()
        assertThat(behavior.restartsAtPosition(PREVIOUS_BUTTON_RESTART_THRESHOLD_MS)).isFalse()
        assertThat(behavior.restartsAtPosition(PREVIOUS_BUTTON_RESTART_THRESHOLD_MS + 1)).isTrue()
    }

    @Test
    fun `previous track mode never restarts because of the position`() {
        val behavior = PreviousButtonBehavior.PREVIOUS_TRACK

        assertThat(behavior.restartsAtPosition(0L)).isFalse()
        assertThat(behavior.restartsAtPosition(PREVIOUS_BUTTON_RESTART_THRESHOLD_MS + 1)).isFalse()
        assertThat(behavior.restartsAtPosition(Long.MAX_VALUE)).isFalse()
        assertThat(behavior.shouldRestartCurrentTrack(positionMs = 120_000L, hasPrevious = true)).isFalse()
    }

    @Test
    fun `both modes restart when there is no previous track`() {
        PreviousButtonBehavior.entries.forEach { behavior ->
            assertThat(behavior.shouldRestartCurrentTrack(positionMs = 1_000L, hasPrevious = false)).isTrue()
            assertThat(behavior.shouldRestartCurrentTrack(positionMs = 60_000L, hasPrevious = false)).isTrue()
        }
    }

    @Test
    fun `restart track mode steps back early in the song`() {
        val behavior = PreviousButtonBehavior.RESTART_TRACK

        assertThat(behavior.shouldRestartCurrentTrack(positionMs = 5_000L, hasPrevious = true)).isFalse()
        assertThat(behavior.shouldRestartCurrentTrack(positionMs = 30_000L, hasPrevious = true)).isTrue()
    }
}
