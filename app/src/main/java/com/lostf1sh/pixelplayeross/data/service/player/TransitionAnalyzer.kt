package com.lostf1sh.pixelplayeross.data.service.player

import android.util.LruCache
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.data.media.WaveformDecoder
import com.lostf1sh.pixelplayeross.data.media.WaveformRepository
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to the start and end of a local song and describes them as a [MixProfile], which is what
 * the automatic mix uses to pick a transition that suits the pair. Only the first and last
 * [HEAD_MS] / [TAIL_MS] are decoded (not the whole song), and the small result is remembered, so a
 * song costs a second or two of decoding once per process.
 *
 * Singleton because the playback service and the editor both ask for profiles and should share
 * what is already known. Cloud songs and anything the platform can't decode have no profile (null):
 * the automatic mix then falls back to tempo tags alone.
 */
@Singleton
class TransitionAnalyzer @Inject constructor() {

    private object Unavailable

    // Values are MixProfile or Unavailable (a remembered failure, so a bad file is tried once).
    private val cache = LruCache<String, Any>(CACHE_SIZE)

    // Decoding is CPU-heavy; a pair needs two songs at a time, never more.
    private val slots = Semaphore(MAX_PARALLEL_ANALYSES)

    /** Profile of [mediaItem]'s local file, or null when there isn't one. */
    suspend fun profileFor(mediaItem: MediaItem?): MixProfile? {
        val path = mediaItem?.mediaMetadata?.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return profileForFile(path)
    }

    suspend fun profileForFile(path: String): MixProfile? {
        val key = withContext(Dispatchers.IO) { cacheKey(path) } ?: return null
        cache.get(key)?.let { return it as? MixProfile }

        val profile = withContext(Dispatchers.IO) {
            slots.withPermit { analyze(path) { !isActive } }
        }
        // A cancelled analysis also returns null; don't remember that as a real failure.
        if (profile != null) {
            cache.put(key, profile)
        } else if (currentCoroutineContext().isActive) {
            cache.put(key, Unavailable)
        }
        return profile
    }

    private fun analyze(path: String, isCancelled: () -> Boolean): MixProfile? {
        val durationMs = WaveformDecoder.probeDurationMs(path) ?: return null
        val head = WaveformDecoder.decodeRange(
            path, WaveformRepository.BUCKET_MS, startMs = 0L, endMs = HEAD_MS, isCancelled = isCancelled,
        ) ?: return null
        val tail = WaveformDecoder.decodeRange(
            path,
            WaveformRepository.BUCKET_MS,
            startMs = (durationMs - TAIL_MS).coerceAtLeast(0L),
            endMs = Long.MAX_VALUE,
            isCancelled = isCancelled,
        ) ?: return null
        return MixProfileBuilder.build(durationMs, head, tail)
    }

    /** Null when [path] isn't a readable local file (e.g. a cloud stream). */
    private fun cacheKey(path: String): String? {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        return "$path|${file.length()}|${file.lastModified()}"
    }

    private companion object {
        const val HEAD_MS = 40_000L
        const val TAIL_MS = 40_000L
        const val CACHE_SIZE = 128
        const val MAX_PARALLEL_ANALYSES = 2
    }
}
