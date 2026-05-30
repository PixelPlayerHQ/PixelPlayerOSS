package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ViewModelScoped
class SongRemovalStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val libraryStateHolder: LibraryStateHolder
) {
    suspend fun showDeleteConfirmation(activity: Activity, song: Song): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.dialog_delete_song_title))
                    .setMessage(
                        activity.getString(
                            R.string.dialog_delete_song_message,
                            song.title,
                            song.displayArtist
                        )
                    )
                    .setPositiveButton(activity.getString(R.string.delete_action)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteSongFile(song: Song): Boolean {
        return metadataEditStateHolder.deleteSong(song)
    }

    suspend fun removeSongFromLibrary(song: Song) {
        libraryStateHolder.removeSong(song.id)
        musicRepository.deleteById(song.id.toLong())
        playlistPreferencesRepository.removeSongFromAllPlaylists(song.id)
    }
}
