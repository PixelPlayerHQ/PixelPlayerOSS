package com.lostf1sh.pixelplayeross.data.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PlaybackSnapshotItemCacheTest {
    @Test
    fun `track advances reuse immutable queue items`() {
        val cache = PlaybackSnapshotItemCache<String>()
        var builds = 0

        val first = cache.getOrBuild { builds++; listOf("one", "two") }
        val second = cache.getOrBuild { builds++; listOf("replacement") }

        assertThat(second).isSameInstanceAs(first)
        assertThat(builds).isEqualTo(1)
    }

    @Test
    fun `queue mutation invalidates cached items`() {
        val cache = PlaybackSnapshotItemCache<String>()
        cache.getOrBuild { listOf("one") }

        cache.invalidate()

        assertThat(cache.getOrBuild { listOf("one", "two") })
            .containsExactly("one", "two")
            .inOrder()
    }
}
