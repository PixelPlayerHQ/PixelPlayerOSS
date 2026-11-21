package com.lostf1sh.pixelplayeross.data.service.player

import android.util.LruCache
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.data.media.WaveformData
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
 * What listening to a song's ends found: its [profile], plus the loudness [head] (first 40 s) and
 * [tail] (last 40 s) it was read from. The editor draws these straight away, while the whole-song
 * waveform is still being decoded.
 */
class SongAnalysis(val profile: MixProfile, val head: WaveformData, val tail: WaveformData)

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

    // Values are SongAnalysis or Unavailable (a remembered failure, so a bad file is tried once).
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

    suspend fun profileForFile(path: String): MixProfile? = analysisFor(path)?.profile

    /** The full analysis of a local file, or null when there isn't one (cloud song, undecodable, silent). */
    suspend fun analysisFor(path: String): SongAnalysis? {
        val key = withContext(Dispatchers.IO) { cacheKey(path) } ?: return null
        cache.get(key)?.let { return it as? SongAnalysis }

        val analysis = withContext(Dispatchers.IO) {
            slots.withPermit { analyze(path) { !isActive } }
        }
        // A cancelled analysis also returns null; don't remember that as a real failure.
        if (analysis != null) {
            cache.put(key, analysis)
        } else if (currentCoroutineContext().isActive) {
            cache.put(key, Unavailable)
        }
        return analysis
    }

    private fun analyze(path: String, isCancelled: () -> Boolean): SongAnalysis? {
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
        val profile = MixProfileBuilder.build(durationMs, head, tail) ?: return null
        return SongAnalysis(profile, head, tail)
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
