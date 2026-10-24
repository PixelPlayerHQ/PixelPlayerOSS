package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineExtension = MainCoroutineExtension(UnconfinedTestDispatcher())

    private val collapsedByDefault = MutableStateFlow(true)

    private val artistSongs = MutableStateFlow(
        album(10L, "Morning Static", 2019) + album(20L, "Low Tide", 2021) + album(30L, "Neon Ritual", 2023)
    )

    private val musicRepository = mockk<MusicRepository> {
        every { getArtistById(ArtistId) } returns flowOf(Artist(id = ArtistId, name = "Aurora Fields", songCount = 9))
        every { getSongsForArtist(ArtistId) } returns artistSongs
    }

    private val artistImageRepository = mockk<ArtistImageRepository> {
        coEvery { getEffectiveArtistImageUrl(any(), any()) } returns null
    }

    private val userPreferencesRepository = mockk<UserPreferencesRepository> {
        every { artistAlbumsCollapsedByDefaultFlow } returns collapsedByDefault
    }

    @Test
    fun `loaded state carries the collapsed default together with the album sections`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.albumSections.map { it.title })
            .containsExactly("Neon Ritual", "Low Tide", "Morning Static")
            .inOrder()
        assertThat(state.albumsCollapsedByDefault).isTrue()
        assertThat(viewModel.albumExpansionOverrides.value).isEmpty()
    }

    @Test
    fun `albums start expanded while the collapsed default is off`() {
        collapsedByDefault.value = false

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.albumsCollapsedByDefault).isFalse()
    }

    @Test
    fun `changing the setting does not re-fold a page that is already open`() {
        val viewModel = createViewModel()

        collapsedByDefault.value = false
        artistSongs.value = artistSongs.value.dropLast(1)

        assertThat(viewModel.uiState.value.albumsCollapsedByDefault).isTrue()
    }

    @Test
    fun `toggling one album records only that album`() {
        val viewModel = createViewModel()
        val lowTide = viewModel.sectionKey("Low Tide")

        viewModel.setAlbumSectionExpanded(lowTide, expanded = true)

        assertThat(viewModel.albumExpansionOverrides.value).containsExactly(lowTide, true)
    }

    @Test
    fun `collapse all and expand all set every album`() {
        val viewModel = createViewModel()
        val keys = viewModel.uiState.value.albumSections.map { it.collapseKey() }

        viewModel.setAllAlbumSectionsExpanded(true)
        assertThat(viewModel.albumExpansionOverrides.value).isEqualTo(keys.associateWith { true })

        viewModel.setAlbumSectionExpanded(keys.first(), expanded = false)
        viewModel.setAllAlbumSectionsExpanded(false)
        assertThat(viewModel.albumExpansionOverrides.value).isEqualTo(keys.associateWith { false })
    }

    @Test
    fun `removing the last song of an album drops its expansion choice`() {
        val viewModel = createViewModel()
        val morningStatic = viewModel.sectionKey("Morning Static")
        val lowTide = viewModel.sectionKey("Low Tide")
        viewModel.setAllAlbumSectionsExpanded(true)

        artistSongs.value.filter { it.albumId == 10L }.forEach { viewModel.removeSongFromAlbumSection(it.id) }

        assertThat(viewModel.uiState.value.albumSections.map { it.title }).doesNotContain("Morning Static")
        assertThat(viewModel.albumExpansionOverrides.value).doesNotContainKey(morningStatic)
        assertThat(viewModel.albumExpansionOverrides.value).containsEntry(lowTide, true)
    }

    @Test
    fun `a library update keeps the choices of albums that are still there`() {
        val viewModel = createViewModel()
        val neonRitual = viewModel.sectionKey("Neon Ritual")
        val lowTide = viewModel.sectionKey("Low Tide")
        viewModel.setAlbumSectionExpanded(neonRitual, expanded = true)
        viewModel.setAlbumSectionExpanded(lowTide, expanded = true)

        artistSongs.value = artistSongs.value.filterNot { it.albumId == 30L }

        assertThat(viewModel.albumExpansionOverrides.value).containsExactly(lowTide, true)
    }

    private fun createViewModel() = ArtistDetailViewModel(
        context = mockk(relaxed = true),
        musicRepository = musicRepository,
        artistImageRepository = artistImageRepository,
        userPreferencesRepository = userPreferencesRepository,
        themeStateHolder = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf("artistId" to ArtistId.toString()))
    )

    private fun ArtistDetailViewModel.sectionKey(title: String): String =
        uiState.value.albumSections.first { it.title == title }.collapseKey()

    private fun album(albumId: Long, title: String, year: Int): List<Song> = (1..3).map { track ->
        Song(
            id = "$albumId-$track",
            title = "$title $track",
            artist = "Aurora Fields",
            artistId = ArtistId,
            album = title,
            albumId = albumId,
            path = "/music/$albumId-$track.mp3",
            contentUriString = "content://media/external/audio/media/$albumId$track",
            albumArtUriString = null,
            duration = 180_000L,
            trackNumber = track,
            year = year,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
    }

    private companion object {
        const val ArtistId = 1L
    }
}
