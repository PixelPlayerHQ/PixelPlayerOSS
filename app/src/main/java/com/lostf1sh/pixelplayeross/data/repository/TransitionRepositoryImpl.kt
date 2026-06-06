package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.database.TransitionRuleEntity
import com.lostf1sh.pixelplayeross.data.model.TransitionResolution
import com.lostf1sh.pixelplayeross.data.model.TransitionRule
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.model.TransitionSource
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionResolution> {
        // Chain the lookups according to priority: specific -> playlist -> global
        return transitionDao.getSpecificRule(playlistId, fromTrackId, toTrackId)
            .flatMapLatest { specificRule ->
                if (specificRule != null) {
                    flowOf(
                        TransitionResolution(
                            settings = specificRule.settings,
                            source = TransitionSource.PLAYLIST_SPECIFIC,
                        )
                    )
                } else {
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
                }
            }
    }

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
        // upsertRule dedupes the default rule, which a plain @Upsert cannot because of NULL track ids (F76).
        transitionDao.upsertRule(rule.toEntity())
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

    // --- Mappers ---

    private fun TransitionRuleEntity.toModel(): TransitionRule {
        return TransitionRule(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }

    private fun TransitionRule.toEntity(): TransitionRuleEntity {
        // The ID is included for updates. If it's the default 0, Room treats it as a new entry for auto-generation.
        // The unique index on (playlistId, from, to) ensures upsert logic works correctly.
        return TransitionRuleEntity(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }
}
