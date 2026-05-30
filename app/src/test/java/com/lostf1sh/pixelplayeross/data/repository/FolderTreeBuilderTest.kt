package com.lostf1sh.pixelplayeross.data.repository

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.FolderSongRow
import org.junit.jupiter.api.Test

class FolderTreeBuilderTest {

    private val builder = FolderTreeBuilder()

    @Test
    fun inferRemovableStorageRoots_usesSdCardVolumeRoot() {
        val rows = listOf(
            folderSong(parentDirectoryPath = "/storage/1234-5678/Music/Album")
        )

        val roots = builder.inferRemovableStorageRoots(
            folderSongs = rows,
            internalStorageRoot = "/storage/emulated/0",
            knownRemovableRoots = emptySet()
        )

        assertThat(roots).containsExactly("/storage/1234-5678")
    }

    @Test
    fun buildFolderTreeForRoots_includesSdCardFolders() {
        val rows = listOf(
            folderSong(
                id = 1L,
                parentDirectoryPath = "/storage/1234-5678/Music/Album",
                title = "First Song"
            )
        )

        val folders = builder.buildFolderTreeForRoots(
            folderSongs = rows,
            selectedRootPaths = setOf("/storage/1234-5678")
        )

        assertThat(folders.map { it.name }).containsExactly("Music")
        assertThat(folders.single().subFolders.map { it.name }).containsExactly("Album")
        assertThat(folders.single().totalSongCount).isEqualTo(1)
    }

    @Test
    fun buildFolderTreeForRoots_doesNotMatchSiblingPathPrefix() {
        val rows = listOf(
            folderSong(
                id = 1L,
                parentDirectoryPath = "/storage/emulated/0/Music",
                title = "Internal Song"
            ),
            folderSong(
                id = 2L,
                parentDirectoryPath = "/storage/emulated/0-other/Music",
                title = "Wrong Prefix Song"
            )
        )

        val folders = builder.buildFolderTreeForRoots(
            folderSongs = rows,
            selectedRootPaths = setOf("/storage/emulated/0")
        )

        assertThat(folders).hasSize(1)
        assertThat(folders.single().songs.map { it.title }).containsExactly("Internal Song")
        assertThat(folders.single().totalSongCount).isEqualTo(1)
    }

    @Test
    fun inferRemovableStorageRoots_supportsMediaRwSdCardPaths() {
        val rows = listOf(
            folderSong(parentDirectoryPath = "/mnt/media_rw/1234-5678/Music")
        )

        val roots = builder.inferRemovableStorageRoots(
            folderSongs = rows,
            internalStorageRoot = "/storage/emulated/0",
            knownRemovableRoots = emptySet()
        )

        assertThat(roots).containsExactly("/mnt/media_rw/1234-5678")
    }

    private fun folderSong(
        id: Long = 1L,
        parentDirectoryPath: String,
        title: String = "Song"
    ): FolderSongRow = FolderSongRow(
        id = id,
        parentDirectoryPath = parentDirectoryPath,
        title = title,
        albumArtUriString = null
    )
}
