package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel

enum class SettingType {
    SWITCH,
    NAVIGABLE_CARD,
    ACTION
}

data class SettingSpec(
    val id: String,
    val itemKey: String = "setting_$id",
    @StringRes val titleRes: Int? = null,
    val titleStatic: String? = null,
    @StringRes val subtitleRes: Int? = null,
    val subtitleStatic: String? = null,
    val category: SettingsCategory,
    val subscreenRoute: String,
    val type: SettingType,
    @StringRes val keywordsRes: List<Int> = emptyList(),
    val keywordsStatic: List<String> = emptyList(),
    val getValue: ((SettingsUiState) -> Boolean)? = null,
    val onToggle: ((SettingsViewModel, Boolean) -> Unit)? = null,
    val onAction: ((NavController) -> Unit)? = null
) {
    fun getTitle(context: android.content.Context): String {
        return titleStatic ?: titleRes?.let { context.getString(it) } ?: ""
    }

    fun getSubtitle(context: android.content.Context): String? {
        return subtitleStatic ?: subtitleRes?.let { context.getString(it) }
    }

    fun createNavigationRoute(): String {
        return if (subscreenRoute.contains("?")) {
            "$subscreenRoute&highlightKey=$itemKey"
        } else {
            "$subscreenRoute?highlightKey=$itemKey"
        }
    }
}

data class SearchResultItem(
    val spec: SettingSpec,
    val score: Float,
    val matchedTitle: String,
    val matchedSubtitle: String?
)

data class SearchResultSection(
    val titleRes: Int,
    val items: List<SearchResultItem>
)
