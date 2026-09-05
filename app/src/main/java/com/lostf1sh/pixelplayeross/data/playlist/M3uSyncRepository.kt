package com.lostf1sh.pixelplayeross.data.playlist

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylist
import com.lostf1sh.pixelplayeross.data.preferences.M3uSyncConfig
import com.lostf1sh.pixelplayeross.data.preferences.M3uSyncLink
import com.lostf1sh.pixelplayeross.data.preferences.M3uSyncPreferences
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.di.AppScope
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

data class M3uSyncReport(
    val exported: Int = 0,
    val imported: Int = 0,
    val unchanged: Int = 0,
    val conflicts: List<String> = emptyList(),
    val unresolvedEntries: Int = 0,
    val skippedFiles: Int = 0,
) {
    val changed: Int get() = exported + imported
}

data class M3uSyncState(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val lastReport: M3uSyncReport? = null,
    val error: String? = null,
)

private data class RuntimeState(
    val isSyncing: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val report: M3uSyncReport? = null,
    val error: String? = null,
)

private data class M3uDocument(
    val uri: Uri,
    val name: String,
)

private data class ParsedM3uDocument(
    val document: M3uDocument,
    val content: String,
    val parsed: M3uParseResult,
) {
    val rawHash: String by lazy { sha256("${document.name}\n$content") }
}

internal object M3uSyncSafety {
    fun safeRequestedPlaylistId(
        markerId: String?,
        occupiedPlaylistIds: Set<String>,
    ): String? = markerId?.takeIf { it !in occupiedPlaylistIds }

    fun canImportEntireFile(parsed: M3uParseResult): Boolean =
        parsed.unresolvedEntries.isEmpty() && parsed.ambiguousEntries.isEmpty()

    fun missingPlaylistSongIds(
        playlistSongIds: List<String>,
        availableSongIds: Set<String>,
    ): List<String> = playlistSongIds
        .filterNot(availableSongIds::contains)
        .distinct()
}

internal data class M3uReplacementNames(
    val temporary: String,
    val backup: String,
)

internal fun m3uReplacementNames(
    desiredName: String,
    transactionId: String,
): M3uReplacementNames {
    val stem = desiredName.substringBeforeLast('.', desiredName)
        .take(48)
        .ifBlank { "Playlist" }
    val extension = desiredName.substringAfterLast('.', "m3u8")
        .takeIf { it.equals("m3u", ignoreCase = true) || it.equals("m3u8", ignoreCase = true) }
        ?.lowercase()
        ?: "m3u8"
    val safeTransactionId = transactionId
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .take(48)
        .ifBlank { "write" }
    return M3uReplacementNames(
        temporary = "$stem.pixelplayer-$safeTransactionId.tmp",
        backup = "$stem.pixelplayer-backup-$safeTransactionId.$extension",
    )
}

/**
 * Reconciles the local playlist database with one user-selected Storage Access Framework tree.
 * SAF traversal, stable identity markers, conflict detection and checkpoints stay behind this
 * boundary so UI and workers only need to request a sync.
 */
