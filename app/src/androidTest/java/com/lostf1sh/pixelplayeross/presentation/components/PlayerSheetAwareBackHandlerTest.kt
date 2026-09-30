package com.lostf1sh.pixelplayeross.presentation.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.presentation.navigation.PlayerSheetBackCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerSheetAwareBackHandlerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backInTheCollapseCommitReachesFolderHandler() {
        val coordinator = PlayerSheetBackCoordinator()
        val dispatcher = composeRule.activity.onBackPressedDispatcher
        val events = mutableListOf<String>()
        var expanded by mutableStateOf(true)
        var secondBackDispatched = false

        composeRule.setContent {
            BackHandler { events += "leave library" }
            BackHandler(enabled = expanded) {
                events += "collapse player"
                expanded = false
            }
            SideEffect {
                coordinator.updatePlayerSheetHandlingBack(expanded)
                if (!expanded && !secondBackDispatched) {
                    secondBackDispatched = true
                    // Dispatch before another frame or the library callback's own SideEffect.
                    dispatcher.onBackPressed()
                }
            }
            // Register after the player to cover the navigation-entry ordering as well.
            PlayerSheetAwareBackHandler(coordinator, enabled = true) {
                events += "parent folder"
            }
        }

        composeRule.runOnIdle {
            assertThat(composeRule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                .isTrue()
            dispatcher.onBackPressed()
        }
        composeRule.runOnIdle {
            assertThat(events).containsExactly("collapse player", "parent folder").inOrder()
        }
    }
}
