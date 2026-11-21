package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.database.TransitionRuleEntity
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.TransitionResolution
import com.lostf1sh.pixelplayeross.data.model.TransitionRule
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.model.TransitionSource
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val transitionDao: TransitionDao,
    private val userPreferences: UserPreferencesRepository
) : TransitionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String?,
        toTrackId: String?
    ): Flow<TransitionResolution> {
        // Chain the lookups according to priority: specific -> playlist -> global
        if (fromTrackId == null || toTrackId == null) return resolvePlaylistDefault(playlistId)
        return transitionDao.getSpecificRule(playlistId, fromTrackId, toTrackId)
            .flatMapLatest { specificRule ->
                // A pair row made for a mix holds only placeholder settings: it is played while the
                // playlist is a mix and never stands in for a crossfade setting. A row that had a
                // genuine setting before its mix was authored keeps it for when the mix is off.
                if (specificRule != null && specificRule.holdsCrossfadeSetting()) {
                    flowOf(
                        TransitionResolution(
                            settings = specificRule.settings,
                            source = TransitionSource.PLAYLIST_SPECIFIC,
                        )
                    )
                } else {
                    resolvePlaylistDefault(playlistId)
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resolvePlaylistDefault(playlistId: String): Flow<TransitionResolution> =
        transitionDao.getPlaylistDefaultRule(playlistId).flatMapLatest { playlistRule ->
            if (playlistRule != null) {
                flowOf(
                    TransitionResolution(
                        settings = playlistRule.settings,
                        source = TransitionSource.PLAYLIST_DEFAULT,
                    )
                )
            } else {
                userPreferences.globalTransitionSettingsFlow.map { settings ->
                    TransitionResolution(
                        settings = settings,
                        source = TransitionSource.GLOBAL_DEFAULT,
                    )
                }
            }
        }

    override fun mixEnabledPlaylistIds(): Flow<Set<String>> = userPreferences.mixEnabledPlaylistIdsFlow

    override fun isMixEnabled(playlistId: String): Flow<Boolean> =
        userPreferences.mixEnabledPlaylistIdsFlow.map { playlistId in it }.distinctUntilChanged()

    override suspend fun setMixEnabled(playlistId: String, enabled: Boolean) {
        userPreferences.setPlaylistMixEnabled(playlistId, enabled)
    }

    override fun getMixTransition(
        playlistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<MixTransition?> =
        transitionDao.getSpecificRule(playlistId, fromTrackId, toTrackId)
            .map { MixTransition.fromJson(it?.mixJson) }

    override fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>> {
        return transitionDao.getAllRulesForPlaylist(playlistId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?> {
        return transitionDao.getPlaylistDefaultRule(playlistId).map { entity ->
            entity?.toModel()
        }
    }

    override suspend fun saveRule(rule: TransitionRule) {
        transitionDao.setRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: Long) {
        transitionDao.deleteRule(ruleId)
    }

    override suspend fun deletePlaylistDefaultRule(playlistId: String) {
        transitionDao.deletePlaylistDefaultRule(playlistId)
    }

    override fun getGlobalSettings(): Flow<TransitionSettings> {
        return userPreferences.globalTransitionSettingsFlow
    }

    override suspend fun saveGlobalSettings(settings: TransitionSettings) {
        userPreferences.saveGlobalTransitionSettings(settings)
    }

    private fun TransitionRuleEntity.holdsCrossfadeSetting(): Boolean =
        mixJson == null || MixTransition.fromJson(mixJson)?.keepsPairSettings == true

    private fun TransitionRuleEntity.toModel(): TransitionRule {
        return TransitionRule(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings,
            mix = MixTransition.fromJson(this.mixJson)
        )
    }

    private fun TransitionRule.toEntity(): TransitionRuleEntity {
        return TransitionRuleEntity(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings,
            mixJson = this.mix?.let { MixTransition.toJson(it.sanitized()) }
        )
    }
}
