package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerApiService
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerArtist
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerSearchResponse
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtistImageRepositoryTest {

    @Test
    fun `calculateCustomImageSampleSize keeps small bitmaps at full resolution`() {
        assertEquals(1, ArtistImageRepository.calculateCustomImageSampleSize(1024, 1024))
    }

    @Test
    fun `calculateCustomImageSampleSize aggressively downsamples oversized inputs`() {
        val sampleSize = ArtistImageRepository.calculateCustomImageSampleSize(12000, 8000)

        assertTrue(sampleSize >= 4)
        assertEquals(8, sampleSize)
    }

    @Test
    fun `cancelled prefetch does not mark artist as failed for the session`() = runTest {
        val deezerApiService = mockk<DeezerApiService>()
        val musicDao = mockk<MusicDao>()
        val repository = ArtistImageRepository(deezerApiService, musicDao, userPreferencesRepository())
        val firstAttemptStarted = CompletableDeferred<Unit>()
        val searchAttempts = AtomicInteger(0)
        val rawUrl = "https://cdn-images.dzcdn.net/images/artist/250x250-000000-80-0-0.jpg"
        val upgradedUrl = "https://cdn-images.dzcdn.net/images/artist/1000x1000-000000-80-0-0.jpg"

        coEvery { musicDao.getArtistIdByNormalizedName("Artist Name") } returns 42L
        coEvery { musicDao.getArtistImageUrl(42L) } returns null
        coEvery { musicDao.getArtistImageUrlByNormalizedName("Artist Name") } returns null
        coJustRun { musicDao.updateArtistImageUrl(42L, any()) }
        coEvery { deezerApiService.searchArtist("Artist Name", 1) } coAnswers {
            when (searchAttempts.incrementAndGet()) {
                1 -> {
                    firstAttemptStarted.complete(Unit)
                    awaitCancellation()
                }

                else -> DeezerSearchResponse(
                    data = listOf(
                        DeezerArtist(
                            id = 7L,
                            name = "Artist Name",
                            pictureBig = rawUrl
                        )
                    )
                )
            }
        }

        val prefetchJob = launch {
            repository.prefetchArtistImages(listOf(42L to "Artist Name"))
        }
        firstAttemptStarted.await()
        prefetchJob.cancel()
        prefetchJob.join()

        val imageUrl = repository.getArtistImageUrl("Artist Name", 42L)

        assertEquals(upgradedUrl, imageUrl)
        assertEquals(2, searchAttempts.get())
        coVerify(exactly = 1) { musicDao.updateArtistImageUrl(42L, upgradedUrl) }
    }

    @Test
    fun `disabled artist image lookup does not call deezer`() = runTest {
        val deezerApiService = mockk<DeezerApiService>(relaxed = true)
        val musicDao = mockk<MusicDao>(relaxed = true)
        val repository = ArtistImageRepository(
            deezerApiService,
            musicDao,
            userPreferencesRepository(externalArtistImagesEnabled = false)
        )

        val imageUrl = repository.getArtistImageUrl("Artist Name", 42L)

        assertEquals(null, imageUrl)
        coVerify(exactly = 0) { deezerApiService.searchArtist(any(), any()) }
    }

    private fun userPreferencesRepository(externalArtistImagesEnabled: Boolean = true): UserPreferencesRepository {
        return mockk {
            every { this@mockk.externalArtistImagesEnabledFlow } returns flowOf(externalArtistImagesEnabled)
        }
    }
}
