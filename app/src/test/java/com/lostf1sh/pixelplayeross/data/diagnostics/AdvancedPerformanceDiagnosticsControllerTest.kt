package com.lostf1sh.pixelplayeross.data.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AdvancedPerformanceDiagnosticsControllerTest {
    @Test
    fun `stall monitor runs only for an active foreground session`() {
        assertThat(shouldRunMainThreadStallMonitor(true, true)).isTrue()
        assertThat(shouldRunMainThreadStallMonitor(true, false)).isFalse()
        assertThat(shouldRunMainThreadStallMonitor(false, true)).isFalse()
        assertThat(shouldRunMainThreadStallMonitor(false, false)).isFalse()
    }
}
