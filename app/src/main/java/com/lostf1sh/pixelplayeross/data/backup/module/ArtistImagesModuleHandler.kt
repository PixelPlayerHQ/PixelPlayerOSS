package com.lostf1sh.pixelplayeross.data.backup.module

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.backup.model.ArtistImageBackupEntry
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImagesModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.ARTIST_IMAGES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val artists = musicDao.getAllArtistsListRaw()
        val entries = artists
            .mapNotNull { artist ->
                val imageUrl = artist.imageUrl?.takeIf { it.isNotBlank() }
                val customImageBase64 = artist.customImageUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let { readFileAsBase64(it) }
                // Skip artists with neither a Deezer URL nor a custom image
                if (imageUrl == null && customImageBase64 == null) return@mapNotNull null
                ArtistImageBackupEntry(
                    artistName = artist.name,
                    imageUrl = imageUrl ?: "",
                    customImageBase64 = customImageBase64
                )
            }
        gson.toJson(entries)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        musicDao.getAllArtistsListRaw().count {
            !it.imageUrl.isNullOrEmpty() || !it.customImageUri.isNullOrEmpty()
        }
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, ArtistImageBackupEntry::class.java).type
        val entries: List<ArtistImageBackupEntry> = gson.fromJson(payload, type)
        entries.forEach { entry ->
            val artistId = musicDao.getArtistIdByName(entry.artistName) ?: return@forEach
            // Restore Deezer URL
            if (entry.imageUrl.isNotBlank()) {
                musicDao.updateArtistImageUrl(artistId, entry.imageUrl)
            }
            // Restore custom image file
            val customBase64 = entry.customImageBase64
            if (customBase64 != null) {
                try {
                    val bytes = Base64.decode(customBase64, Base64.NO_WRAP)
                    val file = File(context.filesDir, "artist_art_${artistId}.jpg")
                    file.writeBytes(bytes)
                    musicDao.updateArtistCustomImage(artistId, file.absolutePath)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to restore custom image for artist: ${entry.artistName}")
                }
            }
        }
    }

    /**
     * restore() is additive — it never writes blank values — so replaying the snapshot
     * through it cannot undo image data a failed restore wrote onto artists that had none
     * before. Rollback instead forces every artist back to exactly its snapshot state,
     * clearing URLs/custom images for artists absent from the snapshot.
     */
    override suspend fun rollback(snapshot: String): Unit = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, ArtistImageBackupEntry::class.java).type
        val entries: List<ArtistImageBackupEntry> = gson.fromJson(snapshot, type) ?: emptyList()
        val snapshotByName = entries.associateBy { it.artistName }

        musicDao.getAllArtistsListRaw().forEach { artist ->
            val entry = snapshotByName[artist.name]
            musicDao.updateArtistImageUrl(artist.id, entry?.imageUrl.orEmpty())

            val customBase64 = entry?.customImageBase64
            if (customBase64 != null) {
                try {
                    val bytes = Base64.decode(customBase64, Base64.NO_WRAP)
                    val file = File(context.filesDir, "artist_art_${artist.id}.jpg")
                    file.writeBytes(bytes)
                    musicDao.updateArtistCustomImage(artist.id, file.absolutePath)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to roll back custom image for artist: ${artist.name}")
                }
            } else {
                // No custom image in the snapshot — remove anything the failed restore wrote.
                File(context.filesDir, "artist_art_${artist.id}.jpg").delete()
                musicDao.updateArtistCustomImage(artist.id, null)
            }
        }
    }

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read artist image: $path")
            null
        }
    }

    companion object {
        private const val TAG = "ArtistImagesHandler"
    }
}
