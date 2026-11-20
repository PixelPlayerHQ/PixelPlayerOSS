package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPickerSelectionTest {

    @Test
    fun `pending edits survive metadata refresh and reordering but reset for different songs`() {
        val firstSong = Song.emptySong().copy(id = "first", title = "First title")
        val secondSong = Song.emptySong().copy(id = "second")
        val refreshedSong = firstSong.copy(title = "Updated title")
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        lateinit var edits: SnapshotStateMap<String, Boolean>

        fun render(songs: ImmutableList<Song>): SnapshotStateMap<String, Boolean> {
            composition.setContent {
                val selectionEdits = rememberPlaylistPickerSelectionEdits(songs)
                SideEffect { edits = selectionEdits }
            }
            return edits
        }

        try {
            render(persistentListOf(firstSong, secondSong))["chosen"] = true
            assertEquals(mapOf("chosen" to true), render(persistentListOf(refreshedSong, secondSong)).toMap())
            assertEquals(mapOf("chosen" to true), render(persistentListOf(secondSong, refreshedSong)).toMap())
            assertTrue(render(persistentListOf(secondSong, refreshedSong.copy(id = "third"))).isEmpty())
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun `a single song can be removed from all of its playlists`() {
        val selection = PlaylistPickerSelection(
            initialPlaylistIds = setOf("one", "two"),
            edits = mapOf("one" to false, "two" to false),
            songCount = 1
        )

        assertTrue(selection.canSave)
        assertTrue(selection.selectedPlaylistIds.isEmpty())
    }

    @Test
    fun `playlists outside the current search keep their membership when saving edits`() {
        val selection = PlaylistPickerSelection(
            initialPlaylistIds = setOf("hidden", "visible"),
            edits = mapOf("visible" to false, "new" to true),
            songCount = 1
        )

        assertTrue(selection.canSave)
        assertEquals(setOf("hidden", "new"), selection.selectedPlaylistIds)
    }

    @Test
    fun `a single song with unchanged membership cannot be saved`() {
        val selection = PlaylistPickerSelection(setOf("one"), emptyMap(), songCount = 1)

        assertFalse(selection.canSave)
    }

    @Test
    fun `a playlist arriving after the picker opens keeps its membership`() {
        val edits = mapOf("chosen" to true)
        val beforeLoad = PlaylistPickerSelection(emptySet(), edits, songCount = 1)
        val afterLoad = PlaylistPickerSelection(setOf("arrived"), edits, songCount = 1)

        assertEquals(setOf("chosen"), beforeLoad.selectedPlaylistIds)
        assertEquals(setOf("arrived", "chosen"), afterLoad.selectedPlaylistIds)
    }

    @Test
    fun `adding multiple songs requires at least one selected playlist`() {
        assertFalse(PlaylistPickerSelection(emptySet(), emptyMap(), songCount = 2).canSave)
        assertTrue(PlaylistPickerSelection(emptySet(), mapOf("one" to true), songCount = 2).canSave)
    }
}
