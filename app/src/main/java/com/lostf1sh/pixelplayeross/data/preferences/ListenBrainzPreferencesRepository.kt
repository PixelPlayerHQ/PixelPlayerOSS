package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-source scrobble toggles. All default to enabled — connecting a ListenBrainz account is the
 * consent act; users disable a streaming source only when their server already scrobbles
 * server-side. With no account connected these values have no effect.
 */
@Singleton
class ListenBrainzPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SCROBBLE_LOCAL = booleanPreferencesKey("listenbrainz_scrobble_local")
        val SCROBBLE_NAVIDROME = booleanPreferencesKey("listenbrainz_scrobble_navidrome")
        val SCROBBLE_JELLYFIN = booleanPreferencesKey("listenbrainz_scrobble_jellyfin")
    }

    val scrobbleLocalFlow: Flow<Boolean> = dataStore.data.map { it[Keys.SCROBBLE_LOCAL] ?: true }
    val scrobbleNavidromeFlow: Flow<Boolean> = dataStore.data.map { it[Keys.SCROBBLE_NAVIDROME] ?: true }
    val scrobbleJellyfinFlow: Flow<Boolean> = dataStore.data.map { it[Keys.SCROBBLE_JELLYFIN] ?: true }

    suspend fun setScrobbleLocal(enabled: Boolean) {
        dataStore.edit { it[Keys.SCROBBLE_LOCAL] = enabled }
    }

    suspend fun setScrobbleNavidrome(enabled: Boolean) {
        dataStore.edit { it[Keys.SCROBBLE_NAVIDROME] = enabled }
    }

    suspend fun setScrobbleJellyfin(enabled: Boolean) {
        dataStore.edit { it[Keys.SCROBBLE_JELLYFIN] = enabled }
    }

    suspend fun isSourceEnabled(source: String): Boolean {
        val key = when (source) {
            ListenBrainzSource.NAVIDROME -> Keys.SCROBBLE_NAVIDROME
            ListenBrainzSource.JELLYFIN -> Keys.SCROBBLE_JELLYFIN
            else -> Keys.SCROBBLE_LOCAL
        }
        return dataStore.data.first()[key] ?: true
    }
}
