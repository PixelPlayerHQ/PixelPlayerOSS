package com.lostf1sh.pixelplayeross.data.image

import androidx.annotation.VisibleForTesting
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Disk cache shared by the Navidrome and Jellyfin Coil fetchers.
 *
 * Only bodies that carry an image signature are cached: Subsonic servers answer failed
 * `getCoverArt` calls with HTTP 200 and a JSON/XML error, and caching that body would pin a
 * broken cover until the cache is cleared. Downloads land in a temp file that is renamed into
 * place, so a concurrent request never decodes a file another request is still writing.
 *
 * Coil runs these fetchers on the ImageLoader's CPU-sized Default dispatcher, which also
 * decodes every image in the app, so nothing here may block it. Disk reads move to IO, and a
 * download runs on OkHttp's own threads and is cancelled with the fetch: a cover whose list
 * item scrolled away no longer waits on a slow server (Gonic resizes covers on demand) while
 * other artwork queues behind it.
 */
internal object RemoteArtworkCache {

    private const val SIGNATURE_BYTES = 12

    /** Cover size the fetchers request when the cover URI carries no `size` parameter. */
    const val DEFAULT_COVER_SIZE_PX = 500

    private val COVER_SCHEMES = setOf("navidrome_cover", "jellyfin_cover")

    private val downloadLocks = HashMap<String, DownloadLock>()

    /** Name of the file the [scheme] fetcher caches cover [coverId] at [sizePx] in. */
    fun cacheFileName(scheme: String, coverId: String, sizePx: Int): String =
        "${scheme}_${coverId}_$sizePx.jpg"

    /**
     * The cache file the Navidrome/Jellyfin fetcher writes for [rawArtworkUri]
     * (`navidrome_cover://<id>` or `jellyfin_cover://<id>`, optionally with `?size=`), or null
     * when the URI is not a cloud cover URI this cache can name.
     */
    fun cacheFileFor(cacheDir: File, rawArtworkUri: String): File? {
        val scheme = rawArtworkUri.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme !in COVER_SCHEMES) return null
        val afterScheme = rawArtworkUri.substring(scheme.length + "://".length)
        val coverId = afterScheme.substringBefore('?').substringBefore('#').substringBefore('/')
        // Hosts with user info or a port would not match the id the fetcher reads from Uri.host.
        if (coverId.isBlank() || coverId.any { it == '@' || it == ':' || it == '\\' }) return null
        val sizePx = afterScheme.substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
            .split('&')
            .firstOrNull { it.startsWith("size=") }
            ?.removePrefix("size=")
            ?.toIntOrNull()
            ?: DEFAULT_COVER_SIZE_PX
        return File(cacheDir, cacheFileName(scheme, coverId, sizePx))
    }

    /** Whether [file] holds a cached image (not an empty file or a cached server error body). */
    fun isCachedImage(file: File): Boolean =
        file.isFile && file.length() > 0L && hasImageSignature(readSignature(file))

    suspend fun cachedResult(file: File): SourceResult? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() == 0L) return@withContext null
        // A body cached before this check existed is skipped, not deleted: the next successful
        // download renames over it, and deleting here could remove a fresh file another
        // request renamed into place after the signature was read.
        if (!hasImageSignature(readSignature(file))) return@withContext null
        SourceResult(
            source = ImageSource(file = file.absolutePath.toPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    /**
     * Downloads [file] unless another fetch finishes downloading it first.
     *
     * A cover often shows in several places at once (a song row per track of an album, the
     * album grid), and each one fetches it. Only one fetch downloads a given cover at a time;
     * the others wait and then read the file it cached. If the downloading fetch is cancelled,
     * the next one in line downloads the cover itself.
     */
    @Throws(IOException::class)
    suspend fun download(client: OkHttpClient, request: Request, file: File): SourceResult =
        withDownloadLock(file) {
            cachedResult(file) ?: fetchAndCache(client, request, file)
        }

    private class DownloadLock {
        val mutex = Mutex()
        var holders = 0
    }

    /** Number of fetches that hold or are waiting for [file]'s download lock. */
    @VisibleForTesting
    internal fun fetchesQueuedFor(file: File): Int =
        synchronized(downloadLocks) { downloadLocks[file.path]?.holders ?: 0 }

    private suspend fun <T> withDownloadLock(file: File, block: suspend () -> T): T {
        val key = file.path
        val lock = synchronized(downloadLocks) {
            downloadLocks.getOrPut(key, ::DownloadLock).apply { holders++ }
        }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            synchronized(downloadLocks) {
                if (--lock.holders == 0) downloadLocks.remove(key)
            }
        }
    }

    private suspend fun fetchAndCache(client: OkHttpClient, request: Request, file: File): SourceResult =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // After a cancel this is the call reporting it; resuming then is a no-op.
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    // The body is read here rather than after resuming, so a cancel that lands
                    // while it is still streaming aborts the read as well.
                    continuation.resumeWith(runCatching { response.use { cacheResponse(it, file) } })
                }
            })
        }

    private fun cacheResponse(response: Response, file: File): SourceResult {
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val bytes = response.body.bytes()
        if (!hasImageSignature(bytes)) {
            throw IOException("Response is not an image (Content-Type: ${response.header("Content-Type")})")
        }

        val tempFile = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            tempFile.writeBytes(bytes)
            if (!tempFile.renameTo(file)) throw IOException("Could not move artwork into $file")
        } finally {
            tempFile.delete()
        }

        return SourceResult(
            source = ImageSource(file = file.absolutePath.toPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = response.header("Content-Type") ?: "image/jpeg",
            dataSource = DataSource.NETWORK
        )
    }

    internal fun hasImageSignature(header: ByteArray): Boolean = imageMimeType(header) != null

    /** MIME type of the image cached in [file], read from its signature, or null if it is not an image. */
    fun imageMimeType(file: File): String? = imageMimeType(readSignature(file))

    internal fun imageMimeType(header: ByteArray): String? {
        fun matches(offset: Int, vararg expected: Int): Boolean =
            header.size >= offset + expected.size &&
                expected.indices.all { header[offset + it].toInt() and 0xFF == expected[it] }

        return when {
            matches(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            matches(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            matches(0, 0x47, 0x49, 0x46, 0x38) -> "image/gif" // GIF8
            matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp" // RIFF....WEBP
            // ISO-BMFF ftyp box; the major brand tells AVIF from HEIF.
            matches(4, 0x66, 0x74, 0x79, 0x70) ->
                if (matches(8, 0x61, 0x76, 0x69, 0x66) || matches(8, 0x61, 0x76, 0x69, 0x73)) "image/avif" else "image/heif"
            matches(0, 0x42, 0x4D) -> "image/bmp"
            else -> null
        }
    }

    private fun readSignature(file: File): ByteArray = runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(SIGNATURE_BYTES)
            var read = 0
            while (read < SIGNATURE_BYTES) {
                val count = input.read(buffer, read, SIGNATURE_BYTES - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}
