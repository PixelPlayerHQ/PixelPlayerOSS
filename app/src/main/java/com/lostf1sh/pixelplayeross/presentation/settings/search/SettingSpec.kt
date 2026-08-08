package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.annotation.StringRes
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsUiState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel

enum class SettingType {
    SWITCH,
    NAVIGABLE_CARD
}

/**
 * One searchable entry in the settings tree.
 *
 * A spec describes where a setting lives ([category] + [subscreenRoute]) and how it should be
 * presented in search results ([type]). Titles and subtitles come either from a string resource
 * or, for settings whose label is composed at the call site, from a static string.
 */
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
    /**
     * Whether [subscreenRoute] accepts a `highlightKey` query argument. Only category pages do;
     * appending it to a route that does not declare the argument would fail to match.
     */
    val supportsHighlight: Boolean = true,
    val getValue: ((SettingsUiState) -> Boolean)? = null,
    val onToggle: ((SettingsViewModel, Boolean) -> Unit)? = null
) {
    fun getTitle(context: android.content.Context): String =
        titleStatic ?: titleRes?.let { context.getString(it) } ?: ""

    fun getSubtitle(context: android.content.Context): String? =
        subtitleStatic ?: subtitleRes?.let { context.getString(it) }

    /**
     * The route to open when a result is tapped. The target screen reads `highlightKey` and
     * pulses the matching row so the user can see which setting the result referred to.
     */
    fun createNavigationRoute(): String = when {
        !supportsHighlight -> subscreenRoute
        subscreenRoute.contains("?") -> "$subscreenRoute&highlightKey=$itemKey"
        else -> "$subscreenRoute?highlightKey=$itemKey"
    }
}

data class SearchResultItem(
    val spec: SettingSpec,
    val score: Float,
    val matchedTitle: String,
    val matchedSubtitle: String?
)

data class SearchResultSection(
    @StringRes val titleRes: Int,
    val items: List<SearchResultItem>
)
