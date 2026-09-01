package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lostf1sh.pixelplayeross.data.playlist.M3uSyncCheckpoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class M3uSyncLink(
    val playlistId: String,
    val documentUri: String,
    val fileName: String,
    val checkpoint: M3uSyncCheckpoint? = null,
)

@Serializable
internal data class M3uSyncConfig(
    val treeUri: String? = null,
    val links: Map<String, M3uSyncLink> = emptyMap(),
    val revision: Long = 0,
)

internal fun M3uSyncConfig.withLinksIfSelectionMatches(
    expectedTreeUri: String,
    expectedRevision: Long,
    replacementLinks: Map<String, M3uSyncLink>,
): M3uSyncConfig? = if (treeUri == expectedTreeUri && revision == expectedRevision) {
    copy(links = replacementLinks)
} else {
    null
}

/**
 * Owns the complete persisted state for app-external M3U synchronization.
 *
 * Keeping the tree grant and reconciliation checkpoints in one serialized value means an update
 * cannot expose a link without its matching checkpoint (or vice versa).
 */
@Singleton
class M3uSyncPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    internal val configFlow: Flow<M3uSyncConfig> = dataStore.data.map { preferences ->
        preferences[CONFIG_KEY]
            ?.let { encoded -> runCatching { json.decodeFromString<M3uSyncConfig>(encoded) }.getOrNull() }
            ?: M3uSyncConfig()
    }

    internal suspend fun snapshot(): M3uSyncConfig = configFlow.first()

    internal suspend fun selectTree(treeUri: String) {
        update { current ->
            if (current.treeUri == treeUri) {
                current.copy(revision = current.revision + 1)
            } else {
                M3uSyncConfig(treeUri = treeUri, revision = current.revision + 1)
            }
        }
    }

    internal suspend fun replaceLinksIfSelection(
        expectedTreeUri: String,
        expectedRevision: Long,
        links: Map<String, M3uSyncLink>,
    ): Boolean {
        var committed = false
        dataStore.edit { preferences ->
            val current = preferences[CONFIG_KEY]
                ?.let { encoded ->
                    runCatching { json.decodeFromString<M3uSyncConfig>(encoded) }.getOrNull()
                }
                ?: M3uSyncConfig()
            current.withLinksIfSelectionMatches(
                expectedTreeUri = expectedTreeUri,
                expectedRevision = expectedRevision,
                replacementLinks = links,
            )?.let { updated ->
                preferences[CONFIG_KEY] = json.encodeToString(updated)
                committed = true
            }
        }
        return committed
    }

    suspend fun disable() {
        update { current -> M3uSyncConfig(revision = current.revision + 1) }
    }

    private suspend fun update(transform: (M3uSyncConfig) -> M3uSyncConfig) {
        dataStore.edit { preferences ->
            val current = preferences[CONFIG_KEY]
                ?.let { encoded ->
                    runCatching { json.decodeFromString<M3uSyncConfig>(encoded) }.getOrNull()
                }
                ?: M3uSyncConfig()
            preferences[CONFIG_KEY] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        val CONFIG_KEY = stringPreferencesKey("m3u_sync_config_v1")
    }
}
