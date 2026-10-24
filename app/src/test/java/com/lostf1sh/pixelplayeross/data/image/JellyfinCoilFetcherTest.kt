package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

class JellyfinCoilFetcherTest {

    @TempDir
    lateinit var cacheDir: File

    private val coverUri = mockk<Uri>(relaxed = true) {
        every { host } returns "item-1"
        every { getQueryParameter("size") } returns null
    }
    private val repository = mockk<JellyfinRepository> {
        every { isLoggedIn } returns true
        every { getImageUrl("item-1", 500) } returns "https://jellyfin.example/Items/item-1/Images/Primary?maxWidth=500"
        every { getAuthorizationHeader() } returns "MediaBrowser Token=\"token\""
    }

    @Test
    fun `cancelled fetch propagates the cancellation and aborts the cover download`() = runTest {
        val server = GatedCoverServer(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
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
}
