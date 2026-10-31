package com.lostf1sh.pixelplayeross.presentation.components.player

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.LocalMaterialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The play/pause glyph answers the tap itself instead of waiting for the player, but still
 * shows the real state when playback never starts; a skip tap stretches its button.
 */
@RunWith(AndroidJUnit4::class)
class AnimatedPlaybackControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val playLabel get() = context.getString(R.string.cd_play)
    private val pauseLabel get() = context.getString(R.string.cd_pause)

    private fun showControls(
        isPlayingProvider: () -> Boolean,
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMaterialTheme provides MaterialTheme.colorScheme) {
                    AnimatedPlaybackControls(
                        isPlayingProvider = isPlayingProvider,
                        onPrevious = {},
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        pressAnimationSpec = tween(durationMillis = 240),
                    )
                }
            }
        }
    }

    @Test
    fun playTapShowsPauseAtOnceAndFallsBackWhenPlaybackNeverStarts() {
        showControls(isPlayingProvider = { false })
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription(playLabel).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(pauseLabel).assertExists()

        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithContentDescription(playLabel).assertExists()
    }

    @Test
    fun playTapDuringTheInitialNotPlayingDelayKeepsPause() {
        composeRule.mainClock.autoAdvance = false
        showControls(isPlayingProvider = { false })
        // The controls appear paused and start waiting before they show "not playing".
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithContentDescription(playLabel).performClick()
        // Past that wait, but before the fallback to the player's real state.
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithContentDescription(pauseLabel).assertExists()
    }

    @Test
    fun pauseTapRightAfterASkipIsNotOverriddenByTheSkipsPlayingState() {
        val isPlaying = mutableStateOf(true)
        showControls(isPlayingProvider = { isPlaying.value })
        composeRule.mainClock.autoAdvance = false

        // A skip briefly reports "not playing"; the "playing" that follows waits for the skip.
        composeRule.onNodeWithContentDescription(context.getString(R.string.next_track)).performClick()
        composeRule.mainClock.advanceTimeBy(40)
        composeRule.runOnUiThread { isPlaying.value = false }
        composeRule.mainClock.advanceTimeBy(40)
        composeRule.runOnUiThread { isPlaying.value = true }
        composeRule.mainClock.advanceTimeBy(40)

        composeRule.onNodeWithContentDescription(pauseLabel).performClick()
        composeRule.mainClock.advanceTimeBy(80)

        composeRule.onNodeWithContentDescription(playLabel).assertExists()
    }

    @Test
    fun playTapKeepsPauseOnceThePlayerFollows() {
        val isPlaying = mutableStateOf(false)
        showControls(
            isPlayingProvider = { isPlaying.value },
            onPlayPause = { isPlaying.value = !isPlaying.value },
        )
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription(playLabel).performClick()
        composeRule.mainClock.advanceTimeBy(1_500)

        composeRule.onNodeWithContentDescription(pauseLabel).assertExists()
    }

    @Test
    fun nextTapStretchesNextAndSettlesBackToEqualWidths() {
        showControls(isPlayingProvider = { true })
        val previous = composeRule.onNodeWithContentDescription(context.getString(R.string.previous_track))
        val next = composeRule.onNodeWithContentDescription(context.getString(R.string.next_track))
        assertThat(next.widthDp()).isWithin(0.5f).of(previous.widthDp())
        composeRule.mainClock.autoAdvance = false

        next.performClick()
        composeRule.mainClock.advanceTimeBy(200)
        assertThat(next.widthDp()).isGreaterThan(previous.widthDp() + 1f)

        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(next.widthDp()).isWithin(0.5f).of(previous.widthDp())
    }

    private fun SemanticsNodeInteraction.widthDp(): Float = getBoundsInRoot().width.value
}
