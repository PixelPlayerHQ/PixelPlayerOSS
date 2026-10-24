package com.lostf1sh.pixelplayeross.data.image

import coil.decode.DataSource
import coil.fetch.SourceResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RemoteArtworkCacheTest {

    @TempDir
    lateinit var cacheDir: File

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val subsonicError =
        """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""

    @Test
    fun `subsonic error answered with HTTP 200 is not cached as artwork`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val error = runCatching {
            RemoteArtworkCache.download(clientReturning(subsonicError.toByteArray(), "application/json"), request(), target)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(target.exists()).isFalse()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `HTTP error status is not cached even when the body looks like an image`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val error = runCatching {
            RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg", code = 500), request(), target)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `downloaded image replaces an unusable cached file without leaving temp files`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")
        target.writeBytes(byteArrayOf(0, 1, 2))

        val result = RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg"), request(), target)

        assertThat(target.readBytes()).isEqualTo(jpegBytes)
        assertThat(cacheDir.listFiles()!!.map { it.name }).containsExactly(target.name)
        assertThat(result.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `PNG cover from a Gonic server is cached and then served from disk`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-12_500.jpg")

        val result = RemoteArtworkCache.download(clientReturning(pngBytes, "image/png"), request(), target)

        assertThat(result.mimeType).isEqualTo("image/png")
        assertThat(target.readBytes()).isEqualTo(pngBytes)
        assertThat(RemoteArtworkCache.cachedResult(target)).isNotNull()
    }

    @Test
    fun `previously cached error body is not served and the next download replaces it`() = runTest {
        val poisoned = File(cacheDir, "navidrome_cover_al-1_500.jpg").apply { writeText(subsonicError) }

        assertThat(RemoteArtworkCache.cachedResult(poisoned)).isNull()

        RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg"), request(), poisoned)
        assertThat(RemoteArtworkCache.cachedResult(poisoned)).isNotNull()
    }

    @Test
    fun `cached image is served from disk`() = runTest {
        val cached = File(cacheDir, "jellyfin_cover_item_500.jpg").apply { writeBytes(jpegBytes) }

        assertThat(RemoteArtworkCache.cachedResult(cached)).isNotNull()
        assertThat(cached.exists()).isTrue()
    }

    @Test
    fun `cancelling a cover download before the server answers cancels the HTTP call and caches nothing`() = runTest {
        val server = GatedCoverServer(pngBytes)
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val download = launch(Dispatchers.Default) { RemoteArtworkCache.download(server.client, request(), target) }
        assertThat(server.firstRequestStarted.await(5, TimeUnit.SECONDS)).isTrue()

        download.cancel()
        download.join()

        assertThat(server.firstCallCancelled.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(download.isCancelled).isTrue()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `cancelling while the cover body is streaming stops the read and caches nothing`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        StalledBodyCoverServer(image = pngBytes + ByteArray(8 * 1024), sentBytes = 1024).use { server ->
            val download = launch(Dispatchers.Default) {
                RemoteArtworkCache.download(server.client, server.request, target)
            }
            // The server has sent part of the cover and is holding back the rest, so the fetch
            // is now reading a body it cannot finish.
            assertThat(server.bodyBytesRead.await(5, TimeUnit.SECONDS)).isTrue()

            download.cancel()
            download.join()

            // The client drops the connection instead of waiting for the rest of the body, and
            // the callback that was reading it returns without writing anything.
            assertThat(server.clientHungUp.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(server.callsFinished.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(download.isCancelled).isTrue()
            assertThat(cacheDir.listFiles()).isEmpty()
        }
    }

    @Test
    fun `concurrent fetches of one cover download it once`() = runTest {
        val server = GatedCoverServer(pngBytes)
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val first = async(Dispatchers.Default) { RemoteArtworkCache.download(server.client, request(), target) }
        assertThat(server.firstRequestStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val second = startFetchWaitingForLock(server, target)
        server.releaseFirstResponse()

        assertThat(first.await().dataSource).isEqualTo(DataSource.NETWORK)
        assertThat(second.await().dataSource).isEqualTo(DataSource.DISK)
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(RemoteArtworkCache.fetchesQueuedFor(target)).isEqualTo(0)
    }

    @Test
    fun `fetch waiting on a cancelled download downloads the cover itself`() = runTest {
        val server = GatedCoverServer(pngBytes)
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val first = launch(Dispatchers.Default) { RemoteArtworkCache.download(server.client, request(), target) }
        assertThat(server.firstRequestStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val second = startFetchWaitingForLock(server, target)
        first.cancel()

        assertThat(second.await().dataSource).isEqualTo(DataSource.NETWORK)
        first.join()
        assertThat(server.firstCallCancelled.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(server.requestCount.get()).isEqualTo(2)
        assertThat(target.readBytes()).isEqualTo(pngBytes)
        assertThat(RemoteArtworkCache.fetchesQueuedFor(target)).isEqualTo(0)
    }

    @Test
    fun `image signatures of formats servers return are recognized`() {
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.ISO_8859_1)
        val avif = "\u0000\u0000\u0000\u001CftypAVIF".toByteArray(Charsets.ISO_8859_1)

        assertThat(RemoteArtworkCache.hasImageSignature(jpegBytes)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(pngBytes)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(webp)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(avif)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature("<?xml version".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature(ByteArray(0))).isFalse()
    }

    /**
     * Starts another fetch of [target] while the first one holds its download lock, and returns
     * only once that fetch is parked on the lock.
     *
     * UNDISPATCHED runs the fetch on this thread until it first suspends, so `async` returns
     * when the fetch blocks rather than whenever Default gets around to starting it. The
     * checks then pin down where it blocked: it shares the first fetch's lock, has not finished,
     * and has not sent a request of its own.
     */
    @Test
    fun `cache file for a cover URI matches the file its fetcher writes`() {
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "navidrome_cover://al-1"))
            .isEqualTo(File(cacheDir, "navidrome_cover_al-1_500.jpg"))
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "jellyfin_cover://item-2?size=300"))
            .isEqualTo(File(cacheDir, "jellyfin_cover_item-2_300.jpg"))
    }

    @Test
    fun `cache file is not named for unsupported or malformed cover URIs`() {
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "https://music.example/cover.jpg")).isNull()
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "navidrome_cover://")).isNull()
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "navidrome_cover://user@al-1")).isNull()
        assertThat(RemoteArtworkCache.cacheFileFor(cacheDir, "navidrome_cover://al-1:80")).isNull()
    }

    private fun CoroutineScope.startFetchWaitingForLock(server: GatedCoverServer, target: File): Deferred<SourceResult> {
        val fetch = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            RemoteArtworkCache.download(server.client, request(), target)
        }
        assertThat(RemoteArtworkCache.fetchesQueuedFor(target)).isEqualTo(2)
        assertThat(fetch.isCompleted).isFalse()
        assertThat(server.requestCount.get()).isEqualTo(1)
        return fetch
    }

    private fun request() = Request.Builder().url("https://music.example/rest/getCoverArt.view?id=al-1").build()

    private fun clientReturning(body: ByteArray, contentType: String, code: Int = 200) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .header("Content-Type", contentType)
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `cached image type is read from the file signature`() {
        assertThat(RemoteArtworkCache.imageMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
            .isEqualTo("image/jpeg")
        assertThat(RemoteArtworkCache.imageMimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
            .isEqualTo("image/png")
        assertThat(RemoteArtworkCache.imageMimeType("GIF89a".toByteArray())).isEqualTo("image/gif")
        assertThat(RemoteArtworkCache.imageMimeType("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
            .isEqualTo("image/webp")
        assertThat(RemoteArtworkCache.imageMimeType("\u0000\u0000\u0000\u001CftypavifX".toByteArray()))
            .isEqualTo("image/avif")
        assertThat(RemoteArtworkCache.imageMimeType("\u0000\u0000\u0000\u001CftypheicX".toByteArray()))
            .isEqualTo("image/heif")
        assertThat(RemoteArtworkCache.imageMimeType("<?xml version".toByteArray())).isNull()
    }
}