package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val ALBUM_ART_COLOR_ACCURACY = intPreferencesKey("album_art_color_accuracy_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val GLOBAL_NOW_PLAYING_THEME_ENABLED = booleanPreferencesKey("global_now_playing_theme_enabled_v1")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val INTERFACE_SCALE = intPreferencesKey("interface_scale_percent")
    }

    val appThemeModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM
    }

    val playerThemePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.ALBUM_ART
    }

    val globalNowPlayingThemeEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.GLOBAL_NOW_PLAYING_THEME_ENABLED] ?: false
    }

    val themePresetFlow: Flow<String> = dataStore.data.map { preferences ->
        ThemePreset.sanitize(preferences[Keys.THEME_PRESET])
    }

    val interfaceAppearanceFlow: Flow<InterfaceAppearance> = dataStore.data.map { preferences ->
        InterfaceAppearance(
            useSystemFont = preferences[Keys.USE_SYSTEM_FONT] ?: false,
            scalePercent = InterfaceAppearance.sanitizeScale(preferences[Keys.INTERFACE_SCALE])
        )
    }

    val albumArtPaletteStyleFlow: Flow<AlbumArtPaletteStyle> = dataStore.data.map { preferences ->
        AlbumArtPaletteStyle.fromStorageKey(preferences[Keys.ALBUM_ART_PALETTE_STYLE])
    }

    val albumArtColorAccuracyFlow: Flow<Int> = dataStore.data.map { preferences ->
        AlbumArtColorAccuracy.clamp(preferences[Keys.ALBUM_ART_COLOR_ACCURACY] ?: AlbumArtColorAccuracy.DEFAULT)
    }

    suspend fun setPlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
        }

    suspend fun setAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_THEME_MODE] = themeMode
        }

    suspend fun setGlobalNowPlayingThemeEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.GLOBAL_NOW_PLAYING_THEME_ENABLED] = enabled
        }

    suspend fun setThemePreset(preset: String) =
        dataStore.edit { preferences ->
            preferences[Keys.THEME_PRESET] = ThemePreset.sanitize(preset)
        }

    suspend fun setUseSystemFont(enabled: Boolean) = dataStore.edit { preferences ->
        preferences[Keys.USE_SYSTEM_FONT] = enabled
    }

    suspend fun setInterfaceScale(scalePercent: Int) = dataStore.edit { preferences ->
        preferences[Keys.INTERFACE_SCALE] = InterfaceAppearance.sanitizeScale(scalePercent)
    }

    suspend fun initializeAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_THEME_MODE] == null) {
                preferences[Keys.APP_THEME_MODE] = themeMode
            }
        }

    suspend fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        }

    suspend fun setAlbumArtColorAccuracy(level: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(level)
        }

    suspend fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) = dataStore.edit { preferences ->
        preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(accuracyLevel)
    }
}

data class InterfaceAppearance(
    val useSystemFont: Boolean = false,
    val scalePercent: Int = DEFAULT_SCALE
) {
    companion object {
        const val DEFAULT_SCALE = 100
        val scaleOptions = listOf(90, DEFAULT_SCALE, 110, 120)

        fun sanitizeScale(value: Int?): Int = scaleOptions.firstOrNull { it == value } ?: DEFAULT_SCALE
    }
}

/** App-wide surface palettes. Only [DEFAULT] and [AMOLED] exist; unknown stored values fall back to [DEFAULT]. */
object ThemePreset {
    const val DEFAULT = "default"
    const val AMOLED = "amoled"

    fun sanitize(value: String?): String = if (value == AMOLED) AMOLED else DEFAULT
}
