package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.decode.DataSource
import coil.fetch.SourceResult
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
    fun `failed download leaves the cover unresolved`() = runTest {
        val failing = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Connection refused") }
            .build()

        val result = NavidromeCoilFetcher(coverUri, repository, failing, cacheDir).fetch()

        assertThat(result).isNull()
        assertThat(cacheDir.listFiles()).isEmpty()
    }
}