@Singleton
class M3uSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val preferences: M3uSyncPreferences,
    private val dispatchers: DispatcherProvider,
    @AppScope appScope: CoroutineScope,
) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val syncMutex = Mutex()
    private val runtimeState = MutableStateFlow(RuntimeState())

    val state: StateFlow<M3uSyncState> = combine(
        preferences.configFlow,
        runtimeState,
    ) { config, runtime ->
        M3uSyncState(
            enabled = config.treeUri != null,
            treeUri = config.treeUri,
            isSyncing = runtime.isSyncing,
            lastSyncEpochMillis = runtime.lastSyncEpochMillis,
            lastReport = runtime.report,
            error = runtime.error,
        )
    }.stateIn(appScope, SharingStarted.Eagerly, M3uSyncState())

    suspend fun configure(treeUri: Uri): M3uSyncReport = syncMutex.withLock {
        require(DocumentsContract.isTreeUri(treeUri)) { "A document tree must be selected" }
        val selectedTree = treeUri.toString()
        val previousTree = preferences.snapshot().treeUri?.let(Uri::parse)
        var grantTaken = false
        var selectionPersisted = false
        try {
            resolver.takePersistableUriPermission(treeUri, TREE_PERMISSION_FLAGS)
            grantTaken = true
            // Verify the grant before persisting it. Some providers advertise a tree but deny access.
            withContext(dispatchers.io) { listM3uDocuments(treeUri) }
            preferences.selectTree(selectedTree)
            selectionPersisted = true
            syncNowLocked()
        } finally {
            when {
                selectionPersisted && previousTree != null && previousTree != treeUri -> {
                    releaseTreePermission(previousTree)
                }
                !selectionPersisted && grantTaken && previousTree?.toString() != selectedTree -> {
                    releaseTreePermission(treeUri)
                }
            }
        }
    }

    suspend fun disable(): Unit = syncMutex.withLock {
        val oldTree = preferences.snapshot().treeUri?.let(Uri::parse)
        preferences.disable()
        runtimeState.value = RuntimeState()
        oldTree?.let(::releaseTreePermission)
    }

    suspend fun syncNow(): M3uSyncReport = syncMutex.withLock {
        syncNowLocked()
    }

    private suspend fun syncNowLocked(): M3uSyncReport {
        val config = preferences.snapshot()
        val treeUri = config.treeUri?.let(Uri::parse)
            ?: return M3uSyncReport()
        runtimeState.value = runtimeState.value.copy(isSyncing = true, error = null)
        return try {
            val report = withContext(dispatchers.io) { reconcile(treeUri, config) }
            runtimeState.value = RuntimeState(
                lastSyncEpochMillis = System.currentTimeMillis(),
                report = report,
            )
            report
        } catch (error: Throwable) {
            Timber.e(error, "Automatic M3U synchronization failed")
            runtimeState.value = runtimeState.value.copy(
                isSyncing = false,
                error = error.message ?: error.javaClass.simpleName,
            )
            throw error
        }
    }

    private suspend fun reconcile(
        treeUri: Uri,
        config: M3uSyncConfig,
    ): M3uSyncReport {
        val expectedTreeUri = checkNotNull(config.treeUri)
        val songs = musicRepository.getAllSongsOnce()
        val songsById = songs.associateBy(Song::id)
        val exportableSongIds = songs.asSequence()
            .filter { song -> song.path.isNotBlank() || song.contentUriString.isNotBlank() }
            .map(Song::id)
            .toSet()
        val allPlaylistsById = playlistRepository.getPlaylistsOnce()
            .associateBy(Playlist::id)
            .toMutableMap()
        val occupiedPlaylistIds = allPlaylistsById.keys.toMutableSet()
        val playlists = allPlaylistsById.values
            .filter(::isSyncablePlaylist)
            .associateBy(Playlist::id)
            .toMutableMap()
        val documents = listM3uDocuments(treeUri)
        val parsedFiles = documents.mapNotNull { document ->
            runCatching {
                val content = readBoundedText(document.uri)
                ParsedM3uDocument(document, content, M3uManager.parseContent(content, songs))
            }.onFailure { error ->
                Timber.w(error, "Skipping unreadable M3U document %s", document.name)
            }.getOrNull()
        }
        val filesByUri = parsedFiles.associateBy { it.document.uri.toString() }
        val unreadableDocuments = documents
            .filterNot { it.uri.toString() in filesByUri }
        val unreadableDocumentsByName = unreadableDocuments.groupBy { it.name.lowercase() }
        val filesByMarker = parsedFiles
            .mapNotNull { file -> file.parsed.playlistId?.let { id -> id to file } }
            .groupBy({ it.first }, { it.second })

        val links = config.links.toMutableMap()
        val processedPlaylists = mutableSetOf<String>()
        val processedFiles = mutableSetOf<String>()
        val conflicts = mutableListOf<String>()
        var exported = 0
        var imported = 0
        var unchanged = 0
        var unresolved = 0
        val skipped = documents.size - parsedFiles.size

        fun noteFile(file: ParsedM3uDocument) {
            processedFiles += file.document.uri.toString()
            unresolved += file.parsed.unresolvedEntries.size + file.parsed.ambiguousEntries.size
        }

        fun noteConflict(
            name: String,
            playlistId: String? = null,
            files: List<ParsedM3uDocument> = emptyList(),
        ) {
            conflicts += name
            playlistId?.let(processedPlaylists::add)
            files.forEach(::noteFile)
        }

        fun resolvedSongs(playlist: Playlist): List<Song>? {
            val missing = M3uSyncSafety.missingPlaylistSongIds(
                playlistSongIds = playlist.songIds,
                availableSongIds = exportableSongIds,
            )
            if (missing.isNotEmpty()) {
                Timber.w(
                    "Refusing to export playlist %s because %d song ids are unavailable",
                    playlist.id,
                    missing.size,
                )
                return null
            }
            return playlist.songIds.map(songsById::getValue)
        }

        suspend fun importFile(
            file: ParsedM3uDocument,
            existing: Playlist?,
            requestedId: String? = null,
        ): Playlist {
            if (!M3uSyncSafety.canImportEntireFile(file.parsed)) {
                throw IOException("Playlist contains unresolved or ambiguous entries")
            }
            val fileName = playlistNameFromFile(file.document.name)
            val playlist = if (existing == null) {
                occupiedPlaylistIds += playlistRepository.getPlaylistsOnce().map(Playlist::id)
                val safeRequestedId = M3uSyncSafety.safeRequestedPlaylistId(
                    markerId = requestedId,
                    occupiedPlaylistIds = occupiedPlaylistIds,
                )
                if (requestedId != null && safeRequestedId == null) {
                    throw IOException("Playlist identity is already in use")
                }
                playlistRepository.createPlaylist(
                    name = fileName,
                    songIds = file.parsed.songIds,
                    customId = safeRequestedId,
                    source = "LOCAL",
                )
            } else {
                val updated = existing.copy(name = fileName, songIds = file.parsed.songIds)
                playlistRepository.updatePlaylist(updated)
                updated
            }
            playlists[playlist.id] = playlist
            allPlaylistsById[playlist.id] = playlist
            occupiedPlaylistIds += playlist.id
            val completeSongs = resolvedSongs(playlist)
                ?: throw IOException("Imported playlist songs are unavailable")
            val appHash = appHash(playlist, completeSongs)
            links[playlist.id] = M3uSyncLink(
                playlistId = playlist.id,
                documentUri = file.document.uri.toString(),
                fileName = file.document.name,
                checkpoint = M3uSyncCheckpoint(appHash, file.rawHash),
            )
            processedPlaylists += playlist.id
            noteFile(file)
            imported++
            return playlist
        }

        suspend fun exportPlaylist(
            playlist: Playlist,
            completeSongs: List<Song>,
            currentFile: ParsedM3uDocument?,
        ) {
            val desiredName = desiredFileName(playlist.name)
            val content = M3uManager.generateContent(playlist, completeSongs)
            val written = writeDocument(treeUri, currentFile?.document, desiredName, content)
            val finalFileHash = sha256("${written.name}\n$content")
            val finalAppHash = appHash(playlist, completeSongs)
            links[playlist.id] = M3uSyncLink(
                playlistId = playlist.id,
                documentUri = written.uri.toString(),
                fileName = written.name,
                checkpoint = M3uSyncCheckpoint(finalAppHash, finalFileHash),
            )
            currentFile?.let(::noteFile)
            processedFiles += written.uri.toString()
            processedPlaylists += playlist.id
            exported++
        }

        // Reconcile known links first. A marker can recover a file whose provider changed its URI.
        config.links.values.forEach linkLoop@ { link ->
            val playlist = playlists[link.playlistId]
            val occupyingPlaylist = allPlaylistsById[link.playlistId]
            val unreadableLinkedDocuments = listOfNotNull(
                    unreadableDocuments.firstOrNull { it.uri.toString() == link.documentUri },
                )
                .plus(unreadableDocumentsByName[link.fileName.lowercase()].orEmpty())
                .distinctBy { it.uri }
            if (unreadableLinkedDocuments.isNotEmpty()) {
                noteConflict(
                    name = playlist?.name ?: occupyingPlaylist?.name ?: link.fileName,
                    playlistId = playlist?.id,
                )
                return@linkLoop
            }
            val markerFiles = filesByMarker[link.playlistId].orEmpty()
                .filterNot { it.document.uri.toString() in processedFiles }
            val configuredFile = filesByUri[link.documentUri]
            val configuredFileUri = configuredFile?.document?.uri?.toString()
            if (configuredFileUri != null && configuredFileUri in processedFiles) {
                noteConflict(
                    name = playlist?.name ?: occupyingPlaylist?.name ?: link.fileName,
                    playlistId = playlist?.id,
                )
                return@linkLoop
            }
            val linkedFile = configuredFile
                ?.takeIf { linkedFile ->
                    linkedFile.document.uri.toString() !in processedFiles &&
                        (
                            linkedFile.parsed.playlistId == null ||
                                linkedFile.parsed.playlistId == link.playlistId
                        )
                }
            if (linkedFile == null && markerFiles.size > 1) {
                noteConflict(
                    name = playlist?.name ?: occupyingPlaylist?.name ?: link.fileName,
                    playlistId = playlist?.id,
                    files = markerFiles,
                )
                return@linkLoop
            }
            val file = linkedFile ?: markerFiles.singleOrNull()

            when {
                playlist == null && file == null -> links.remove(link.playlistId)
                playlist == null && file != null -> {
                    when {
                        occupyingPlaylist != null -> noteConflict(
                            name = occupyingPlaylist.name,
                            files = listOf(file),
                        )
                        !M3uSyncSafety.canImportEntireFile(file.parsed) -> noteConflict(
                            name = playlistNameFromFile(file.document.name),
                            files = listOf(file),
                        )
                        else -> importFile(
                            file = file,
                            existing = null,
                            requestedId = link.playlistId,
                        )
                    }
                }
                playlist != null && file == null -> {
                    val completeSongs = resolvedSongs(playlist)
                    if (completeSongs == null) {
                        noteConflict(playlist.name, playlist.id)
                    } else {
                        exportPlaylist(playlist, completeSongs, currentFile = null)
                    }
                }
                playlist != null && file != null -> {
                    val completeSongs = resolvedSongs(playlist)
                    if (completeSongs == null) {
                        noteConflict(playlist.name, playlist.id, listOf(file))
                        return@linkLoop
                    }
                    if (file.document.name.isM3uBackupFileName()) {
                        if (
                            M3uSyncSafety.canImportEntireFile(file.parsed) &&
                            file.parsed.songIds == playlist.songIds
                        ) {
                            exportPlaylist(playlist, completeSongs, file)
                        } else {
                            noteConflict(playlist.name, playlist.id, listOf(file))
                        }
                        return@linkLoop
                    }
                    val appHash = appHash(playlist, completeSongs)
                    val action = M3uSyncPlanner.decide(appHash, file.rawHash, link.checkpoint)
                    if (
                        action != M3uSyncAction.NONE &&
                        !M3uSyncSafety.canImportEntireFile(file.parsed)
                    ) {
                        noteConflict(playlist.name, playlist.id, listOf(file))
                        return@linkLoop
                    }
                    when (action) {
                        M3uSyncAction.NONE -> {
                            links[playlist.id] = link.copy(
                                documentUri = file.document.uri.toString(),
                                fileName = file.document.name,
                                checkpoint = M3uSyncCheckpoint(appHash, file.rawHash),
                            )
                            processedPlaylists += playlist.id
                            noteFile(file)
                            unchanged++
                        }
                        M3uSyncAction.EXPORT -> exportPlaylist(playlist, completeSongs, file)
                        M3uSyncAction.IMPORT -> importFile(file, playlist)
                        M3uSyncAction.CONFLICT -> noteConflict(
                            playlist.name,
                            playlist.id,
                            listOf(file),
                        )
                    }
                }
            }
        }

        // New app playlists either adopt their unique identity-marked file or get a new export.
        playlists.values.filterNot { it.id in processedPlaylists }.forEach playlistLoop@ { playlist ->
            if (unreadableDocumentsByName[desiredFileName(playlist.name).lowercase()].orEmpty().isNotEmpty()) {
                noteConflict(playlist.name, playlist.id)
                return@playlistLoop
            }
            val markerFiles = filesByMarker[playlist.id].orEmpty()
                .filterNot { it.document.uri.toString() in processedFiles }
            val completeSongs = resolvedSongs(playlist)
            if (completeSongs == null) {
                noteConflict(playlist.name, playlist.id, markerFiles)
                return@playlistLoop
            }
            when {
                markerFiles.size > 1 -> {
                    noteConflict(playlist.name, playlist.id, markerFiles)
                }
                markerFiles.size == 1 -> {
                    val file = markerFiles.single()
                    val sameSongs = M3uSyncSafety.canImportEntireFile(file.parsed) &&
                        file.parsed.songIds == playlist.songIds
                    if (sameSongs) {
                        val appHash = appHash(playlist, completeSongs)
                        links[playlist.id] = M3uSyncLink(
                            playlistId = playlist.id,
                            documentUri = file.document.uri.toString(),
                            fileName = file.document.name,
                            checkpoint = M3uSyncCheckpoint(appHash, file.rawHash),
                        )
                        processedPlaylists += playlist.id
                        noteFile(file)
                        unchanged++
                    } else {
                        noteConflict(playlist.name, playlist.id, listOf(file))
                    }
                }
                else -> exportPlaylist(playlist, completeSongs, currentFile = null)
            }
        }

        // Every complete remaining external file is imported. An occupied marker is never reused.
        parsedFiles.filterNot { it.document.uri.toString() in processedFiles }.forEach { file ->
            val markerId = file.parsed.playlistId
            val occupyingPlaylist = markerId?.let(allPlaylistsById::get)
            when {
                !M3uSyncSafety.canImportEntireFile(file.parsed) -> noteConflict(
                    playlistNameFromFile(file.document.name),
                    files = listOf(file),
                )
                markerId != null && markerId in occupiedPlaylistIds -> noteConflict(
                    occupyingPlaylist?.name ?: playlistNameFromFile(file.document.name),
                    files = listOf(file),
                )
                else -> importFile(file, existing = null, requestedId = markerId)
            }
        }

        if (
            !preferences.replaceLinksIfSelection(
                expectedTreeUri = expectedTreeUri,
                expectedRevision = config.revision,
                links = links,
            )
        ) {
            throw IOException("Playlist sync folder changed during reconciliation")
        }
        return M3uSyncReport(
            exported = exported,
            imported = imported,
            unchanged = unchanged,
            conflicts = conflicts.distinct(),
            unresolvedEntries = unresolved,
            skippedFiles = skipped.coerceAtLeast(0),
        )
    }

    private fun isSyncablePlaylist(playlist: Playlist): Boolean =
        !playlist.isQueueGenerated && !playlist.isSmartPlaylist && playlist.source == "LOCAL"

    private fun appHash(playlist: Playlist, completeSongs: List<Song>): String {
        val name = desiredFileName(playlist.name)
        val content = M3uManager.generateContent(playlist, completeSongs)
        return sha256("$name\n$content")
    }

    private fun listM3uDocuments(treeUri: Uri): List<M3uDocument> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val result = mutableListOf<M3uDocument>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeIndex)
                val name = cursor.getString(nameIndex) ?: continue
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR || !name.isM3uFileName()) continue
                result += M3uDocument(
                    uri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(idIndex),
                    ),
                    name = name,
                )
            }
        } ?: throw IOException("The selected playlist folder is unavailable")
        return result
    }

    private fun readBoundedText(uri: Uri): String {
        val reader = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
            ?: throw IOException("Unable to open playlist file")
        return reader.use { readM3uTextBounded(it) }
    }

    private fun writeDocument(
        treeUri: Uri,
        current: M3uDocument?,
        desiredName: String,
        content: String,
    ): M3uDocument {
        val names = m3uReplacementNames(desiredName, UUID.randomUUID().toString())
        val temporary = createTemporaryDocument(treeUri, names.temporary)
        var temporaryUriForCleanup: Uri? = temporary.uri

        try {
            if (temporary.name.isM3uFileName()) {
                throw IOException("Storage provider changed the temporary playlist name")
            }
            writeAndClose(temporary.uri, content)

            if (current == null) {
                val created = renameRequired(temporary, desiredName)
                temporaryUriForCleanup = null
                if (!created.name.isM3uFileName()) {
                    deleteBestEffort(created, "unrecognized replacement")
                    throw IOException("Storage provider changed the playlist file name")
                }
                return created
            }

            val backup = renameRequired(current, names.backup)
            var replacement: M3uDocument? = null
            try {
                replacement = renameRequired(temporary, desiredName)
                temporaryUriForCleanup = null
                if (!replacement.name.isM3uFileName()) {
                    throw IOException("Storage provider changed the playlist file name")
                }
            } catch (error: Exception) {
                val restored = renameBestEffort(backup, current.name)
                if (restored != null) {
                    replacement?.let { deleteBestEffort(it, "failed replacement") }
                } else {
                    Timber.e(
                        error,
                        "Could not restore %s; original content remains at %s",
                        current.name,
                        backup.uri,
                    )
                }
                throw error
            }

            cleanupBackupAfterCommit(backup)
            return checkNotNull(replacement)
        } catch (error: Exception) {
            temporaryUriForCleanup?.let { temporaryUri ->
                deleteBestEffort(M3uDocument(temporaryUri, names.temporary), "temporary write")
            }
            when (error) {
                is IOException -> throw error
                is SecurityException -> throw error
                else -> throw IOException("Unable to replace playlist file safely", error)
            }
        }
    }

    private fun createTemporaryDocument(treeUri: Uri, displayName: String): M3uDocument {
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val created = DocumentsContract.createDocument(
            resolver,
            rootDocumentUri,
            // A playlist MIME type makes the system provider append .m3u to our .tmp name.
            // Keep incomplete writes outside playlist scans until the final rename.
            "application/octet-stream",
            displayName,
        ) ?: throw IOException("Unable to create temporary playlist file")
        return M3uDocument(created, displayName(created, displayName))
    }

    private fun writeAndClose(uri: Uri, content: String) {
        val output = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw IOException("Unable to open temporary playlist file")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    private fun renameRequired(document: M3uDocument, desiredName: String): M3uDocument {
        val renamed = DocumentsContract.renameDocument(resolver, document.uri, desiredName)
            ?: throw IOException("Storage provider cannot safely rename playlist files")
        return M3uDocument(renamed, displayName(renamed, desiredName))
    }

    private fun renameBestEffort(document: M3uDocument, desiredName: String): M3uDocument? =
        runCatching { renameRequired(document, desiredName) }
            .onFailure { error ->
                Timber.e(error, "Unable to rename playlist document %s", document.uri)
            }
            .getOrNull()

    private fun cleanupBackupAfterCommit(backup: M3uDocument) {
        if (deleteBestEffort(backup, "committed backup")) return
        val hiddenName = backup.name.substringBeforeLast('.', backup.name)
            .take(100)
            .plus(".bak")
        val hidden = renameBestEffort(backup, hiddenName)
        if (hidden == null) {
            Timber.w("Original playlist backup remains recoverable at %s", backup.uri)
        } else {
            Timber.w("Original playlist backup remains recoverable at %s", hidden.uri)
        }
    }

    private fun deleteBestEffort(document: M3uDocument, purpose: String): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, document.uri) }
            .onFailure { error ->
                Timber.w(error, "Unable to remove %s document %s", purpose, document.uri)
            }
            .getOrDefault(false)

    private fun releaseTreePermission(treeUri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(treeUri, TREE_PERMISSION_FLAGS)
        }.onFailure { error ->
            Timber.w(error, "Unable to release M3U tree permission %s", treeUri)
        }
    }

    private fun displayName(uri: Uri, fallback: String): String = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: fallback

    private companion object {
        val TREE_PERMISSION_FLAGS: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        fun desiredFileName(playlistName: String): String {
            val stem = playlistName
                .replace(Regex("[\\u0000-\\u001F\\\\/:*?\"<>|]"), "_")
                .trim(' ', '.')
                .take(120)
                .ifBlank { "Playlist" }
            return "$stem.m3u8"
        }

        fun playlistNameFromFile(fileName: String): String = fileName
            .substringBeforeLast('.', fileName)
            .substringBefore(".pixelplayer-backup-")
            .trim()
            .ifBlank { "Imported Playlist" }

        fun String.isM3uFileName(): Boolean =
            endsWith(".m3u", ignoreCase = true) || endsWith(".m3u8", ignoreCase = true)

        fun String.isM3uBackupFileName(): Boolean = contains(".pixelplayer-backup-")
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
