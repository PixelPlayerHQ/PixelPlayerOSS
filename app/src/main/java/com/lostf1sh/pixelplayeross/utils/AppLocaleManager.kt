package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.lostf1sh.pixelplayeross.data.preferences.AppLanguage
import java.util.Locale

object AppLocaleManager {
    private const val PREFERENCES_NAME = "app_locale_preferences"
    private const val KEY_LANGUAGE_TAG = "app_language_tag"

    fun currentLanguageTag(context: Context): String =
        AppLanguage.normalize(
            context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, AppLanguage.SYSTEM.tag)
                .orEmpty()
        )

    fun applyLanguage(context: Context, languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, normalized)
            .apply()

        val locales = if (normalized.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalized)
        }

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun wrapContext(base: Context): Context {
        val languageTag = currentLanguageTag(base)
        if (languageTag.isBlank()) return base

        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }

        return base.createConfigurationContext(configuration)
    }
}
