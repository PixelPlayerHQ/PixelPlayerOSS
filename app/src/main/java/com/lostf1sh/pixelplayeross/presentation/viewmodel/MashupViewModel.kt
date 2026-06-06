package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.presentation.viewmodel.exts.DeckController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val stemWaveforms: Map<String, List<Int>> = emptyMap()
)

data class MashupUiState(
    val deck1: DeckState = DeckState(),
    val deck2: DeckState = DeckState(),
    val crossfaderValue: Float = 0f,
    val allSongs: List<Song> = emptyList(),
    val showSongPickerForDeck: Int? = null
)

@HiltViewModel
class MashupViewModel @Inject constructor(
    private val application: Application,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MashupUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var deck1Controller: DeckController
    private lateinit var deck2Controller: DeckController

    private var progressJob: Job? = null
    private var loadSongsForPickerJob: Job? = null

    init {
        initializeDecks()
    }

    private fun initializeDecks() {
        deck1Controller = DeckController(application)
        deck2Controller = DeckController(application)
    }

    fun loadSong(deck: Int, song: Song) {
        updateDeckState(deck) { it.copy(song = song) }
        val songUri = Uri.parse(song.contentUriString)
        val controller = if (deck == 1) deck1Controller else deck2Controller
        controller.loadSong(songUri)
        controller.player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateDeckState(deck) { it.copy(isPlaying = isPlaying) }
                syncProgressUpdater()
            }
        })
        closeSongPicker()
    }

    private fun updateDeckState(deck: Int, update: (DeckState) -> DeckState) {
        if (deck == 1) _uiState.update { it.copy(deck1 = update(it.deck1)) }
        else _uiState.update { it.copy(deck2 = update(it.deck2)) }
    }

    fun playPause(deck: Int) { if (deck == 1) deck1Controller.playPause() else deck2Controller.playPause() }
    fun seek(deck: Int, progress: Float) {
        if (deck == 1) deck1Controller.seek(progress) else deck2Controller.seek(progress)
        pushDeckProgress()
    }
    fun nudge(deck: Int, amountMs: Long) {
        if (deck == 1) deck1Controller.nudge(amountMs) else deck2Controller.nudge(amountMs)
        pushDeckProgress()
    }

    fun setVolume(deck: Int, volume: Float) {
        updateDeckState(deck) { it.copy(volume = volume.coerceIn(0f, 1f)) }
        updateCrossfaderAndVolumes()
    }

    fun onCrossfaderChange(value: Float) {
        _uiState.update { it.copy(crossfaderValue = value) }
        updateCrossfaderAndVolumes()
    }

    fun setSpeed(deck: Int, speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        if (deck == 1) deck1Controller.setSpeed(safeSpeed) else deck2Controller.setSpeed(safeSpeed)
        updateDeckState(deck) { it.copy(speed = safeSpeed) }
    }

    private fun updateCrossfaderAndVolumes() {
        val state = _uiState.value
        val vol1Multiplier = (1f - ((state.crossfaderValue + 1f) / 2f)).coerceIn(0f, 1f)
        val vol2Multiplier = ((state.crossfaderValue + 1f) / 2f).coerceIn(0f, 1f)

        deck1Controller.setDeckVolume(state.deck1.volume * vol1Multiplier)
        deck2Controller.setDeckVolume(state.deck2.volume * vol2Multiplier)
    }

    /**
     * Starts the progress ticker when at least one deck is playing and stops it
     * once neither deck is playing. Avoids the always-on 10Hz loop that drained
     * CPU/battery while both decks were idle (mirrors the subscription/playback
     * gating the rest of the app uses for its position ticker).
     */
    private fun syncProgressUpdater() {
        if (deck1Controller.player?.isPlaying == true || deck2Controller.player?.isPlaying == true) {
            startProgressUpdater()
        } else {
            stopProgressUpdater()
        }
    }

    private fun startProgressUpdater() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (isActive &&
                (deck1Controller.player?.isPlaying == true || deck2Controller.player?.isPlaying == true)
            ) {
                pushDeckProgress()
                delay(PROGRESS_TICK_MS)
            }
            progressJob = null
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
        // Emit a final snapshot so the slider lands on the paused/finished position.
        pushDeckProgress()
    }

    private fun pushDeckProgress() {
        val progress1 = deck1Controller.getProgress()
        val progress2 = deck2Controller.getProgress()
        updateDeckState(1) { if (it.progress == progress1) it else it.copy(progress = progress1) }
        updateDeckState(2) { if (it.progress == progress2) it else it.copy(progress = progress2) }
    }

    fun openSongPicker(deck: Int) {
        _uiState.update { it.copy(showSongPickerForDeck = deck) }
        loadSongsForPickerJob?.cancel()
        loadSongsForPickerJob = viewModelScope.launch {
            val songs = musicRepository.getAllSongsOnce()
            // Only publish if the picker is still open (it may have been dismissed).
            _uiState.update {
                if (it.showSongPickerForDeck != null) it.copy(allSongs = songs) else it
            }
        }
    }

    fun closeSongPicker() {
        loadSongsForPickerJob?.cancel()
        loadSongsForPickerJob = null
        _uiState.update { it.copy(showSongPickerForDeck = null, allSongs = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        deck1Controller.release()
        deck2Controller.release()
        progressJob?.cancel()
        loadSongsForPickerJob?.cancel()
    }

    private companion object {
        // Aligns with the slider tick used elsewhere in the app (>=250ms keeps the
        // slider smooth without the previous 100ms/10Hz battery drain).
        private const val PROGRESS_TICK_MS = 250L
    }
}
