package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.TransitionResolution
import com.lostf1sh.pixelplayeross.data.model.TransitionRule
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing transition rules and settings.
 * This is the single source of truth for transition-related data.
 */
interface TransitionRepository {

    /**
     * Resolves the effective transition settings for a given playback context.
     * It checks for rules in the following priority order:
     * 1. A specific rule for the given fromTrackId and toTrackId in the playlist.
     * 2. The default rule for the given playlistId.
     * 3. The global default settings from user preferences.
     *
     * Pass null track ids to skip the per-pair rule, e.g. when the queue no longer plays the
     * pair the way the playlist has it.
     */
    fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String?,
        toTrackId: String?
    ): Flow<TransitionResolution>

    /** Whether the playlist is a DJ-style mix. */
    fun isMixEnabled(playlistId: String): Flow<Boolean>

    /** Ids of every mix playlist; emits when any playlist is turned into a mix or back. */
    fun mixEnabledPlaylistIds(): Flow<Set<String>>

    suspend fun setMixEnabled(playlistId: String, enabled: Boolean)

    /**
     * The authored mix transition for a pair in a playlist, or null when the pair has none
     * (callers then build an Auto transition).
     */
    fun getMixTransition(playlistId: String, fromTrackId: String, toTrackId: String): Flow<MixTransition?>

    /**
     * Gets all rules defined for a specific playlist.
     */
    fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>>

    /**
     * Gets the default rule for a playlist. Does not fall back to global settings.
     * Returns a flow that may emit null if no specific default rule exists for the playlist.
     */
    fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?>

    /**
     * Saves a transition rule. If a rule for the given context
     * (playlistId, fromTrackId, toTrackId) already exists, it will be updated.
     * Otherwise, a new rule will be created.
     */
    suspend fun saveRule(rule: TransitionRule)

    /**
     * Deletes a specific rule by its unique ID.
     */
    suspend fun deleteRule(ruleId: Long)

    /**
     * Deletes the default transition rule for a given playlist.
     */
    suspend fun deletePlaylistDefaultRule(playlistId: String)

    /**
     * Gets the flow of global transition settings.
     */
    fun getGlobalSettings(): Flow<TransitionSettings>

    /**
     * Saves the global transition settings.
     */
    suspend fun saveGlobalSettings(settings: TransitionSettings)
}
