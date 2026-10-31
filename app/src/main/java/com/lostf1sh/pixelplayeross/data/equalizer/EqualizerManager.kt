package com.lostf1sh.pixelplayeross.data.equalizer

import com.lostf1sh.pixelplayeross.data.preferences.EqualizerPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live effect settings for the software DSP. Every player's [EqualizerAudioSink] reads [settings]
 * on its playback thread, so changes apply to both crossfade players in any output mode, with no
 * audio session or device effect support involved.
 */
@Singleton
class EqualizerManager @Inject constructor(
    private val preferences: EqualizerPreferencesRepository
) {

    private val _settings = MutableStateFlow(AudioEffectsSettings())
    val settings: StateFlow<AudioEffectsSettings> = _settings.asStateFlow()

    private val restoreLock = Mutex()
    private var restored = false

    /**
     * Loads the stored settings once per process. Later calls are no-ops so a screen opening
     * cannot roll back changes that are still waiting to be persisted.
     */
    suspend fun restoreFromPreferences() = restoreLock.withLock {
        if (restored) return@withLock
        val presetName = preferences.equalizerPresetFlow.first()
        val bands = when (presetName) {
            "custom" -> preferences.equalizerCustomBandsFlow.first()
            else -> preferences.customPresetsFlow.first().find { it.name == presetName }?.bandLevels
                ?: EqualizerPreset.fromName(presetName).bandLevels
        }
        _settings.value = AudioEffectsSettings(
            equalizerEnabled = preferences.equalizerEnabledFlow.first(),
            bandLevelsDb = bands.toBands(),
            bassBoostEnabled = preferences.bassBoostEnabledFlow.first(),
            bassBoostStrength = preferences.bassBoostStrengthFlow.first(),
            virtualizerEnabled = preferences.virtualizerEnabledFlow.first(),
            virtualizerStrength = preferences.virtualizerStrengthFlow.first(),
            loudnessEnabled = preferences.loudnessEnhancerEnabledFlow.first(),
            loudnessGainMb = preferences.loudnessEnhancerStrengthFlow.first(),
        )
        restored = true
    }

    fun setEnabled(enabled: Boolean) = _settings.update { it.copy(equalizerEnabled = enabled) }

    fun setBandLevel(bandIndex: Int, level: Int) = _settings.update { current ->
        val bands = current.bandLevelsDb
        if (bandIndex !in bands.indices) return@update current
        val clamped = level.coerceIn(-EqualizerDsp.MAX_BAND_DB, EqualizerDsp.MAX_BAND_DB)
        current.copy(bandLevelsDb = bands.toMutableList().also { it[bandIndex] = clamped })
    }

    fun applyPreset(preset: EqualizerPreset) =
        _settings.update { it.copy(bandLevelsDb = preset.bandLevels.toBands()) }

    fun setBassBoostEnabled(enabled: Boolean) = _settings.update { it.copy(bassBoostEnabled = enabled) }

    fun setBassBoostStrength(strength: Int) =
        _settings.update { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }

    fun setVirtualizerEnabled(enabled: Boolean) = _settings.update { it.copy(virtualizerEnabled = enabled) }

    fun setVirtualizerStrength(strength: Int) =
        _settings.update { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) = _settings.update { it.copy(loudnessEnabled = enabled) }

    /** Loudness gain in millibels, 0..1000 (0..10 dB). */
    fun setLoudnessEnhancerStrength(strength: Int) =
        _settings.update { it.copy(loudnessGainMb = strength.coerceIn(0, 1000)) }

    /** Presets saved by older versions can have fewer bands; missing ones are flat. */
    private fun List<Int>.toBands(): List<Int> = List(EqualizerDsp.BAND_COUNT) { getOrElse(it) { 0 } }
}
