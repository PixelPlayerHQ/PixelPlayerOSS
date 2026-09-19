package com.lostf1sh.pixelplayeross.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FolderArtistArtUtilsTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `finds artist portrait in parent of album folder with case insensitive name`() {
        val album = directory("Music/Daft Punk/Discovery")
        val image = album.parentFile!!.resolve("ArTiSt.PnG").apply { writeText("image") }

        assertEquals(image.canonicalFile, findImage(album))
    }

    @Test
    fun `accepts band portrait beside tracks`() {
        val artist = directory("Music/Daft Punk")
        val image = artist.resolve("band.jpg").apply { writeText("image") }

        assertEquals(image.canonicalFile, findImage(artist))
    }

    @Test
    fun `artist directory portrait wins over album directory portrait`() {
        val album = directory("Music/Daft Punk/Discovery")
        val image = album.parentFile!!.resolve("artist.jpg").apply { writeText("image") }
        album.resolve("artist.png").writeText("image")

        assertEquals(image.canonicalFile, findImage(album))
    }

    @Test
    fun `skips invalid candidate and tries remaining explicit portrait names`() {
        val album = directory("Music/Daft Punk/Discovery")
        album.parentFile!!.resolve("artist.jpg").writeText("invalid")
        val image = album.parentFile!!.resolve("band.png").apply { writeText("image") }

        assertEquals(image.canonicalFile, findImage(album))
    }

    @Test
    fun `does not use album covers as artist portraits`() {
        val album = directory("Music/Daft Punk/Discovery")
        album.resolve("cover.jpg").writeText("image")
        album.parentFile!!.resolve("folder.jpg").writeText("image")

        assertNull(findImage(album))
    }

    @Test
    fun `ignores shared music and downloads directory portraits`() {
        val album = directory("Music/Discovery")
        album.parentFile!!.resolve("artist.jpg").writeText("image")
        val downloads = directory("Downloads")
        downloads.resolve("band.png").writeText("image")

        assertNull(findImage(album, downloads))
    }

    @Test
    fun `does not use an unrelated grandparent portrait`() {
        val album = directory("Collection/Artist/Album")
        root.resolve("Collection/artist.jpg").toFile().writeText("image")

        assertNull(findImage(album, artistName = "Artist"))
    }

    @Test
    fun `finds named artist directory above a disc folder`() {
        val disc = directory("Music/Daft Punk/Discovery/Disc 1")
        val image = root.resolve("Music/Daft Punk/band.png").toFile().apply { writeText("image") }

        assertEquals(image.canonicalFile, findImage(disc))
    }

    @Test
    fun `does not inherit another artist portrait for a collaborator`() {
        val album = directory("Music/Daft Punk/Discovery")
        album.parentFile!!.resolve("artist.jpg").writeText("image")

        assertNull(findImage(album, artistName = "Guest Singer"))
    }

    @Test
    fun `rejects portrait symlinks escaping the inspected directory`() {
        val album = directory("Music/Artist/Album")
        val outside = root.resolve("private.jpg").toFile().apply { writeText("image") }
        Files.createSymbolicLink(album.parentFile!!.resolve("artist.jpg").toPath(), outside.toPath())

        assertNull(findImage(album, artistName = "Artist"))
    }

    @Test
    fun `considers multiple album directories and ignores missing directories`() {
        val first = directory("Music/Artist/First Album")
        val second = directory("Other Music/Artist/Second Album")
        val image = second.parentFile!!.resolve("artist.png").apply { writeText("image") }

        assertEquals(
            image.canonicalFile,
            findImage(root.resolve("missing").toFile(), first, second, artistName = "Artist")
        )
    }

    private fun directory(path: String): File = root.resolve(path).toFile().apply { mkdirs() }

    private fun findImage(vararg directories: File, artistName: String = "Daft Punk"): File? = FolderArtistArtUtils.findArtistImage(
        audioDirectoryPaths = directories.map { it.absolutePath },
        artistName = artistName,
        isUsableArtwork = { it.readText() == "image" }
    )
}
