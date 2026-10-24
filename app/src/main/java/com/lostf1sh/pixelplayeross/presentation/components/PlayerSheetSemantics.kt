package com.lostf1sh.pixelplayeross.presentation.components

import android.content.res.Resources
import androidx.annotation.StringRes
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState

/**
 * Test tags of the player sheet card, exposed to UiAutomator as its resource id. They stay the same
 * in every language, so the baseline profile benchmarks wait on them instead of the translated
 * [playerSheetContentDescription]. PlayerSheetAnimationBenchmarks repeats these values.
 */
internal const val PLAYER_SHEET_COLLAPSED_TEST_TAG = "player_sheet_collapsed"
internal const val PLAYER_SHEET_EXPANDED_TEST_TAG = "player_sheet_expanded"

internal fun playerSheetTestTag(state: PlayerSheetState): String =
    when (state) {
        PlayerSheetState.COLLAPSED -> PLAYER_SHEET_COLLAPSED_TEST_TAG
        PlayerSheetState.EXPANDED -> PLAYER_SHEET_EXPANDED_TEST_TAG
    }

/**
 * TalkBack description of the player sheet card, naming [songTitle] when there is one.
 *
 * Takes the caller's [resources] so it follows the in-app language.
 */
internal fun playerSheetContentDescription(
    resources: Resources,
    state: PlayerSheetState,
    songTitle: String?
): String {
    val title = songTitle?.takeIf { it.isNotBlank() }
    val descriptionRes = playerSheetContentDescriptionRes(state, hasTitle = title != null)
    return if (title != null) {
        resources.getString(descriptionRes, title)
    } else {
        resources.getString(descriptionRes)
    }
}

/** The variants with a title take it as their only format argument. */
@StringRes
internal fun playerSheetContentDescriptionRes(state: PlayerSheetState, hasTitle: Boolean): Int =
    when (state) {
        PlayerSheetState.COLLAPSED ->
            if (hasTitle) R.string.cd_player_sheet_collapsed else R.string.cd_player_sheet_collapsed_no_track
        PlayerSheetState.EXPANDED ->
            if (hasTitle) R.string.cd_player_sheet_expanded else R.string.cd_player_sheet_expanded_no_track
    }
