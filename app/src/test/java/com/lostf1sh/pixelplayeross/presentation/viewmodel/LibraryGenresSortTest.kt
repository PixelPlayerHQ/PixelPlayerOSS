package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.Genre
import com.lostf1sh.pixelplayeross.data.model.SortOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryGenresSortTest {

    private val genres = listOf(
        genre("unknown", "Unknown", songCount = 40),
        genre("jazz", "Jazz", songCount = 3),
        genre("house", "House", songCount = 12),
        genre("ambient", "ambient", songCount = 3),
        genre("electronic", "Electronic", songCount = 7)
    )

    @Test
    fun `name sorts ignore case and keep Unknown last`() {
        assertEquals(
            listOf("ambient", "Electronic", "House", "Jazz", "Unknown"),
            sortGenresList(genres, SortOption.GenreNameAZ).map { it.name }
        )
        assertEquals(
            listOf("Jazz", "House", "Electronic", "ambient", "Unknown"),
            sortGenresList(genres, SortOption.GenreNameZA).map { it.name }
        )
    }

    @Test
    fun `song count sorts break ties by name and keep Unknown last`() {
        assertEquals(
            listOf("House", "Electronic", "ambient", "Jazz", "Unknown"),
            sortGenresList(genres, SortOption.GenreSongCountDesc).map { it.name }
        )
        assertEquals(
            listOf("ambient", "Jazz", "Electronic", "House", "Unknown"),
            sortGenresList(genres, SortOption.GenreSongCountAsc).map { it.name }
        )
    }

    @Test
    fun `a sort option from another tab falls back to name order`() {
        assertEquals(
            sortGenresList(genres, SortOption.GenreNameAZ),
            sortGenresList(genres, SortOption.FolderSongCountDesc)
        )
    }

    private fun genre(id: String, name: String, songCount: Int) =
        Genre(id = id, name = name, songCount = songCount)
}
