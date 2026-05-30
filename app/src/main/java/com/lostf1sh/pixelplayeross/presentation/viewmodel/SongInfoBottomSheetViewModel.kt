package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.toArtist
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.AudioMeta
import com.lostf1sh.pixelplayeross.utils.AudioMetaUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@HiltViewModel
class SongInfoBottomSheetViewModel @Inject constructor(
    private val musicDao: MusicDao,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class SongLocationInfo(
        val label: String,
        val value: String,
        val isCloud: Boolean,
    )

    enum class ToneTarget {
        Ringtone,
        Notification,
        Alarm,
    }

    sealed interface ToneActionResult {
        data class Success(val message: String) : ToneActionResult
        data class NeedsSystemWritePermission(val message: String) : ToneActionResult
        data class Error(val message: String) : ToneActionResult
    }

    private val _audioMeta = MutableStateFlow<AudioMeta?>(null)
    private val _resolvedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val resolvedArtists: StateFlow<List<Artist>> = _resolvedArtists.asStateFlow()

    val audioMeta: StateFlow<AudioMeta?> = _audioMeta.asStateFlow()

    fun loadArtistsForSong(song: Song) {
        val refs = song.artists
        if (refs.isEmpty() || refs.size < 2) {
            _resolvedArtists.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = refs.map { it.id }.filter { it > 0L }.distinct()
            val entitiesById = if (ids.isNotEmpty()) {
                musicDao.getArtistsByIds(ids).associateBy { it.id }
            } else {
                emptyMap()
            }
            val resolved = refs.map { ref ->
                entitiesById[ref.id]?.toArtist()
                    ?: Artist(id = ref.id, name = ref.name, songCount = 0)
            }
            _resolvedArtists.value = resolved
        }
    }

    fun loadAudioMeta(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val meta = AudioMetaUtils.getAudioMetadata(
                musicDao = musicDao,
                id = song.id.toLongOrNull() ?: -1L,
                filePath = song.path,
                deepScan = false
            )
            _audioMeta.value = meta
        }
    }

    fun getSongLocationInfo(song: Song): SongLocationInfo {
        val provider = getCloudProviderLabel(song.contentUriString)
        return if (provider != null) {
            SongLocationInfo(
                label = "Provider",
                value = provider,
                isCloud = true,
            )
        } else {
            SongLocationInfo(
                label = "Path",
                value = song.path,
                isCloud = false,
            )
        }
    }

    fun hasSystemWritePermission(): Boolean {
        return Settings.System.canWrite(appContext)
    }

    fun createSystemWriteSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    fun setSongAsTone(song: Song, target: ToneTarget, onComplete: (ToneActionResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                setSongAsToneInternal(song, target)
            }
            onComplete(result)
        }
    }

    private fun getCloudProviderLabel(contentUriString: String): String? {
        return when {
            contentUriString.startsWith("navidrome://") -> "Navidrome"
            contentUriString.startsWith("jellyfin://") -> "Jellyfin"
            else -> null
        }
    }

    private suspend fun setSongAsToneInternal(song: Song, target: ToneTarget): ToneActionResult {
        if (getCloudProviderLabel(song.contentUriString) != null) {
            return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_local_only)
            )
        }

        val ringtoneUri = runCatching { resolveMediaStoreAudioUri(song) }.getOrNull()
            ?: return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_missing_file)
            )

        if (!Settings.System.canWrite(appContext)) {
            return ToneActionResult.NeedsSystemWritePermission(
                appContext.getString(R.string.song_info_ringtone_permission_prompt)
            )
        }

        return runCatching {
            markAsToneCandidate(ringtoneUri, target)
            RingtoneManager.setActualDefaultRingtoneUri(
                appContext,
                target.ringtoneManagerType,
                ringtoneUri,
            )
            ToneActionResult.Success(
                appContext.getString(
                    R.string.song_info_tone_success,
                    song.title,
                    appContext.getString(target.successLabelResId),
                )
            )
        }.getOrElse { throwable ->
            ToneActionResult.Error(
                appContext.getString(
                    R.string.song_info_ringtone_failed,
                    throwable.localizedMessage ?: throwable.javaClass.simpleName
                )
            )
        }
    }

    private suspend fun resolveMediaStoreAudioUri(song: Song): Uri? {
        song.id.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { id ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
            ?.takeIf(::mediaStoreAudioExists)
            ?.let { return it }

        song.contentUriString
            .takeIf { it.startsWith("content://") }
            ?.toUri()
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?.let { return it }

        findMediaStoreAudioUriByPath(song.path)?.let { return it }

        val file = File(song.path)
        if (!file.exists()) return null

        return scanAudioFile(file, song.mimeType)
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?: findMediaStoreAudioUriByPath(song.path)
    }

    private fun findMediaStoreAudioUriByPath(path: String): Uri? {
        if (path.isBlank()) return null
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        return runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }.getOrNull()
    }

    private suspend fun scanAudioFile(file: File, mimeType: String?): Uri? =
        suspendCancellableCoroutine { continuation ->
            val mimeTypes = mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { arrayOf(it) }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(file.absolutePath),
                mimeTypes,
            ) { _, uri ->
                if (continuation.isActive) {
                    continuation.resume(uri)
                }
            }
        }

    private fun mediaStoreAudioExists(uri: Uri): Boolean {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
        }.getOrDefault(false)
    }

    private fun markAsToneCandidate(uri: Uri, target: ToneTarget) {
        runCatching {
            val values = ContentValues().apply {
                when (target) {
                    ToneTarget.Ringtone -> put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    ToneTarget.Notification -> put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                    ToneTarget.Alarm -> put(MediaStore.Audio.Media.IS_ALARM, true)
                }
            }
            appContext.contentResolver.update(uri, values, null, null)
        }
    }

    private val ToneTarget.ringtoneManagerType: Int
        get() = when (this) {
            ToneTarget.Ringtone -> RingtoneManager.TYPE_RINGTONE
            ToneTarget.Notification -> RingtoneManager.TYPE_NOTIFICATION
            ToneTarget.Alarm -> RingtoneManager.TYPE_ALARM
        }

    private val ToneTarget.successLabelResId: Int
        get() = when (this) {
            ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
            ToneTarget.Notification -> R.string.song_info_tone_notification_label
            ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
        }
}
