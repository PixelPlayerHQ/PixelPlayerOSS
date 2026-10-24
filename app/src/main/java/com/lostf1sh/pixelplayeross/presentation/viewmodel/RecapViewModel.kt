package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.stats.ListeningRecap
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.recap.RecapSchedule
import com.lostf1sh.pixelplayeross.presentation.recap.RecapSlide
import com.lostf1sh.pixelplayeross.presentation.recap.RecapUiModel
import com.lostf1sh.pixelplayeross.presentation.recap.RecapUiState
import com.lostf1sh.pixelplayeross.presentation.recap.buildRecapSlides
import com.lostf1sh.pixelplayeross.presentation.stats.resolveStatsArtistImages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds the recap story for the year in the `year` navigation argument. The story is a
 * snapshot: plays recorded while it is open do not reshuffle the slides under the reader.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val year: Int = savedStateHandle.get<Int>(YEAR_ARG) ?: LocalDate.now().year

    private val _uiState = MutableStateFlow<RecapUiState>(RecapUiState.Loading)
    val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

    /** The year's most played songs still in the library, most played first. */
    @Volatile
    var topSongsQueue: List<Song> = emptyList()
        private set

    init {
        viewModelScope.launch {
            try {
                load()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Exception) {
                Timber.e(throwable, "Failed to build the %d recap", year)
                _uiState.value = RecapUiState.NotEnoughData(year)
            }
        }
    }

    /**
     * Shows the story as soon as the listening data is in, with each artist's most played cover
     * standing in for their picture, then swaps the pictures in: those can wait on the network.
     */
    private suspend fun load() {
        val songs = withContext(dispatchers.io) { musicRepository.getAllSongsOnce() }
        val recap = playbackStatsRepository.loadRecap(year, songs)
        if (recap == null || recap.summary.totalDurationMs < RecapSchedule.MIN_RECAP_LISTENING_MS) {
            _uiState.value = RecapUiState.NotEnoughData(year)
            return
        }

        val songsById = songs.associateBy { it.id }
        topSongsQueue = recap.summary.songs.mapNotNull { songsById[it.songId] }

        // The recap ends today while the year is still running.
        val lastDay = LocalDate.ofYearDay(year, recap.daysInRange)
        val isSoFar = RecapSchedule.isSoFar(year, lastDay)
        val covers = recap.summary.topArtists
            .mapNotNull { artist -> recap.mostPlayedCoverOf(artist.artist, songsById)?.let { artist.artist to it } }
            .toMap()
        val model = buildModel(recap, isSoFar, covers)
        _uiState.value = RecapUiState.Ready(model)

        val shownArtists = model.slides
            .filterIsInstance<RecapSlide.TopArtists>()
            .singleOrNull()
            ?.artists
            ?.map { it.name }
            .orEmpty()
        if (shownArtists.isEmpty()) return
        val pictures = try {
            withContext(dispatchers.io) {
                resolveStatsArtistImages(shownArtists, musicRepository, artistImageRepository)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Exception) {
            // The story is already up with cover stand-ins; keep it rather than failing it.
            Timber.w(throwable, "Failed to load artist pictures for the %d recap", year)
            return
        }
        if (pictures.isNotEmpty()) {
            _uiState.value = RecapUiState.Ready(buildModel(recap, isSoFar, covers + pictures))
        }
    }

    private suspend fun buildModel(
        recap: ListeningRecap,
        isSoFar: Boolean,
        artistImages: Map<String, String>
    ): RecapUiModel {
        val slides = withContext(dispatchers.default) {
            buildRecapSlides(
                recap = recap,
                isSoFar = isSoFar,
                artistImages = artistImages,
                canPlayTopSongs = topSongsQueue.isNotEmpty()
            )
        }
        return RecapUiModel(year = year, isSoFar = isSoFar, slides = slides.toImmutableList())
    }

    companion object {
        /** Navigation argument holding the recap's calendar year. */
        const val YEAR_ARG = "year"
    }
}

/**
 * Cover of the most played track by [artistName], for artists without a picture of their own.
 * Matches the way stats split multi-artist songs into their separate artists.
 */
private fun ListeningRecap.mostPlayedCoverOf(artistName: String, songsById: Map<String, Song>): String? {
    val key = artistName.trim().lowercase(Locale.ROOT)
    return summary.songs.firstNotNullOfOrNull { played ->
        val cover = played.albumArtUri?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
        val song = songsById[played.songId] ?: return@firstNotNullOfOrNull null
        val songArtists = song.artists.map { it.name }.ifEmpty { listOf(song.displayArtist) }
        cover.takeIf { songArtists.any { it.trim().lowercase(Locale.ROOT) == key } }
    }
}
