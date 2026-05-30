package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import android.os.Environment
import com.lostf1sh.pixelplayeross.data.database.FolderSongRow
import com.lostf1sh.pixelplayeross.data.model.FolderSource
import com.lostf1sh.pixelplayeross.data.model.MusicFolder
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.DirectoryRuleResolver
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import com.lostf1sh.pixelplayeross.utils.StorageType
import com.lostf1sh.pixelplayeross.utils.StorageUtils
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderTreeBuilder @Inject constructor() {

    fun buildFolderTree(
        folderSongs: List<FolderSongRow>,
        allowedDirs: Set<String>,
        blockedDirs: Set<String>,
        isFolderFilterActive: Boolean,
        folderSource: FolderSource,
        context: Context
    ): List<MusicFolder> {
        // 1. Resolve Rules
        val resolver = DirectoryRuleResolver(allowedDirs, blockedDirs)

        // 2. Filter Songs based on Directory Rules
        val filteredSongs = if (isFolderFilterActive && blockedDirs.isNotEmpty()) {
            folderSongs.filter { song ->
                val normalizedParent = normalizePath(song.parentDirectoryPath)
                normalizedParent.isNotBlank() && !resolver.isBlocked(normalizedParent)
            }
        } else {
            folderSongs
        }

        if (filteredSongs.isEmpty()) return emptyList()

        // 3. Determine Root Path based on Source
        val storages = StorageUtils.getAvailableStorages(context)
        val internalStorageRoot = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: Environment.getExternalStorageDirectory().path
        val removableStorageRoots = storages
            .filter { it.storageType != StorageType.INTERNAL }
            .map { it.path.path }
            .toSet()
        val sdStorageRoot = StorageUtils.getSdCardStorage(context)?.path?.path

        val selectedRootPaths = when (folderSource) {
            FolderSource.INTERNAL -> setOf(internalStorageRoot)
            FolderSource.SD_CARD -> buildSet {
                sdStorageRoot?.let(::add)
                addAll(removableStorageRoots)
                addAll(
                    inferRemovableStorageRoots(
                        folderSongs = filteredSongs,
                        internalStorageRoot = internalStorageRoot,
                        knownRemovableRoots = removableStorageRoots + listOfNotNull(sdStorageRoot)
                    )
                )
            }
        }

        return buildFolderTreeForRoots(
            folderSongs = filteredSongs,
            selectedRootPaths = selectedRootPaths
        )
    }

    internal fun buildFolderTreeForRoots(
        folderSongs: List<FolderSongRow>,
        selectedRootPaths: Set<String>
    ): List<MusicFolder> {
        val normalizedRoots = normalizeRootPaths(selectedRootPaths)
        if (normalizedRoots.isEmpty()) return emptyList()

        return normalizedRoots
            .flatMap { rootPath ->
                buildFolderTreeForRoot(
                    folderSongs = folderSongs,
                    selectedRootPath = rootPath
                )
            }
            .filter { it.totalSongCount > 0 }
            .sortedBy { it.name.lowercase() }
    }

    private fun buildFolderTreeForRoot(
        folderSongs: List<FolderSongRow>,
        selectedRootPath: String
    ): List<MusicFolder> {
        val normalizedSelectedRoot = normalizePath(selectedRootPath)

        val songsToProcess = folderSongs.filter { song ->
            isPathAtOrUnder(
                path = normalizePath(song.parentDirectoryPath),
                root = normalizedSelectedRoot
            )
        }

        if (songsToProcess.isEmpty()) return emptyList()
        
        val folderMap = mutableMapOf<String, TempFolder>()
        val rootFolder = getOrCreateTempFolder(normalizedSelectedRoot, folderMap, getNameFromPath(normalizedSelectedRoot))

        for (song in songsToProcess) {
            val parentPath = normalizePath(song.parentDirectoryPath)
            if (parentPath.isBlank()) continue

            // Get or create the folder for this song
            val folder = getOrCreateTempFolder(parentPath, folderMap, getNameFromPath(parentPath))
            folder.songs.add(song.toFolderStubSong())
            
            // Ensure hierarchy
            var currentPath = parentPath
            while (currentPath.length > normalizedSelectedRoot.length && isPathAtOrUnder(currentPath, normalizedSelectedRoot)) {
                val parentOfCurrent = getParentPath(currentPath) ?: break
                
                // If we went above root, stop
                if (!isPathAtOrUnder(parentOfCurrent, normalizedSelectedRoot)) break
                
                val parentFolder = getOrCreateTempFolder(parentOfCurrent, folderMap, getNameFromPath(parentOfCurrent))
                val added = parentFolder.subFolderPaths.add(currentPath)
                
                if (!added) break 
                
                currentPath = parentOfCurrent
            }
        }
        
        return rootFolder.subFolderPaths
            .mapNotNull { path -> buildImmutableFolder(path, folderMap) }
            .filter { it.totalSongCount > 0 }
            .sortedBy { it.name.lowercase() }
    }

    internal fun inferRemovableStorageRoots(
        folderSongs: List<FolderSongRow>,
        internalStorageRoot: String,
        knownRemovableRoots: Set<String>
    ): Set<String> {
        val normalizedInternalRoot = normalizePath(internalStorageRoot)
        val normalizedKnownRemovableRoots = normalizeRootPaths(knownRemovableRoots)

        return folderSongs.mapNotNull { song ->
            val parentPath = normalizePath(song.parentDirectoryPath)
            if (parentPath.isBlank() || isPathAtOrUnder(parentPath, normalizedInternalRoot)) {
                return@mapNotNull null
            }

            normalizedKnownRemovableRoots.firstOrNull { root ->
                isPathAtOrUnder(parentPath, root)
            } ?: inferStorageRootFromPath(parentPath)
        }.toSet()
    }

    private fun inferStorageRootFromPath(path: String): String? {
        val parts = path.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        return when {
            parts[0] == "storage" && parts.size >= 3 && parts[1] == "emulated" ->
                "/storage/emulated/${parts[2]}"
            parts[0] == "storage" && parts.size >= 2 ->
                "/storage/${parts[1]}"
            parts.size >= 3 && parts[0] == "mnt" && parts[1] == "media_rw" ->
                "/mnt/media_rw/${parts[2]}"
            parts.size >= 2 && parts[0] == "mnt" ->
                "/mnt/${parts[1]}"
            parts[0].startsWith("sdcard", ignoreCase = true) ->
                "/${parts[0]}"
            else -> null
        }
    }

    private fun normalizeRootPaths(rootPaths: Set<String>): List<String> {
        val normalizedRoots = rootPaths
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .distinct()

        return normalizedRoots.filterNot { candidate ->
            normalizedRoots.any { other ->
                other != candidate && isPathAtOrUnder(path = other, root = candidate)
            }
        }
    }

    private fun isPathAtOrUnder(path: String, root: String): Boolean {
        if (path == root) return true
        return path.startsWith("$root/")
    }

    private fun getOrCreateTempFolder(path: String, map: MutableMap<String, TempFolder>, name: String): TempFolder {
        return map.getOrPut(path) {
            TempFolder(path, name)
        }
    }

    private fun buildImmutableFolder(path: String, map: Map<String, TempFolder>): MusicFolder? {
        val temp = map[path] ?: return null
        
        // Recursively build subfolders
        val subFolders = temp.subFolderPaths
            .mapNotNull { subPath -> buildImmutableFolder(subPath, map) }
            .sortedBy { it.name.lowercase() }
            .toImmutableList()
            
        return MusicFolder(
            path = temp.path,
            name = temp.name,
            songs = temp.songs
                .sortedWith(
                    compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
                .toImmutableList(),
            subFolders = subFolders
        )
    }

    private fun getParentPath(path: String): String? {
        val lastSeparatorIndex = path.lastIndexOf('/')
        if (lastSeparatorIndex <= 0) return null 
        return path.substring(0, lastSeparatorIndex)
    }

    private fun getNameFromPath(path: String): String {
        val lastSeparatorIndex = path.lastIndexOf('/')
        if (lastSeparatorIndex == -1) return path
        return path.substring(lastSeparatorIndex + 1)
    }
    
    private fun normalizePath(path: String): String {
        return if (path.endsWith("/")) path.dropLast(1) else path
    }

    private fun FolderSongRow.toFolderStubSong(): Song {
        val parentPath = normalizePath(parentDirectoryPath)
        val syntheticPath = if (parentPath.isBlank()) title else "$parentPath/$title"
        val resolvedAlbumArtUri = when {
            LocalArtworkUri.isLocalArtworkUri(albumArtUriString) -> albumArtUriString
            id > 0L && LocalArtworkUri.looksLikeVolatileArtworkUri(albumArtUriString) ->
                LocalArtworkUri.buildSongUri(id)
            else -> albumArtUriString
        }
        return Song(
            id = id.toString(),
            title = title,
            artist = "",
            artistId = -1L,
            album = "",
            albumId = -1L,
            path = syntheticPath,
            contentUriString = "",
            albumArtUriString = resolvedAlbumArtUri,
            duration = 0L,
            trackNumber = 0,
            year = 0,
            dateAdded = 0L,
            dateModified = 0L,
            mimeType = null,
            bitrate = null,
            sampleRate = null
        )
    }

    private class TempFolder(
        val path: String,
        val name: String,
        val songs: MutableList<Song> = ArrayList(), 
        val subFolderPaths: MutableSet<String> = HashSet() 
    )
}
