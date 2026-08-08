package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkDao
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import com.lostf1sh.pixelplayeross.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookmarksModuleHandler @Inject constructor(
    private val audioBookmarkDao: AudioBookmarkDao,
    @param:BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.AUDIO_BOOKMARKS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(audioBookmarkDao.getAllBookmarksOnce())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        audioBookmarkDao.getAllBookmarksOnce().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, AudioBookmarkEntity::class.java).type
        val bookmarks: List<AudioBookmarkEntity> = gson.fromJson(payload, type)
        audioBookmarkDao.replaceAll(bookmarks)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
