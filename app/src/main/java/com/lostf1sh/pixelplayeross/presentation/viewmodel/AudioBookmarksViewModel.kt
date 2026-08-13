package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.AudioBookmarkRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioBookmarksViewModel @Inject constructor(
    private val bookmarkRepository: AudioBookmarkRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val allBookmarks: StateFlow<List<AudioBookmarkEntity>> = bookmarkRepository.getAllBookmarksFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun observeSongs(songIds: List<String>): Flow<List<Song>> =
        musicRepository.getSongsByIds(songIds)

    fun addBookmark(song: Song, title: String, timestampMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmark = AudioBookmarkEntity(
                songId = song.id,
                songTitle = song.title,
                artistName = song.displayArtist,
                albumArtUri = song.albumArtUriString,
                title = title,
                timestampMs = timestampMs
            )
            bookmarkRepository.insertBookmark(bookmark)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun renameBookmark(id: Long, title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val bookmark = bookmarkRepository.getBookmarkById(id) ?: return@launch
            bookmarkRepository.insertBookmark(bookmark.copy(title = trimmedTitle))
        }
    }
}
