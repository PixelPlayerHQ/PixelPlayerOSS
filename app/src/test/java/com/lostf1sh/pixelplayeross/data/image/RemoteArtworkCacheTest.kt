package com.lostf1sh.pixelplayeross.data.image

import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class RemoteArtworkCacheTest {

    @TempDir
    lateinit var cacheDir: File

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
    private val subsonicError =
        """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""

    @Test
    fun `subsonic error answered with HTTP 200 is not cached as artwork`() {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        assertThrows<IOException> {
            RemoteArtworkCache.download(clientReturning(subsonicError.toByteArray(), "application/json"), request(), target)
        }

        assertThat(target.exists()).isFalse()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `downloaded image replaces the cached file without leaving temp files`() {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")
        target.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1))

        val result = RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg"), request(), target)

        assertThat(target.readBytes()).isEqualTo(jpegBytes)
        assertThat(cacheDir.listFiles()!!.map { it.name }).containsExactly(target.name)
        assertThat(result.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `previously cached error body is evicted instead of served`() {
        val poisoned = File(cacheDir, "navidrome_cover_al-1_500.jpg").apply { writeText(subsonicError) }

        assertThat(RemoteArtworkCache.cachedResult(poisoned)).isNull()
        assertThat(poisoned.exists()).isFalse()
    }

    @Test
    fun `cached image is served from disk`() {
        val cached = File(cacheDir, "jellyfin_cover_item_500.jpg").apply { writeBytes(jpegBytes) }

        assertThat(RemoteArtworkCache.cachedResult(cached)).isNotNull()
        assertThat(cached.exists()).isTrue()
    }

    @Test
    fun `image signatures of formats servers return are recognized`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.ISO_8859_1)
        val avif = "\u0000\u0000\u0000\u001CftypAVIF".toByteArray(Charsets.ISO_8859_1)

        assertThat(RemoteArtworkCache.hasImageSignature(jpegBytes)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(png)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(webp)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(avif)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature("<?xml version".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature(ByteArray(0))).isFalse()
    }

    private fun request() = Request.Builder().url("https://music.example/rest/getCoverArt.view?id=al-1").build()

    private fun clientReturning(body: ByteArray, contentType: String) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", contentType)
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        }
        .build()
}
