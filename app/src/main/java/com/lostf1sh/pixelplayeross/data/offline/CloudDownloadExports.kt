package com.lostf1sh.pixelplayeross.data.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lostf1sh.pixelplayeross.data.preferences.M3uSyncPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CloudExportFolder(val uri: Uri, val name: String)

enum class CloudExportResult { DISABLED, EXPORTED, FAILED }

@Singleton
class CloudDownloadExports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val m3uPreferences: M3uSyncPreferences
) {
    private val resolver get() = context.contentResolver
    private val configurationMutex = Mutex()

    val folder: Flow<CloudExportFolder?> = preferences.data.map { saved ->
        val uri = saved[FOLDER_URI] ?: return@map null
        CloudExportFolder(Uri.parse(uri), saved[FOLDER_NAME].orEmpty())
    }.distinctUntilChanged()

    suspend fun selectFolder(uri: Uri) = withContext(Dispatchers.IO) {
        configurationMutex.withLock {
            require(DocumentsContract.isTreeUri(uri))
            val previous = folder.first()?.uri
            resolver.takePersistableUriPermission(uri, TREE_FLAGS)
            var saved = false
            try {
                val root = rootDocument(uri)
                val name = resolver.query(
                    root,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_FLAGS
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst() ||
                        (cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0
                    ) null else cursor.getString(0)
                } ?: throw IOException()
                preferences.edit {
                    it[FOLDER_URI] = uri.toString()
                    it[FOLDER_NAME] = name
                }
                saved = true
            } finally {
                if (saved && previous != null && previous != uri) releaseFolder(previous)
                if (!saved && previous != uri) releaseFolder(uri)
            }
        }
    }

    suspend fun clearFolder() = withContext(Dispatchers.IO) {
        configurationMutex.withLock {
            val previous = folder.first()?.uri
            preferences.edit {
                it.remove(FOLDER_URI)
                it.remove(FOLDER_NAME)
            }
            previous?.let { releaseFolder(it) }
        }
    }

    suspend fun export(file: File, title: String, downloadId: String): CloudExportResult =
        exportCopy(file, exportFileName(title, downloadId, file.extension), resumeInterruptedCopy = false)

    internal suspend fun exportDownloadedTrack(
        file: File,
        title: String,
        attemptId: String,
        resumeInterruptedCopy: Boolean
    ): CloudExportResult =
        exportCopy(file, exportFileName(title, attemptId, file.extension, idLength = attemptId.length), resumeInterruptedCopy)

    private suspend fun exportCopy(
        file: File,
        displayName: String,
        resumeInterruptedCopy: Boolean
    ): CloudExportResult =
        withContext(Dispatchers.IO) {
            var document: Uri? = null
            var finished = false
            try {
                val destination = folder.first()?.uri ?: return@withContext CloudExportResult.DISABLED
                val existing = if (resumeInterruptedCopy) findExportCopy(destination, displayName) else null
                if (existing != null && existing.second == file.length()) {
                    return@withContext CloudExportResult.EXPORTED
                }
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "application/octet-stream"
                file.inputStream().buffered().use { input ->
                    val created = existing?.first ?: DocumentsContract.createDocument(
                        resolver, rootDocument(destination), mimeType, displayName
                    ) ?: throw IOException()
                    document = created
                    resolver.openOutputStream(created, "wt")?.buffered()?.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val length = input.read(buffer)
                            if (length < 0) break
                            output.write(buffer, 0, length)
                        }
                    } ?: throw IOException()
                }
                finished = true
                CloudExportResult.EXPORTED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CloudExportResult.FAILED
            } finally {
                if (!finished) document?.let {
                    runCatching { DocumentsContract.deleteDocument(resolver, it) }
                }
            }
        }

    private fun findExportCopy(folder: Uri, displayName: String): Pair<Uri, Long?>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = resolver.query(children, columns, null, null, null) ?: throw IOException()
        cursor.use {
            while (it.moveToNext()) {
                if (it.getString(1) != displayName || it.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(folder, it.getString(0))
                return uri to if (it.isNull(2)) null else it.getLong(2)
            }
        }
        return null
    }

    private suspend fun releaseFolder(uri: Uri) {
        if (m3uPreferences.snapshot().treeUri == uri.toString()) return
        runCatching { resolver.releasePersistableUriPermission(uri, TREE_FLAGS) }
    }

    private fun rootDocument(uri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

    private companion object {
        val FOLDER_URI = stringPreferencesKey("cloud_download_export_tree_uri")
        val FOLDER_NAME = stringPreferencesKey("cloud_download_export_tree_name")
        const val TREE_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

private val unsupportedFileNameCharacters = Regex("""[\\/:*?"<>|\p{Cc}]""")

internal fun exportFileName(title: String, id: String, extension: String, idLength: Int = 12): String {
    val stem = title.replace(unsupportedFileNameCharacters, "_")
        .trim(' ', '.').take(60).trimEnd(' ', '.')
        .ifBlank { "Track" }
    return "$stem - ${id.take(idLength)}.$extension"
}
