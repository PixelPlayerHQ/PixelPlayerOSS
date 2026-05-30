package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.preferences.PreferenceBackupEntry
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerModuleHandler @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.EQUALIZER

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val entries = userPreferencesRepository.exportPreferencesForBackup()
            .filter { it.key in EQUALIZER_KEYS }
        gson.toJson(entries)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        userPreferencesRepository.exportPreferencesForBackup()
            .count { it.key in EQUALIZER_KEYS }
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)
        userPreferencesRepository.clearPreferencesByKeys(EQUALIZER_KEYS)
        if (entries.isNotEmpty()) {
            userPreferencesRepository.importPreferencesFromBackup(entries, clearExisting = false)
        }
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    companion object {
        val EQUALIZER_KEYS = setOf(
            "custom_presets_json",
            "pinned_presets_json"
        )
    }
}
