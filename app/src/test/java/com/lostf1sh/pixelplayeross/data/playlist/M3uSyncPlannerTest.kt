package com.lostf1sh.pixelplayeross.data.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class M3uSyncPlannerTest {

    @Test
    fun `new app playlist exports and new file imports`() {
        assertThat(
            M3uSyncPlanner.decide(
                appHash = "app",
                fileHash = null,
                checkpoint = null,
            )
        ).isEqualTo(M3uSyncAction.EXPORT)
        assertThat(
            M3uSyncPlanner.decide(
                appHash = null,
                fileHash = "file",
                checkpoint = null,
            )
        ).isEqualTo(M3uSyncAction.IMPORT)
    }

    @Test
    fun `only changed side wins after checkpoint`() {
        val checkpoint = M3uSyncCheckpoint(appHash = "old", fileHash = "old")

        assertThat(M3uSyncPlanner.decide("new", "old", checkpoint))
            .isEqualTo(M3uSyncAction.EXPORT)
        assertThat(M3uSyncPlanner.decide("old", "new", checkpoint))
            .isEqualTo(M3uSyncAction.IMPORT)
        assertThat(M3uSyncPlanner.decide("old", "old", checkpoint))
            .isEqualTo(M3uSyncAction.NONE)
    }

    @Test
    fun `simultaneous changes never overwrite either side`() {
        val checkpoint = M3uSyncCheckpoint(appHash = "old-app", fileHash = "old-file")

        assertThat(M3uSyncPlanner.decide("new-app", "new-file", checkpoint))
            .isEqualTo(M3uSyncAction.CONFLICT)
    }

    @Test
    fun `matching simultaneous changes converge without a false conflict`() {
        val checkpoint = M3uSyncCheckpoint(appHash = "old-app", fileHash = "old-file")

        assertThat(M3uSyncPlanner.decide("same-new-state", "same-new-state", checkpoint))
            .isEqualTo(M3uSyncAction.NONE)
    }

    @Test
    fun `first link with different existing content is a conflict`() {
        assertThat(M3uSyncPlanner.decide("app", "file", checkpoint = null))
            .isEqualTo(M3uSyncAction.CONFLICT)
        assertThat(M3uSyncPlanner.decide("same", "same", checkpoint = null))
            .isEqualTo(M3uSyncAction.NONE)
    }
}
