package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The results below Search's header stay where they are when the filter chips give way to the
 * selection actions, so a long press that starts a selection leaves its row under the finger.
 */
@RunWith(AndroidJUnit4::class)
class SearchResultsHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var showSelectionActions by mutableStateOf(false)

    private fun setHeader(chipsHeight: Dp, actionsHeight: Dp) {
        composeRule.setContent {
            Column(Modifier.fillMaxSize()) {
                SearchResultsHeader(
                    showSelectionActions = showSelectionActions,
                    modifier = Modifier.fillMaxWidth(),
                    filterChips = { Box(Modifier.fillMaxWidth().height(chipsHeight).testTag(CHIPS)) },
                ) {
                    Box(Modifier.fillMaxWidth().height(actionsHeight).testTag(ACTIONS))
                }
                Box(Modifier.fillMaxWidth().weight(1f).testTag(RESULTS))
            }
        }
    }

    private fun top(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun selectionActionsTakeTheChipsPlaceWithoutMovingTheResults() {
        // Chips wrapped to two rows, actions in one.
        setHeader(chipsHeight = 112.dp, actionsHeight = 64.dp)
        val resultsTop = top(RESULTS)
        composeRule.onNodeWithTag(CHIPS).assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIONS).assertIsNotDisplayed()

        showSelectionActions = true

        composeRule.onNodeWithTag(ACTIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(CHIPS).assertIsNotDisplayed()
        assertThat(top(RESULTS)).isEqualTo(resultsTop)
        // Centred in the space the chips leave.
        with(composeRule.density) {
            assertThat(top(ACTIONS)).isWithin(1f).of((112.dp - 64.dp).toPx() / 2f)
        }

        showSelectionActions = false

        composeRule.onNodeWithTag(CHIPS).assertIsDisplayed()
        assertThat(top(RESULTS)).isEqualTo(resultsTop)
    }

    @Test
    fun theHeaderIsAsTallAsTheTallerOfTheTwo() {
        setHeader(chipsHeight = 48.dp, actionsHeight = 64.dp)
        val resultsTop = top(RESULTS)
        with(composeRule.density) { assertThat(resultsTop).isWithin(1f).of(64.dp.toPx()) }

        showSelectionActions = true

        assertThat(top(RESULTS)).isEqualTo(resultsTop)
    }

    private companion object {
        const val CHIPS = "filter_chips"
        const val ACTIONS = "selection_actions"
        const val RESULTS = "results"
    }
}
