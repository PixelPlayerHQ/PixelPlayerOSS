package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.media.TagBpmReader
import com.lostf1sh.pixelplayeross.data.media.WaveformData
import com.lostf1sh.pixelplayeross.data.media.WaveformRepository
import com.lostf1sh.pixelplayeross.data.model.MixPreset
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.TransitionRule
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.repository.TransitionRepository
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import com.lostf1sh.pixelplayeross.data.service.player.MixEditing
import com.lostf1sh.pixelplayeross.data.service.player.MixPlanner
import com.lostf1sh.pixelplayeross.data.service.player.MixPresets
import com.lostf1sh.pixelplayeross.data.service.player.MixPreviewPlayer
import com.lostf1sh.pixelplayeross.data.service.player.MixProfile
import com.lostf1sh.pixelplayeross.data.service.player.TransitionAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class MixEditorUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val from: Song? = null,
    val to: Song? = null,
    val fromBpm: Float? = null,
    val toBpm: Float? = null,
    val fromWaveform: WaveformData? = null,
    val toWaveform: WaveformData? = null,
    /** Each lane's waveform is on its way (a first look is shown as soon as the songs are analyzed). */
    val fromWaveformLoading: Boolean = true,
    val toWaveformLoading: Boolean = true,
    val mix: MixTransition? = null,
    val isDirty: Boolean = false,
    val isSaved: Boolean = false,
    val canPreview: Boolean = false,
    val isPreviewPlaying: Boolean = false,
    /**
     * True while the two songs are being listened to so the automatic mix can be chosen from how
     * they start and end. The timeline waits (shows a loader) instead of showing a stand-in graph
     * that would then change by itself. Only for pairs with no saved mix.
     */
    val isAnalyzing: Boolean = false,
) {
    val windowOptions: List<MixPlanner.WindowOption>
        get() = from?.let { MixPlanner.windowOptions(fromBpm, it.duration) }.orEmpty()
}

