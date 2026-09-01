package com.lostf1sh.pixelplayeross.data.playlist

import android.content.Context
import android.net.Uri
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.Reader
import javax.inject.Inject
import javax.inject.Singleton

internal const val MAX_M3U_CHARACTERS = 8 * 1024 * 1024

internal fun readM3uTextBounded(
    reader: Reader,
    maxCharacters: Int = MAX_M3U_CHARACTERS,
): String {
    require(maxCharacters >= 0) { "maxCharacters must not be negative" }
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    val result = StringBuilder(minOf(maxCharacters, DEFAULT_BUFFER_SIZE))
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        if (count > maxCharacters - result.length) {
            throw IOException("Playlist file is too large")
        }
        result.append(buffer, 0, count)
    }
    return result.toString()
}

data class M3uParseResult(
    val playlistId: String?,
    val songIds: List<String>,
    val unresolvedEntries: List<String>,
    val ambiguousEntries: List<String>,
)

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) {

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        var playlistName = "Imported Playlist"
        val allSongs = musicRepository.getAllSongsOnce()
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use(::readM3uTextBounded)
            .orEmpty()
        val parsed = parseContent(content, allSongs)

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(playlistName, parsed.songIds)
    }

    fun generateM3u(playlist: Playlist, songs: List<Song>): String {
        return generateContent(playlist, songs)
    }

    companion object {
        private const val PLAYLIST_ID_MARKER = "#PIXELPLAYER-PLAYLIST-ID:"

        fun parseContent(content: String, songs: List<Song>): M3uParseResult {
            val exactMatches = songs
                .flatMap { song ->
                    listOf(song.path, song.contentUriString)
                        .filter(String::isNotBlank)
                        .map { entry -> normalizeEntry(entry) to song }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, matches) -> matches.distinctBy(Song::id) }
            val basenameMatches = songs
                .flatMap { song ->
                    listOf(song.path, song.contentUriString)
                        .filter(String::isNotBlank)
                        .map { entry -> normalizeEntry(entry).substringAfterLast('/') to song }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, matches) -> matches.distinctBy(Song::id) }

            var playlistId: String? = null
            val resolvedIds = mutableListOf<String>()
            val unresolved = mutableListOf<String>()
            val ambiguous = mutableListOf<String>()

            content.lineSequence().forEach { rawLine ->
                val line = rawLine.removePrefix("\uFEFF").trim()
                if (line.isBlank()) return@forEach
                if (line.startsWith(PLAYLIST_ID_MARKER)) {
                    playlistId = line.removePrefix(PLAYLIST_ID_MARKER)
                        .trim()
                        .takeIf { it.isNotBlank() && it.length <= 200 }
                    return@forEach
                }
                if (line.startsWith('#')) return@forEach

                val normalized = normalizeEntry(line)
                val exact = exactMatches[normalized].orEmpty()
                if (exact.size == 1) {
                    resolvedIds += exact.single().id
                    return@forEach
                }
                if (exact.size > 1) {
                    ambiguous += line
                    return@forEach
                }

                val basename = normalized.substringAfterLast('/')
                val candidates = basenameMatches[basename].orEmpty()
                when (candidates.size) {
                    1 -> resolvedIds += candidates.single().id
                    0 -> unresolved += line
                    else -> ambiguous += line
                }
            }

            return M3uParseResult(
                playlistId = playlistId,
                songIds = resolvedIds.toList(),
                unresolvedEntries = unresolved,
                ambiguousEntries = ambiguous,
            )
        }

        fun generateContent(playlist: Playlist, songs: List<Song>): String {
            val sb = StringBuilder()
            sb.append("#EXTM3U\n")
            sb.append(PLAYLIST_ID_MARKER).append(playlist.id).append('\n')
            for (song in songs) {
                sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
                val location = song.path.takeIf { it.isNotBlank() } ?: song.contentUriString
                if (location.isNotBlank()) sb.append(location).append('\n')
            }
            return sb.toString()
        }

        private fun normalizeEntry(value: String): String =
            value.trim().replace('\\', '/')
    }
}
