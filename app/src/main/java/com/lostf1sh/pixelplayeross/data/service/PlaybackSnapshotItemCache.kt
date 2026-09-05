package com.lostf1sh.pixelplayeross.data.service

/**
 * Keeps the immutable portion of a playback snapshot until the queue itself changes.
 * Advancing to another item only changes index/position, so rebuilding every item's metadata on
 * the player looper would add avoidable work to next/previous actions.
 */
internal class PlaybackSnapshotItemCache<T> {
    private var cachedItems: List<T>? = null

    fun getOrBuild(builder: () -> List<T>): List<T> {
        cachedItems?.let { return it }
        return builder().also { cachedItems = it }
    }

    fun invalidate() {
        cachedItems = null
    }
}
