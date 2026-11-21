package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.decode.DataSource
import coil.fetch.SourceResult
import coil.size.Dimension
import coil.size.Size
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class NavidromeCoilFetcherTest {

    @TempDir
    lateinit var cacheDir: File

    private val coverUri = mockk<Uri>(relaxed = true) {
        every { host } returns "al-12"
        every { getQueryParameter("size") } returns null
    }
    private val repository = mockk<NavidromeRepository> {
        every { isLoggedIn } returns true
        every { getCoverArtUrl("al-12", 500) } returns "https://gonic.example/rest/getCoverArt.view?id=al-12&size=500"
    }

    @Test
    fun `cancelled fetch propagates the cancellation and aborts the cover download`() = runTest {
        val server = GatedCoverServer(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        var completedNormally = false

        // Coil runs fetchers on the ImageLoader's Default dispatcher.
        val fetch = launch(Dispatchers.Default) {
            NavidromeCoilFetcher(coverUri, repository, server.client, cacheDir).fetch()
            completedNormally = true
        }
        assertThat(server.firstRequestStarted.await(5, TimeUnit.SECONDS)).isTrue()

        fetch.cancel()
        fetch.join()

        assertThat(server.firstCallCancelled.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(completedNormally).isFalse()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `cached cover is served from disk without contacting the server`() = runTest {
        File(cacheDir, "navidrome_cover_al-12_500.jpg")
            .writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val unreachable = OkHttpClient.Builder()
            .addInterceptor { throw IOException("The server must not be contacted") }
            .build()

        val result = NavidromeCoilFetcher(coverUri, repository, unreachable, cacheDir).fetch()

        assertThat((result as SourceResult).dataSource).isEqualTo(DataSource.DISK)
    }

    @Test
    fun `now playing uses separate cached variants for requested sizes`() = runTest {
        val rawUri = "navidrome_cover://al-12"
        val originalUri = rawUri.withNavidromeCoverSize(0)
        val highUri = rawUri.withNavidromeCoverSize(800)
        val highCache = RemoteArtworkCache.cacheFileFor(cacheDir, highUri)!!
        val originalCache = RemoteArtworkCache.cacheFileFor(cacheDir, originalUri)!!
        assertThat(highCache).isNotEqualTo(originalCache)
        assertThat(highCache).isNotEqualTo(RemoteArtworkCache.cacheFileFor(cacheDir, rawUri))

        highCache.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val sizedCoverUri = mockk<Uri> {
            every { host } returns "al-12"
            every { getQueryParameter("size") } returns "800"
        }
        val unavailable = OkHttpClient.Builder()
            .addInterceptor { throw IOException("The server must not be contacted") }
            .build()

        val result = NavidromeCoilFetcher(sizedCoverUri, repository, unavailable, cacheDir).fetch()
        assertThat((result as SourceResult).dataSource).isEqualTo(DataSource.DISK)
    }

    @Test
    fun `size override preserves other options and leaves non-cloud sources alone`() {
        val rawUri = "navidrome_cover://al-12?size=500&other=yes#fragment"
        assertThat(rawUri.withNavidromeCoverSize(800))
            .isEqualTo("navidrome_cover://al-12?other=yes&size=800#fragment")
        val jellyfinUri = "jellyfin_cover://al-12"
        assertThat(jellyfinUri.withNavidromeCoverSize(800))
            .isEqualTo("jellyfin_cover://al-12?size=800")
        val localUri = "content://media/external/audio/albumart/1"
        assertThat(localUri.withNavidromeCoverSize(800)).isEqualTo(localUri)
    }

    @Test
    fun `resolveCloudCoverSize honors Coil requested size buckets and explicit query params`() {
        assertThat(resolveCloudCoverSize(explicitSizeParam = 0, requestedSize = Size(256, 256))).isEqualTo(0)
        assertThat(resolveCloudCoverSize(explicitSizeParam = 800, requestedSize = Size(1200, 1200))).isEqualTo(800)
        assertThat(resolveCloudCoverSize(explicitSizeParam = null, requestedSize = null)).isEqualTo(500)
        assertThat(resolveCloudCoverSize(explicitSizeParam = null, requestedSize = Size(256, 256))).isEqualTo(500)
        assertThat(resolveCloudCoverSize(explicitSizeParam = null, requestedSize = Size(640, 640))).isEqualTo(800)
        assertThat(resolveCloudCoverSize(explicitSizeParam = null, requestedSize = Size(1200, 900))).isEqualTo(0)
        assertThat(resolveCloudCoverSize(explicitSizeParam = null, requestedSize = Size.ORIGINAL)).isEqualTo(0)
        assertThat(
            resolveCloudCoverSize(
                explicitSizeParam = null,
                requestedSize = Size(Dimension.Undefined, Dimension.Pixels(1080))
            )
        ).isEqualTo(0)
    }

    @Test
    fun `failed download leaves the cover unresolved`() = runTest {
        val failing = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Connection refused") }
            .build()

        val result = NavidromeCoilFetcher(coverUri, repository, failing, cacheDir).fetch()

        assertThat(result).isNull()
        assertThat(cacheDir.listFiles()).isEmpty()
    }
}