@HiltViewModel
class MixEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val transitionRepository: TransitionRepository,
    private val waveformRepository: WaveformRepository,
    private val engine: DualPlayerEngine,
    private val transitionAnalyzer: TransitionAnalyzer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""
    private val fromId: String = savedStateHandle["fromId"] ?: ""
    private val toId: String = savedStateHandle["toId"] ?: ""

    private val _uiState = MutableStateFlow(MixEditorUiState())
    val uiState: StateFlow<MixEditorUiState> = _uiState.asStateFlow()

    private val preview = MixPreviewPlayer(context, viewModelScope)

    /**
     * Preview playhead, ms from the window start (negative in the lead-in); null when stopped.
     * Kept out of [uiState]: it changes every frame, and only the timeline's draw step reads it.
     */
    val previewPositionMs: StateFlow<Long?> = preview.positionMs

    /** The mix as last loaded or saved; [MixEditorUiState.isDirty] compares against it. */
    private var baseline: MixTransition? = null
    private var existingRuleId: Long = 0L

    /** The two songs' audio profiles once analyzed; the automatic mix is chosen from them. */
    private var profiles: Pair<MixProfile, MixProfile>? = null

    /** The pair's ordinary crossfade settings, if the row had any; saving a mix must leave them alone. */
    private var existingSettings: TransitionSettings? = null

    /** Whether the row already held a genuine crossfade setting for this pair (see [MixTransition.keepsPairSettings]). */
    private var keepsPairSettings = false

    init {
        load()
        viewModelScope.launch {
            preview.positionMs.map { it != null }.distinctUntilChanged().collect { playing ->
                _uiState.update { it.copy(isPreviewPlaying = playing) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val songs = musicRepository.getSongsByIdsOnce(listOf(fromId, toId)).associateBy { it.id }
            val from = songs[fromId]
            val to = songs[toId]
            if (from == null || to == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }

            val (fromBpm, toBpm) = withContext(Dispatchers.IO) { bpmOf(from) to bpmOf(to) }
            val existingRule = transitionRepository.getAllRulesForPlaylist(playlistId).first()
                .firstOrNull { it.fromTrackId == fromId && it.toTrackId == toId }
            existingRuleId = existingRule?.id ?: 0L
            existingSettings = existingRule?.settings
            keepsPairSettings = existingRule != null &&
                (existingRule.mix == null || existingRule.mix.keepsPairSettings)

            val stored = existingRule?.mix
            // Only an unsaved pair of local songs is analyzed; a saved mix is shown exactly as saved.
            val analyze = stored == null && isLocal(from) && isLocal(to)
            val initial = MixPlanner.fit(
                stored ?: MixPlanner.auto(from.duration, fromBpm, toBpm),
                from.duration,
                to.duration,
            )
            baseline = initial

            _uiState.update {
                it.copy(
                    isLoading = false,
                    from = from,
                    to = to,
                    fromBpm = fromBpm,
                    toBpm = toBpm,
                    mix = initial,
                    canPreview = isLocal(from) && isLocal(to),
                    isAnalyzing = analyze,
                )
            }

            refineAutoMix(from, to, fromBpm, toBpm, replaceMix = stored == null)

            // Both whole-song waveforms at once (decoding a song is slow the first time); each
            // lane shows its first look from the analysis meanwhile.
            loadFullWaveform(from.path, outgoing = true)
            loadFullWaveform(to.path, outgoing = false)
        }
    }

    private fun loadFullWaveform(path: String, outgoing: Boolean) {
        viewModelScope.launch {
            val full = waveformRepository.waveformForFile(path)
            _uiState.update { s ->
                // Keep the first look if the whole waveform could not be made.
                if (outgoing) s.copy(fromWaveform = full ?: s.fromWaveform, fromWaveformLoading = false)
                else s.copy(toWaveform = full ?: s.toWaveform, toWaveformLoading = false)
            }
        }
    }

    // --- Edits -----------------------------------------------------------------------------

    fun applyPreset(preset: MixPreset) = editMix(markCustom = false) { mix ->
        if (preset == MixPreset.CUSTOM) mix.copy(preset = MixPreset.CUSTOM) else MixPresets.apply(mix, preset)
    }

    fun selectWindow(option: MixPlanner.WindowOption) {
        val from = _uiState.value.from ?: return
        val to = _uiState.value.to ?: return
        editMix(markCustom = false) { MixEditing.withWindow(it, option, from.duration, to.duration) }
    }

    /** Slides the mix window along the outgoing song by [deltaMs]. */
    fun shiftOutgoing(deltaMs: Long) {
        val from = _uiState.value.from ?: return
        editMix(markCustom = false) { MixEditing.shiftOutPoint(it, deltaMs, from.duration) }
    }

    /** Slides the incoming song under the window by [deltaMs]. */
    fun shiftIncoming(deltaMs: Long) {
        val to = _uiState.value.to ?: return
        editMix(markCustom = false) { MixEditing.shiftInStart(it, deltaMs, to.duration) }
    }

    fun moveKeyframe(outgoing: Boolean, index: Int, t: Float, v: Float) = editMix { mix ->
        if (outgoing) mix.copy(outVolume = MixEditing.moveKeyframe(mix.outVolume, index, t, v))
        else mix.copy(inVolume = MixEditing.moveKeyframe(mix.inVolume, index, t, v))
    }

    fun addKeyframe(outgoing: Boolean, t: Float, v: Float) = editMix { mix ->
        if (outgoing) mix.copy(outVolume = MixEditing.addKeyframe(mix.outVolume, t, v))
        else mix.copy(inVolume = MixEditing.addKeyframe(mix.inVolume, t, v))
    }

    fun removeKeyframe(outgoing: Boolean, index: Int) = editMix { mix ->
        if (outgoing) mix.copy(outVolume = MixEditing.removeKeyframe(mix.outVolume, index))
        else mix.copy(inVolume = MixEditing.removeKeyframe(mix.inVolume, index))
    }

    fun resetToAuto() {
        val state = _uiState.value
        val from = state.from ?: return
        val to = state.to ?: return
        editMix(markCustom = false) {
            MixPlanner.fit(autoMix(from, state.fromBpm, state.toBpm), from.duration, to.duration) ?: it
        }
    }

    /** The automatic mix: chosen from the songs' audio once analyzed, from tempo alone until then. */
    private fun autoMix(from: Song, fromBpm: Float?, toBpm: Float?): MixTransition =
        profiles?.let { (outgoing, incoming) -> MixPlanner.smart(outgoing, incoming, fromBpm, toBpm).mix }
            ?: MixPlanner.auto(from.duration, fromBpm, toBpm)

    /**
     * Listens to both songs so the automatic mix (and "Reset to Auto") can be chosen from how they
     * start and end. When [replaceMix] is true (the pair has no saved mix) the plain automatic mix
     * held meanwhile is swapped for the refined one before the timeline is shown ([isAnalyzing]
     * covers the wait); a saved mix is left exactly as saved. If the songs can't be analyzed in
     * time, the plain automatic mix is kept.
     */
    private fun refineAutoMix(from: Song, to: Song, fromBpm: Float?, toBpm: Float?, replaceMix: Boolean) {
        viewModelScope.launch {
            try {
                val analyzed = withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                    coroutineScope {
                        val outgoingAnalysis = async { transitionAnalyzer.analysisFor(from.path) }
                        val incomingAnalysis = async { transitionAnalyzer.analysisFor(to.path) }
                        outgoingAnalysis.await() to incomingAnalysis.await()
                    }
                }
                val outgoingAnalysis = analyzed?.first ?: return@launch
                val incomingAnalysis = analyzed.second ?: return@launch
                profiles = outgoingAnalysis.profile to incomingAnalysis.profile

                // A first look at each lane while its whole-song waveform is still decoding: the
                // end of the outgoing song and the start of the incoming one are what the window shows.
                _uiState.update { s ->
                    s.copy(
                        fromWaveform = s.fromWaveform ?: outgoingAnalysis.tail,
                        toWaveform = s.toWaveform ?: incomingAnalysis.head,
                    )
                }
                if (!replaceMix) return@launch

                val smart = MixPlanner.fit(autoMix(from, fromBpm, toBpm), from.duration, to.duration)
                    ?: return@launch
                baseline = smart
                _uiState.update { it.copy(mix = smart) }
            } finally {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    private fun editMix(markCustom: Boolean = true, transform: (MixTransition) -> MixTransition) {
        // Not while the automatic mix is still being worked out: the edit would be replaced by it.
        if (_uiState.value.isAnalyzing) return
        stopPreview()
        _uiState.update { state ->
            val current = state.mix ?: return@update state
            val edited = transform(current).let { if (markCustom) MixEditing.custom(it) else it }
            state.copy(mix = edited, isDirty = edited != baseline, isSaved = false)
        }
    }

    // --- Save ------------------------------------------------------------------------------

    fun save() {
        val mix = _uiState.value.mix ?: return
        viewModelScope.launch {
            transitionRepository.saveRule(
                TransitionRule(
                    id = existingRuleId,
                    playlistId = playlistId,
                    fromTrackId = fromId,
                    toTrackId = toId,
                    // The mix is kept apart from ordinary crossfade settings: a pair that has one is
                    // played by its mix only, and these settings stay as they were (or default).
                    settings = existingSettings ?: TransitionSettings(),
                    mix = mix.copy(keepsPairSettings = keepsPairSettings),
                )
            )
            // Saving again must update this row, not insert a second one: pick its id up.
            existingRuleId = transitionRepository.getAllRulesForPlaylist(playlistId).first()
                .firstOrNull { it.fromTrackId == fromId && it.toTrackId == toId }?.id ?: existingRuleId
            baseline = mix
            _uiState.update { it.copy(isDirty = false, isSaved = true) }
        }
    }

    // --- Preview ---------------------------------------------------------------------------

    fun togglePreview() {
        if (preview.isPlaying) {
            stopPreview()
            return
        }
        val state = _uiState.value
        val mix = state.mix ?: return
        val from = state.from ?: return
        val to = state.to ?: return
        if (!state.canPreview || state.isAnalyzing) return
        // The main player would play over the preview.
        engine.masterPlayer.pause()
        preview.start(Uri.fromFile(File(from.path)), Uri.fromFile(File(to.path)), mix)
    }

    fun stopPreview() {
        if (preview.isPlaying) preview.stop()
    }

    override fun onCleared() {
        preview.release()
    }

    private fun bpmOf(song: Song): Float? =
        runCatching { TagBpmReader.readBpm(song.path) }.getOrNull()?.takeIf { it in 40f..220f }

    private fun isLocal(song: Song): Boolean = song.path.isNotBlank() && File(song.path).isFile

    fun consumeSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    private companion object {
        /** Listening to a pair's ends takes a second or two; past this the plain automatic mix is used. */
        const val ANALYSIS_TIMEOUT_MS = 5_000L
    }
}
