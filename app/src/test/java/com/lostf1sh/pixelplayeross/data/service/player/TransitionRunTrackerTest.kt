package com.lostf1sh.pixelplayeross.data.service.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TransitionRunTrackerTest {

    @Test
    fun invalidatingForPlayerRebuild_makesActiveTransitionStale() {
        val tracker = TransitionRunTracker()
        val activeRun = tracker.start()

        tracker.invalidate()

        assertThat(tracker.isCurrent(activeRun)).isFalse()
    }

    @Test
    fun startingReplacementTransition_onlyKeepsLatestRunCurrent() {
        val tracker = TransitionRunTracker()
        val replacedRun = tracker.start()
        val replacementRun = tracker.start()

        assertThat(tracker.isCurrent(replacedRun)).isFalse()
        assertThat(tracker.isCurrent(replacementRun)).isTrue()
    }
}
