package com.lostf1sh.pixelplayeross.data.playlist

import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.media.TagBpmReader
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.playlist.nlp.LibraryIndex
import com.lostf1sh.pixelplayeross.data.playlist.nlp.PlaylistIntentEngine
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a natural-language description ("songs to lift weights to") into a ranked song list,
 * fully offline: it feeds the local library through [PlaylistIntentEngine]'s TF-IDF +
 * stemmed-synonym scorer. No network, no API keys.
 *
 * This is the thin orchestration layer around the pure `nlp` package: it loads the songs,
 * caches the inverted-frequency [LibraryIndex] across requests (rebuilt only when the library
 * signature changes), supplies play-count engagement stats, and — only when the query actually
 * expresses a tempo/energy preference — reads tag-embedded BPM from local files so tempo
 * matching can use real numbers. There is no on-device audio analysis: songs without a tagged
 * BPM simply fall back to the engine's genre-derived energy heuristic.
 */
@Singleton
class NlpPlaylistGenerator @Inject constructor(
    private val musicRepository: MusicRepository,
    private val dailyMixManager: DailyMixManager,
) {

    private val indexMutex = Mutex()
    @Volatile private var cachedIndex: LibraryIndex? = null

    // Tag BPM reads cost file I/O, so the whole-library sweep is memoized per library signature.
    private val bpmMutex = Mutex()
    @Volatile private var cachedBpm: Pair<Long, Map<String, Float>>? = null

    /**
     * Generates a playlist for [description], returning the matched songs in ranked order.
     * Heavy work (indexing, scoring, tag reads) runs off the main thread.
     */
    suspend fun generate(
        description: String,
        minLength: Int = DEFAULT_MIN_LENGTH,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): List<Song> = withContext(Dispatchers.Default) {
        val songs = musicRepository.getAllSongsOnce()
        if (songs.isEmpty()) return@withContext emptyList()

        val index = getOrBuildIndex(songs)

        val engagements = try {
            dailyMixManager.getAllEngagementStats()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading engagement stats")
            emptyMap()
        }

        // Tag BPM is only worth the file reads when the query cares about tempo/energy at all.
        val bpmBySongId = if (PlaylistIntentEngine.parse(description, index).hasEnergySignal) {
            readTagBpms(songs, index.signature)
        } else {
            emptyMap()
        }

        val resultIds = PlaylistIntentEngine.generate(
            query = description,
            minLength = minLength,
            maxLength = maxLength,
            index = index,
            engagements = engagements,
            bpmBySongId = bpmBySongId,
        )
        Timber.tag(TAG).d("NLP playlist: query='%s' -> %d songs", description, resultIds.size)

        val songsById = songs.associateBy { it.id }
        resultIds.mapNotNull { songsById[it] }
    }

    /** Cached inverted-frequency index, rebuilt only when the library signature changes. */
    private suspend fun getOrBuildIndex(songs: List<Song>): LibraryIndex {
        val signature = LibraryIndex.signatureOf(songs)
        cachedIndex?.let { if (it.signature == signature) return it }

        return indexMutex.withLock {
            cachedIndex?.let { if (it.signature == signature) return@withLock it }
            LibraryIndex.build(songs).also { cachedIndex = it }
        }
    }

    /**
     * Reads the tag-embedded BPM of every local song, with bounded parallelism, memoized per
     * library [signature]. Cloud-only songs (no local path) and files without a plausible
     * tagged value are simply absent from the map.
     */
    private suspend fun readTagBpms(songs: List<Song>, signature: Long): Map<String, Float> {
        cachedBpm?.let { (cachedSignature, map) -> if (cachedSignature == signature) return map }

        return bpmMutex.withLock {
            cachedBpm?.let { (cachedSignature, map) -> if (cachedSignature == signature) return@withLock map }

            val bpmBySongId = withContext(Dispatchers.IO) {
                val permits = Semaphore(BPM_READ_PARALLELISM)
                coroutineScope {
                    songs.mapNotNull { song ->
                        val path = song.path.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        async {
                            permits.withPermit {
                                val bpm = try {
                                    TagBpmReader.readBpm(path)
                                } catch (e: Exception) {
                                    Timber.tag(TAG).d(e, "Tag BPM read failed for %s", path)
                                    null
                                }?.takeIf { it in PLAUSIBLE_TAG_BPM_RANGE }
                                if (bpm != null) song.id to bpm else null
                            }
                        }
                    }.awaitAll().filterNotNull().toMap()
                }
            }

            cachedBpm = signature to bpmBySongId
            bpmBySongId
        }
    }

    companion object {
        private const val TAG = "NlpPlaylistGenerator"
        const val DEFAULT_MIN_LENGTH = 10
        const val DEFAULT_MAX_LENGTH = 20
        private const val BPM_READ_PARALLELISM = 8

        // Mirrors TrackBpmRepository: implausible tag values (mis-tagged files, unit
        // confusion) are treated as absent rather than poisoning tempo matching.
        private val PLAUSIBLE_TAG_BPM_RANGE = 40f..220f
    }
}
