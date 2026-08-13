package com.lostf1sh.pixelplayeross.data.media

import android.util.LruCache
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tempo source for the smart crossfade: resolves a track's BPM from its embedded tag
 * (via [TagBpmReader]) and memoizes the result — including misses — so the transition
 * scheduler pays the file read at most once per track per process.
 *
 * Tag-only by design: no audio decoding or on-device analysis ever runs here, so a call
 * costs a handful of small file seeks on a cache miss and nothing at all afterwards.
 * Tracks without a readable local file path (cloud streams) or without a plausible tag
 * value simply resolve to null and the caller falls back to the standard crossfade.
 */
@Singleton
class TrackBpmRepository @Inject constructor() {

    private object NoBpm

    // Keyed by file path; values are Float (plausible tagged BPM) or NoBpm (negative cache).
    private val cache = LruCache<String, Any>(CACHE_SIZE)

    /** Tagged BPM for [mediaItem]'s local file, or null when unavailable. Never analyzes audio. */
    suspend fun bpmFor(mediaItem: MediaItem?): Float? {
        val filePath = mediaItem?.mediaMetadata?.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        cache.get(filePath)?.let { cached ->
            return cached as? Float
        }

        val bpm = withContext(Dispatchers.IO) {
            TagBpmReader.readBpm(filePath)
        }?.takeIf { it in PLAUSIBLE_TAG_BPM_RANGE }

        cache.put(filePath, bpm ?: NoBpm)
        return bpm
    }

    private companion object {
        const val CACHE_SIZE = 512

        // Implausible tag values (mis-tagged files, unit confusion) are treated as absent
        // rather than poisoning tempo-matching.
        val PLAUSIBLE_TAG_BPM_RANGE = 40f..220f
    }
}
