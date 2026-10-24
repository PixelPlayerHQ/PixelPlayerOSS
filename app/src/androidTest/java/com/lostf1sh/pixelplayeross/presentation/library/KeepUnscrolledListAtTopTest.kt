package com.lostf1sh.pixelplayeross.presentation.library

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A folder's songs are first listed by title and re-sort once their details load. A keyed
 * LazyColumn follows its first visible row through that change, even from the top, so a long
 * folder sorted by album opened scrolled to wherever its first song by title landed.
 */
@RunWith(AndroidJUnit4::class)
class KeepUnscrolledListAtTopTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rows = mutableStateOf(TITLE_ORDER)
    private lateinit var listState: LazyListState

    private fun showList(keepAtTop: Boolean = true) {
        composeRule.setContent {
            val state = rememberLazyListState()
            listState = state
            if (keepAtTop) {
                KeepUnscrolledListAtTop(listState = state, rows = rows.value)
            }
            LazyColumn(state = state, modifier = Modifier.height(VIEWPORT_HEIGHT)) {
                items(rows.value, key = { it }) { row ->
                    BasicText(text = row, modifier = Modifier.height(ROW_HEIGHT))
                }
            }
        }
    }

    @Test
    fun unscrolledListStaysAtTheTopWhenItsFirstRowMovesDown() {
        showList()

        composeRule.runOnIdle { rows.value = TRACK_ORDER }

        composeRule.runOnIdle {
            assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
        }
        composeRule.onNodeWithText(TRACK_ORDER.first()).assertIsDisplayed()
    }

    @Test
    fun withoutItAnUnscrolledListFollowsItsFirstRowDown() {
        showList(keepAtTop = false)

        composeRule.runOnIdle { rows.value = TRACK_ORDER }

        composeRule.runOnIdle {
            assertThat(listState.firstVisibleItemIndex).isEqualTo(TRACK_ORDER.indexOf(TITLE_ORDER.first()))
        }
    }

    @Test
    fun scrolledListKeepsFollowingItsFirstVisibleRow() {
        showList()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        val firstVisibleRow = TITLE_ORDER[1]

        composeRule.runOnIdle { rows.value = TRACK_ORDER }

        composeRule.runOnIdle {
            assertThat(listState.firstVisibleItemIndex).isEqualTo(TRACK_ORDER.indexOf(firstVisibleRow))
        }
    }

    private companion object {
        val VIEWPORT_HEIGHT = 200.dp
        val ROW_HEIGHT = 50.dp

        val TITLE_ORDER = listOf(
            "Afterglow", "Blue Hour", "Copper", "Driftwood", "Ember", "Fable",
            "Glass", "Harbor", "Ivory", "Juniper", "Kites", "Lantern"
        )

        // Album track order of the same songs: the first song by title is track 6, which is not
        // far enough down for the end of the list to clamp the scroll.
        val TRACK_ORDER = listOf(
            "Harbor", "Copper", "Kites", "Blue Hour", "Lantern", "Afterglow",
            "Glass", "Juniper", "Ember", "Driftwood", "Ivory", "Fable"
        )
    }
}
