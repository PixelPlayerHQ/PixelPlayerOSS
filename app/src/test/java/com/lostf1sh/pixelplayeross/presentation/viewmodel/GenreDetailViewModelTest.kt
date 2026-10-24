package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.model.Genre
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URLEncoder

@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(testDispatcher)

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val libraryGenres = listOf(
        Genre(id = "alternative", name = "Alternative", songCount = 5),
        Genre(id = "indie_rock", name = "Indie Rock", songCount = 2),
        Genre(id = "unknown", name = "Unknown", songCount = 1)
    )

    private val musicRepository = mockk<MusicRepository>(relaxed = true) {
        every { getGenres() } returns flowOf(libraryGenres)
        every { getMusicByGenre(any()) } returns flowOf(emptyList())
        every { getArtists(any()) } returns flowOf(emptyList())
    }

    @Test
    fun `a genre tile id opens that genre`() = runTest {
        val viewModel = createViewModel("indie_rock")

        assertEquals("Indie Rock", viewModel.uiState.value.genre?.name)
        verify { musicRepository.getMusicByGenre("Indie Rock") }
    }

    @Test
    fun `a song's whole multi-genre value opens its first genre`() = runTest {
        val viewModel = createViewModel("Alternative; Indie Rock")

        assertEquals("alternative", viewModel.uiState.value.genre?.id)
        verify { musicRepository.getMusicByGenre("Alternative") }
    }

    @Test
    fun `a genre name in other casing resolves to the library genre`() = runTest {
        val viewModel = createViewModel("INDIE ROCK")

        assertEquals("indie_rock", viewModel.uiState.value.genre?.id)
    }

    @Test
    fun `a genre missing from the library falls back to its first name`() = runTest {
        val viewModel = createViewModel("Shoegaze, Dream Pop")

        assertEquals("Shoegaze", viewModel.uiState.value.genre?.name)
        assertEquals("shoegaze", viewModel.uiState.value.genre?.id)
        verify { musicRepository.getMusicByGenre("Shoegaze") }
    }

    @Test
    fun `a placeholder genre value opens the Unknown bucket`() = runTest {
        val viewModel = createViewModel("<unknown>")

        assertEquals("unknown", viewModel.uiState.value.genre?.id)
        verify { musicRepository.getMusicByGenre("Unknown") }
    }

    @Test
    fun `a placeholder genre value lists untagged songs when the library has no Unknown bucket`() = runTest {
        every { musicRepository.getGenres() } returns flowOf(libraryGenres.filterNot { it.id == "unknown" })

        val viewModel = createViewModel("Unknown Genre")

        assertEquals("unknown", viewModel.uiState.value.genre?.id)
        assertEquals("Unknown", viewModel.uiState.value.genre?.name)
        verify { musicRepository.getMusicByGenre("Unknown") }
    }

    private fun createViewModel(genreArgument: String) = GenreDetailViewModel(
        musicRepository = musicRepository,
        savedStateHandle = SavedStateHandle(
            mapOf("genreId" to URLEncoder.encode(genreArgument, "UTF-8"))
        ),
        dispatchers = dispatchers
    )
}
