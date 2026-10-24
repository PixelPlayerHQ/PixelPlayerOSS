package com.lostf1sh.pixelplayeross.presentation.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.SortOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the Folders tab the sort sheet sorts two things: the folder rows and the songs inside the
 * open folder. A switcher under the title picks which one the options apply to, and every pick
 * must reach the callbacks of that target only.
 */
@RunWith(AndroidJUnit4::class)
class LibrarySortBottomSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val folderSorts = mutableListOf<SortOption>()
    private val songSorts = mutableListOf<SortOption>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun showSheet(
        secondaryOptions: ImmutableList<SortOption> = SortOption.FOLDER_SONGS.toImmutableList(),
        secondarySelectedOption: SortOption = SortOption.FolderSongTitleAZ,
        initiallyShowSecondary: Boolean = false
    ) {
        composeRule.setContent {
            LibrarySortBottomSheet(
                title = SHEET_TITLE,
                options = SortOption.FOLDERS.toImmutableList(),
                selectedOption = SortOption.FolderNameAZ,
                onDismiss = {},
                onOptionSelected = { folderSorts += it },
                secondaryOptions = secondaryOptions,
                secondarySelectedOption = secondarySelectedOption,
                onSecondaryOptionSelected = { songSorts += it },
                primaryTargetLabel = FOLDERS_LABEL,
                secondaryTargetLabel = SONGS_LABEL,
                initiallyShowSecondary = initiallyShowSecondary
            )
        }
    }

    private fun sortRow(@StringRes label: Int) =
        composeRule.onNode(hasText(string(label)) and isSelectable())

    @Test
    fun opensOnTheFolderOptionsAndSwitchesToTheSongOptions() {
        showSheet()

        sortRow(R.string.sort_method_subfolder_count).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sort_method_album_artist)).assertDoesNotExist()

        composeRule.onNodeWithText(SONGS_LABEL).performClick()

        sortRow(R.string.sort_method_album_artist).assertIsDisplayed()
        sortRow(R.string.sort_method_title).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.sort_method_subfolder_count)).assertDoesNotExist()
    }

    @Test
    fun songOptionsReportOnlyToTheSongCallbacks() {
        showSheet()

        composeRule.onNodeWithText(SONGS_LABEL).performClick()
        sortRow(R.string.sort_method_album_artist).performClick()

        composeRule.runOnIdle {
            assertThat(songSorts).containsExactly(SortOption.FolderSongAlbumArtist)
            assertThat(folderSorts).isEmpty()
        }
    }

    @Test
    fun songOptionsKeepTheCurrentDirectionAndTheOrderCardFlipsIt() {
        showSheet(
            secondarySelectedOption = SortOption.FolderSongDurationAsc,
            initiallyShowSecondary = true
        )

        sortRow(R.string.sort_method_duration).assertIsSelected()
        sortRow(R.string.sort_method_artist).performClick()
        composeRule.onNodeWithText(string(R.string.presentation_batch_g_sort_ascending)).performClick()

        composeRule.runOnIdle {
            assertThat(songSorts)
                .containsExactly(SortOption.FolderSongArtist, SortOption.FolderSongDuration)
                .inOrder()
            assertThat(folderSorts).isEmpty()
        }
    }

    @Test
    fun withoutSongOptionsTheSheetHasNoSwitcher() {
        showSheet(secondaryOptions = persistentListOf(), initiallyShowSecondary = true)

        composeRule.onNodeWithText(SONGS_LABEL).assertDoesNotExist()
        sortRow(R.string.sort_method_name).performClick()

        composeRule.runOnIdle {
            assertThat(folderSorts).containsExactly(SortOption.FolderNameAZ)
            assertThat(songSorts).isEmpty()
        }
    }

    private companion object {
        const val SHEET_TITLE = "Sort by"
        const val FOLDERS_LABEL = "Folders"
        const val SONGS_LABEL = "Songs"
    }
}
