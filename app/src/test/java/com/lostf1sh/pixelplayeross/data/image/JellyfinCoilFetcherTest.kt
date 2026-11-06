package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.fetch.SourceResult
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class JellyfinCoilFetcherTest {

    @TempDir
    lateinit var cacheDir: File

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private val coverUri = coverUri("item-1")
    private val repository = mockk<JellyfinRepository> {
        every { isLoggedIn } returns true
        every { getImageUrl(any(), 500) } answers {
            "https://jellyfin.example/Items/${firstArg<String>()}/Images/Primary?maxWidth=500"
        }
        every { getAuthorizationHeader() } returns "MediaBrowser Token=\"token\""
        coEvery { getAlbumCoverItemId(any()) } returns null
    }

    /** A client for a server that can't be reached, like a LAN server with the VPN off. */
    private val unreachableRequests = AtomicInteger()
    private val unreachableClient = OkHttpClient.Builder()
        .addInterceptor {
            unreachableRequests.incrementAndGet()
            throw ConnectException("Failed to connect")
        }
        .build()

    @BeforeEach
    fun resetReachability() {
        JellyfinCoilFetcher.resetServerReachability()
    }

    private fun coverUri(itemId: String) = mockk<Uri>(relaxed = true) {
        every { host } returns itemId
        every { getQueryParameter("size") } returns null
    }

    private fun cacheCover(itemId: String, sizePx: Int = 500): File =
        File(cacheDir, RemoteArtworkCache.cacheFileName("jellyfin_cover", itemId, sizePx))
            .apply { writeBytes(png) }

    private fun SourceResult.cachedPath(): String = source.file().toString()

    @Test
    fun `cancelled fetch propagates the cancellation and aborts the cover download`() = runTest {
        val server = GatedCoverServer(png)
        var completedNormally = false

        // Coil runs fetchers on the ImageLoader's Default dispatcher.
        val fetch = launch(Dispatchers.Default) {
            JellyfinCoilFetcher(coverUri, repository, server.client, cacheDir).fetch()
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
    fun `cached song cover is served without the album fallback`() = runTest {
        val songCover = cacheCover("item-1")
        cacheCover("album-cover")
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "album-cover"

        val result = JellyfinCoilFetcher(coverUri, repository, unreachableClient, cacheDir).fetch()

        assertThat((result as SourceResult).cachedPath()).isEqualTo(songCover.absolutePath)
        assertThat(unreachableRequests.get()).isEqualTo(0)
        coVerify(exactly = 0) { repository.getAlbumCoverItemId(any()) }
    }

    @Test
    fun `uncached song cover falls back to the cached album cover when the server is unreachable`() = runTest {
        val albumCover = cacheCover("album-cover")
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "album-cover"

        val result = JellyfinCoilFetcher(coverUri, repository, unreachableClient, cacheDir).fetch()

        assertThat((result as SourceResult).cachedPath()).isEqualTo(albumCover.absolutePath)
        // The album cover is not stored as the song's own cover, so the song's art is
        // downloaded once the server is reachable again.
        assertThat(File(cacheDir, RemoteArtworkCache.cacheFileName("jellyfin_cover", "item-1", 500)).exists())
            .isFalse()
    }

    @Test
    fun `album fallback uses the default size when the requested size is not cached`() = runTest {
        val sizedUri = mockk<Uri>(relaxed = true) {
            every { host } returns "item-1"
            every { getQueryParameter("size") } returns "300"
        }
        every { repository.getImageUrl("item-1", 300) } returns "https://jellyfin.example/Items/item-1/Images/Primary?maxWidth=300"
        val albumCover = cacheCover("album-cover", 500)
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "album-cover"

        val result = JellyfinCoilFetcher(sizedUri, repository, unreachableClient, cacheDir).fetch()

        assertThat((result as SourceResult).cachedPath()).isEqualTo(albumCover.absolutePath)
    }

    @Test
    fun `no cover when neither the song nor its album cover is cached`() = runTest {
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "album-cover"

        val result = JellyfinCoilFetcher(coverUri, repository, unreachableClient, cacheDir).fetch()

        assertThat(result).isNull()
    }

    @Test
    fun `no fallback when the song is its own album cover`() = runTest {
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "item-1"

        val result = JellyfinCoilFetcher(coverUri, repository, unreachableClient, cacheDir).fetch()

        assertThat(result).isNull()
    }

    @Test
    fun `after an unreachable server further covers skip the network and use the album fallback`() = runTest {
        val albumCover = cacheCover("album-cover")
        coEvery { repository.getAlbumCoverItemId(any()) } returns "album-cover"

        JellyfinCoilFetcher(coverUri, repository, unreachableClient, cacheDir).fetch()
        assertThat(unreachableRequests.get()).isEqualTo(1)

        val result = JellyfinCoilFetcher(coverUri("item-2"), repository, unreachableClient, cacheDir).fetch()

        assertThat(unreachableRequests.get()).isEqualTo(1)
        assertThat((result as SourceResult).cachedPath()).isEqualTo(albumCover.absolutePath)
    }

    @Test
    fun `reachable server still downloads the song's own cover`() = runTest {
        cacheCover("album-cover")
        coEvery { repository.getAlbumCoverItemId("item-1") } returns "album-cover"
        val server = GatedCoverServer(png).apply { releaseFirstResponse() }

        val result = JellyfinCoilFetcher(coverUri, repository, server.client, cacheDir).fetch()

        val songCover = File(cacheDir, RemoteArtworkCache.cacheFileName("jellyfin_cover", "item-1", 500))
        assertThat((result as SourceResult).cachedPath()).isEqualTo(songCover.absolutePath)
        assertThat(songCover.readBytes()).isEqualTo(png)
    }
}
