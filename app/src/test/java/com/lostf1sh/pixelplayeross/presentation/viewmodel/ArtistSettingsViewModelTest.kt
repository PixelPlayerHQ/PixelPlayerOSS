package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistSettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(UnconfinedTestDispatcher())

    private val albumsCollapsedByDefault = MutableStateFlow(false)

    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
        every { artistDelimitersFlow } returns flowOf(UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS)
        every { artistWordDelimitersFlow } returns flowOf(UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS)
        every { extractArtistsFromTitleFlow } returns flowOf(true)
        every { groupByAlbumArtistFlow } returns flowOf(false)
        every { artistAlbumsCollapsedByDefaultFlow } returns albumsCollapsedByDefault
        every { artistSettingsRescanRequiredFlow } returns flowOf(false)
        coEvery { setArtistAlbumsCollapsedByDefault(any()) } answers {
            albumsCollapsedByDefault.value = firstArg()
        }
    }

    private val syncManager = mockk<SyncManager>(relaxed = true) {
        every { isSyncing } returns flowOf(false)
    }

    @Test
    fun `the start collapsed switch follows the stored preference`() {
        val viewModel = ArtistSettingsViewModel(userPreferencesRepository, syncManager)
        assertThat(viewModel.uiState.value.albumsCollapsedByDefault).isFalse()

        albumsCollapsedByDefault.value = true

        assertThat(viewModel.uiState.value.albumsCollapsedByDefault).isTrue()
    }

    @Test
    fun `turning the start collapsed switch on stores it`() {
        val viewModel = ArtistSettingsViewModel(userPreferencesRepository, syncManager)

        viewModel.setAlbumsCollapsedByDefault(true)

        coVerify(exactly = 1) { userPreferencesRepository.setArtistAlbumsCollapsedByDefault(true) }
        assertThat(viewModel.uiState.value.albumsCollapsedByDefault).isTrue()
    }
}
