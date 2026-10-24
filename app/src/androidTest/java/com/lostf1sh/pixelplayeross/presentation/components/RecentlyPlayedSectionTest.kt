package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.model.RecentlyPlayedSongUiModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ThemeStateHolder
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home "Recently played" pills have no more-options button: a tap plays the song and a long
 * press asks for its options.
 */
@RunWith(AndroidJUnit4::class)
class RecentlyPlayedSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val songs = (1..4).map { index ->
        Song.emptySong().copy(
            id = "recent-$index",
            title = "Recent Song $index",
            artist = "Recent Artist $index"
        )
    }

    private val clickedSongIds = mutableListOf<String>()
    private val longClickedSongIds = mutableListOf<String>()

    private fun setSection() {
        val themeStateHolder = mockk<ThemeStateHolder>(relaxed = true) {
            every { getAlbumColorSchemeFlow(any(), any()) } returns MutableStateFlow(null)
        }
        val items = songs.mapIndexed { index, song ->
            RecentlyPlayedSongUiModel(song = song, lastPlayedTimestamp = 1_000L - index)
        }.toImmutableList()

        composeRule.setContent {
            MaterialTheme {
                RecentlyPlayedSection(
                    songs = items,
                    onSongClick = { clickedSongIds += it.id },
                    onSongLongClick = { longClickedSongIds += it.id },
                    onOpenAllClick = {},
                    themeStateHolder = themeStateHolder
                )
            }
        }
    }

    @Test
    fun longPressOnPillAsksForThatSongsOptionsWithoutPlaying() {
        setSection()

        composeRule.onNodeWithText(songs[1].title).performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertThat(longClickedSongIds).containsExactly(songs[1].id)
            assertThat(clickedSongIds).isEmpty()
        }
    }

    @Test
    fun tapOnPillStillPlaysWithoutOpeningOptions() {
        setSection()

        composeRule.onNodeWithText(songs[2].title).performClick()

        composeRule.runOnIdle {
            assertThat(clickedSongIds).containsExactly(songs[2].id)
            assertThat(longClickedSongIds).isEmpty()
        }
    }

    @Test
    fun pillExposesMoreOptionsLongClickActionToAccessibility() {
        setSection()
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.presentation_batch_b_more_options)

        composeRule.onNodeWithText(songs[0].title).assert(
            SemanticsMatcher("has long click labelled '$label'") { node ->
                node.config.getOrNull(SemanticsActions.OnLongClick)?.label == label
            }
        )
    }
}
