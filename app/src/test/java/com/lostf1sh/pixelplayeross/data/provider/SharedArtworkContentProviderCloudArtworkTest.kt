package com.lostf1sh.pixelplayeross.data.provider

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.image.NavidromeCoilFetcher
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Cloud artwork is read synchronously by system media surfaces, including Bluetooth's AVRCP
 * target on its main thread, so the provider may only serve covers already on disk (issue #166).
 */
class SharedArtworkContentProviderCloudArtworkTest {

    @TempDir
    lateinit var cacheDir: File

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    // BitmapFactory isn't available on the JVM; covers are cached server-resized to 500 px.
    private val coverBounds: (File) -> ArtworkImageBounds? =
        { ArtworkImageBounds(500, 500) }

    @Test
    fun `cover that is not cached yet is not served`() {
        val resolved = SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12", coverBounds)

        assertThat(resolved).isNull()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `cover downloaded by the Navidrome fetcher is served from its cache file`() = runTest {
        val coverUri = mockk<Uri>(relaxed = true) {
            every { host } returns "al-12"
            every { getQueryParameter("size") } returns null
        }
        val repository = mockk<NavidromeRepository> {
            every { isLoggedIn } returns true
            every { getCoverArtUrl("al-12", 500) } returns "https://navidrome.example/rest/getCoverArt.view?id=al-12&size=500"
        }
        NavidromeCoilFetcher(coverUri, repository, clientReturning(pngBytes), cacheDir).fetch()

        val resolved = SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12", coverBounds)

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.readBytes()).isEqualTo(pngBytes)
    }

    @Test
    fun `cached Jellyfin cover is served`() {
        val cached = File(cacheDir, "jellyfin_cover_item-84_500.jpg").apply { writeBytes(pngBytes) }

        assertThat(SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "jellyfin_cover://item-84", coverBounds))
            .isEqualTo(cached)
    }

    @Test
    fun `cover requested at an explicit size is served from that size's cache file`() {
        File(cacheDir, "navidrome_cover_al-12_500.jpg").writeBytes(pngBytes)
        val cached = File(cacheDir, "navidrome_cover_al-12_300.jpg").apply { writeBytes(pngBytes) }

        assertThat(SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12?size=300", coverBounds))
            .isEqualTo(cached)
    }

    @Test
    fun `cached server error body is not served as artwork`() {
        File(cacheDir, "navidrome_cover_al-12_500.jpg")
            .writeText("""{"subsonic-response":{"status":"failed"}}""")

        assertThat(SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12", coverBounds))
            .isNull()
    }

    @Test
    fun `oversized cached cover is not served`() {
        File(cacheDir, "navidrome_cover_al-12_500.jpg").writeBytes(
            pngBytes + ByteArray(SharedArtworkContentProvider.MAX_SHARED_CLOUD_ARTWORK_BYTES.toInt())
        )

        assertThat(SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12", coverBounds))
            .isNull()
    }

    @Test
    fun `cover with oversized dimensions is not served even when small on disk`() {
        File(cacheDir, "navidrome_cover_al-12_500.jpg").writeBytes(pngBytes)
        val tooWide = SharedArtworkContentProvider.MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX + 1

        assertThat(
            SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12") {
                ArtworkImageBounds(tooWide, 400)
            }
        ).isNull()
    }

    @Test
    fun `cover whose dimensions can't be read is not served`() {
        File(cacheDir, "navidrome_cover_al-12_500.jpg").writeBytes(pngBytes)

        assertThat(
            SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "navidrome_cover://al-12") { null }
        ).isNull()
    }

    @Test
    fun `non-cloud artwork URI is not resolved against the cover cache`() {
        assertThat(SharedArtworkContentProvider.resolveCachedCloudArtwork(cacheDir, "https://example.com/cover.jpg", coverBounds))
            .isNull()
    }

    private fun clientReturning(body: ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("image/png".toMediaType()))
                .build()
        }
        .build()
}
