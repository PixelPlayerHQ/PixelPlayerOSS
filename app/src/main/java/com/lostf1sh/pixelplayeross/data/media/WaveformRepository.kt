package com.lostf1sh.pixelplayeross.data.media

import android.content.Context
import android.util.LruCache
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Waveforms for the mix editor (scoped to the editor's view model: nothing else uses it). A waveform
 * is decoded from the local file once, then served from memory and from a small cache file (keyed by path, size and modification time, so an edited
 * file is decoded again). Cloud songs and anything the platform can't decode resolve to null:
 * the editor then shows "Can't display waveform" and stays fully usable.
 */
@ViewModelScoped
class WaveformRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Unavailable

    private val cacheDir by lazy { File(context.cacheDir, CACHE_DIR_NAME) }

    // Values are WaveformData or Unavailable (a remembered failure, so we don't re-decode).
    private val memory = LruCache<String, Any>(MEMORY_CACHE_SIZE)
    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    // Decoding is CPU-heavy; the editor needs two songs at a time, never more.
    private val decodeSlots = Semaphore(MAX_PARALLEL_DECODES)

    /** Waveform of [mediaItem]'s local file, or null when there isn't one. */
    suspend fun waveformFor(mediaItem: MediaItem?): WaveformData? {
        val path = mediaItem?.mediaMetadata?.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return waveformForFile(path)
    }

    suspend fun waveformForFile(filePath: String): WaveformData? {
        val key = withContext(Dispatchers.IO) { cacheKey(filePath) } ?: return null

        cached(key)?.let { return it.asWaveform() }

        val lock = keyLocks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            // Another caller may have finished the same song while we waited.
            cached(key)?.let { return@withLock it.asWaveform() }

            val result = withContext(Dispatchers.IO) {
                readDisk(key) ?: decodeSlots.withPermit {
                    WaveformDecoder.decode(filePath, BUCKET_MS) { !isActive }
                        ?.also { writeDisk(key, it) }
                }
            }
            // A cancelled decode returns null too; don't remember that as a real failure.
            if (result != null) {
                memory.put(key, result)
            } else if (currentCoroutineContext().isActive) {
                memory.put(key, Unavailable)
            }
            result
        }
    }

    private fun cached(key: String): Any? = memory.get(key)

    private fun Any.asWaveform(): WaveformData? = this as? WaveformData

    /** Null when [filePath] isn't a readable local file (e.g. a cloud stream). */
    private fun cacheKey(filePath: String): String? {
        val file = File(filePath)
        if (!file.isFile || !file.canRead()) return null
        val digest = MessageDigest.getInstance("SHA-1").digest(filePath.toByteArray())
        val pathHash = digest.take(8).joinToString("") { "%02x".format(it) }
        return "${pathHash}_${file.length()}_${file.lastModified()}"
    }

    private fun readDisk(key: String): WaveformData? = runCatching {
        File(cacheDir, "$key.wf").takeIf { it.isFile }?.readBytes()?.let(WaveformData::decode)
    }.getOrNull()

    private fun writeDisk(key: String, data: WaveformData) {
        runCatching {
            cacheDir.mkdirs()
            val target = File(cacheDir, "$key.wf")
            // Write-then-rename so a crash never leaves a half file that decodes as garbage.
            val temp = File(cacheDir, "$key.tmp")
            temp.writeBytes(data.encode())
            if (!temp.renameTo(target)) temp.delete()
            pruneDisk()
        }.onFailure { Timber.w(it, "Could not cache waveform") }
    }

    private fun pruneDisk() {
        val files = cacheDir.listFiles { f -> f.extension == "wf" } ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - PRUNE_TARGET_DISK_FILES)
            .forEach { it.delete() }
    }

    companion object {
        /** One peak per this many milliseconds of audio. */
        const val BUCKET_MS = 50

        private const val CACHE_DIR_NAME = "waveforms"
        private const val MEMORY_CACHE_SIZE = 24
        private const val MAX_PARALLEL_DECODES = 2
        private const val MAX_DISK_FILES = 400
        private const val PRUNE_TARGET_DISK_FILES = 300
    }
}
