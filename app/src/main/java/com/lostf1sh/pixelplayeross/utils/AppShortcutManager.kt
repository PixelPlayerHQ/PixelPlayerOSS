package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.MainActivityIntentContract
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic app shortcuts for the launcher and persists the last playlist
 * to DataStore so Quick Settings tiles can access it even when the app is closed.
 */
@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val SHORTCUT_ID_LAST_PLAYLIST = "last_playlist"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Updates the dynamic shortcut for the last played playlist and persists
     * the playlist ID/name to DataStore for Quick Settings tile access.
     * @param playlistId The ID of the playlist
     * @param playlistName The display name of the playlist
     */
    fun updateLastPlaylistShortcut(playlistId: String, playlistName: String) {
        // Persist to DataStore so the QS tile can read it when the app is closed
        scope.launch {
            userPreferencesRepository.setLastPlaylist(playlistId, playlistName)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return // Launcher shortcuts not supported before API 25
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivityIntentContract.ACTION_OPEN_PLAYLIST
            putExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID, playlistId)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_LAST_PLAYLIST)
            .setShortLabel(playlistName)
            .setLongLabel(playlistName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_playlist_purple))
            .setIntent(intent)
            .build()

        // Remove old shortcut first to force icon refresh
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_PLAYLIST))
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Removes the last playlist shortcut if it exists.
     */
    fun removeLastPlaylistShortcut() {
        scope.launch {
            userPreferencesRepository.clearLastPlaylist()
        }
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_PLAYLIST))
    }
}
