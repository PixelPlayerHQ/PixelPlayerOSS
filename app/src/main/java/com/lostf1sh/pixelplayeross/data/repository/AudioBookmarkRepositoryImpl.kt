package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkDao
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookmarkRepositoryImpl @Inject constructor(
    private val audioBookmarkDao: AudioBookmarkDao
) : AudioBookmarkRepository {
    override fun getAllBookmarksFlow(): Flow<List<AudioBookmarkEntity>> =
        audioBookmarkDao.getAllBookmarksFlow()

    override suspend fun getBookmarksForSong(songId: String): List<AudioBookmarkEntity> =
        audioBookmarkDao.getBookmarksForSong(songId)

    override suspend fun insertBookmark(bookmark: AudioBookmarkEntity) =
        audioBookmarkDao.insertBookmark(bookmark)

    override suspend fun deleteBookmark(id: Long) =
        audioBookmarkDao.deleteBookmark(id)

    override suspend fun getBookmarkById(id: Long): AudioBookmarkEntity? =
        audioBookmarkDao.getBookmarkById(id)
}
