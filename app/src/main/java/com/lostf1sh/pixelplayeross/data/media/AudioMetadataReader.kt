package com.lostf1sh.pixelplayeross.data.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.wav.WavTag
import timber.log.Timber
import java.io.File

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    /** Every physical ARTIST field, in tag order. [artist] remains the primary value. */
    val artists: List<String>,
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val composer: String?,
    val lyrics: String?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val artwork: AudioMetadataArtwork?,
    val replayGainTrackGainDb: Float? = null,
    val replayGainAlbumGainDb: Float? = null,
    val rating: Int? = null,
    val customFields: List<CustomMetadataField> = emptyList()
)

/**
 * Normalizes values read from repeated metadata fields without changing their source order.
 *
 * Vorbis comments and some ID3 writers can store ARTIST more than once. Keeping this helper
 * format-agnostic lets both TagLib's property map and JAudioTagger's field list use the exact
 * same case-insensitive de-duplication policy.
 */
internal fun normalizeArtistMetadataValues(
    vararg sources: Iterable<String>?
): List<String> {
    val result = mutableListOf<String>()
    sources.forEach { source ->
        source?.forEach { value ->
            val normalized = value.trim()
            if (normalized.isNotEmpty() && result.none { it.equals(normalized, ignoreCase = true) }) {
                result += normalized
            }
        }
    }
    return result
}

data class AudioMetadataArtwork(
    val bytes: ByteArray,
    val mimeType: String?
)

object AudioMetadataReader {

    private const val TAG = "AudioMetadataReader"

    fun read(context: Context, uri: Uri): AudioMetadata? {
        val tempFile = createTempAudioFileFromUri(context, uri) ?: run {
            Timber.tag(TAG).w("Unable to create temp file for uri: $uri")
            return null
        }

        return try {
            read(tempFile)
        } finally {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to delete temp file")
            }
        }
    }

    fun read(
        file: File,
        readArtwork: Boolean = true,
        readCustomMetadata: Boolean = false
    ): AudioMetadata? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())
                val durationMs = audioProperties?.length?.takeIf { it > 0 }?.let { it * 1000L }
                val bitrate = audioProperties?.bitrate?.takeIf { it > 0 }?.let { it * 1000 }
                val sampleRate = audioProperties?.sampleRate?.takeIf { it > 0 }

                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val propertyMap = metadata?.propertyMap ?: emptyMap()

                Timber.tag(TAG).w("TagLib propertyMap keys for ${file.name}: ${propertyMap.keys}")

                val title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val artists = normalizeArtistMetadataValues(propertyMap["ARTIST"]?.asIterable())
                val artist = artists.firstOrNull()
                val albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["ALBUM ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["BAND"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val composer = propertyMap["COMPOSER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["TCOM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val lyrics = propertyMap["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["UNSYNCEDLYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackString = propertyMap["TRACKNUMBER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["TRACK"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackNumber = trackString?.substringBefore('/')?.toIntOrNull()
                val discString = propertyMap["DISCNUMBER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["DISC"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val discNumber = discString?.substringBefore('/')?.toIntOrNull()
                val year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()
                    ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.toIntOrNull()
                val replayGainTrackGainDb = extractReplayGainDb(
                    propertyMap = propertyMap,
                    keys = listOf("REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_GAIN_DB", "R128_TRACK_GAIN")
                )
                val replayGainAlbumGainDb = extractReplayGainDb(
                    propertyMap = propertyMap,
                    keys = listOf("REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_GAIN_DB", "R128_ALBUM_GAIN")
                )
                val tagFamily = metadataTagFamily(file.extension)
                val propertyMapRating = if (readCustomMetadata) {
                    listOf("RATING", "POPULARIMETER")
                        .firstNotNullOfOrNull { key -> propertyMap[key]?.firstOrNull() }
                        ?.let { decodeRatingFromTag(it, tagFamily) }
                } else {
                    null
                }
                val customFields = if (readCustomMetadata) {
                    extractEditableCustomMetadataFields(propertyMap)
                } else {
                    emptyList()
                }

                Timber.tag(TAG).w("TagLib result for ${file.name}: title=$title, artist=$artist, album=$album, genre=$genre")

                val artwork = if (readArtwork) {
                    val pictures = TagLib.getPictures(fd.detachFd())
                    pictures.firstOrNull()?.let { picture ->
                        picture.data.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                            AudioMetadataArtwork(
                                bytes = data,
                                mimeType = picture.mimeType.takeIf { it.isNotBlank() } ?: guessImageMimeType(data)
                            )
                        }
                    }
                } else {
                    null
                }

                val fallback = if (
                    title == null || artist == null || (readArtwork && artwork == null) || readCustomMetadata
                ) {
                    Timber.tag(TAG).w("TagLib incomplete for ${file.name}, trying JAudioTagger fallback...")
                    readWithJAudioTagger(file, readCustomMetadata = readCustomMetadata)
                } else null

                val resolvedArtists = artists.ifEmpty { fallback?.artists.orEmpty() }

                AudioMetadata(
                    title = title ?: fallback?.title,
                    artist = resolvedArtists.firstOrNull() ?: artist ?: fallback?.artist,
                    artists = resolvedArtists,
                    albumArtist = albumArtist ?: fallback?.albumArtist,
                    album = album ?: fallback?.album,
                    genre = genre ?: fallback?.genre,
                    composer = composer ?: fallback?.composer,
                    lyrics = lyrics ?: fallback?.lyrics,
                    durationMs = durationMs ?: fallback?.durationMs,
                    trackNumber = trackNumber ?: fallback?.trackNumber,
                    discNumber = discNumber ?: fallback?.discNumber,
                    year = year ?: fallback?.year,
                    bitrate = bitrate ?: fallback?.bitrate,
                    sampleRate = sampleRate ?: fallback?.sampleRate,
                    artwork = artwork ?: fallback?.artwork,
                    replayGainTrackGainDb = replayGainTrackGainDb ?: fallback?.replayGainTrackGainDb,
                    replayGainAlbumGainDb = replayGainAlbumGainDb ?: fallback?.replayGainAlbumGainDb,
                    rating = fallback?.rating ?: propertyMapRating,
                    customFields = customFields.ifEmpty { fallback?.customFields.orEmpty() }
                )
            }
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Unable to read metadata from file: ${file.absolutePath}")
            null
        }
    }

    /**
     * Fallback reader using JAudioTagger for files where TagLib can't map ID3 frames.
     * Called when TagLib leaves key metadata or requested artwork unresolved.
     */
    private fun readWithJAudioTagger(
        file: File,
        readCustomMetadata: Boolean = false
    ): AudioMetadata? {
        return try {
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            Timber.tag(TAG).w("JAudioTagger: tag class=${tag?.javaClass?.simpleName}, " +
                    "header=${header?.format}, sampleRate=${header?.sampleRateAsNumber}")

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            val artists = normalizeArtistMetadataValues(
                runCatching { tag?.getAll(FieldKey.ARTIST) }.getOrNull(),
                listOfNotNull(
                    runCatching { tag?.getFirst(FieldKey.ARTIST) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                )
            )
            val artist = artists.firstOrNull()
            val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
            val genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }
            val composer = tag?.getFirst(FieldKey.COMPOSER)?.takeIf { it.isNotBlank() }
            val lyrics = tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
            val trackNumber = tag?.getFirst(FieldKey.TRACK)?.takeIf { it.isNotBlank() }
                ?.substringBefore('/')?.toIntOrNull()
            val discNumber = tag?.getFirst(FieldKey.DISC_NO)?.takeIf { it.isNotBlank() }
                ?.substringBefore('/')?.toIntOrNull()
            val year = tag?.getFirst(FieldKey.YEAR)?.takeIf { it.isNotBlank() }
                ?.take(4)?.toIntOrNull()
            val rating = if (readCustomMetadata) {
                val actualTagFamily = when (tag) {
                    is AbstractID3v2Tag, is WavTag -> MetadataTagFamily.ID3
                    is FlacTag, is VorbisCommentTag -> MetadataTagFamily.VORBIS
                    is Mp4Tag -> MetadataTagFamily.MP4
                    else -> metadataTagFamily(file.extension)
                }
                runCatching { tag?.getFirst(FieldKey.RATING) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { decodeRatingFromTag(it, actualTagFamily) }
            } else {
                null
            }

            val durationMs = header?.trackLength?.takeIf { it > 0 }?.let { it * 1000L }
            val bitrate = header?.bitRateAsNumber?.takeIf { it > 0 }?.toInt()?.let { it * 1000 }
            val sampleRate = header?.sampleRateAsNumber?.takeIf { it > 0 }

            val artwork = tag?.firstArtwork?.let { art ->
                art.binaryData?.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                    AudioMetadataArtwork(
                        bytes = data,
                        mimeType = art.mimeType?.takeIf { it.isNotBlank() } ?: guessImageMimeType(data)
                    )
                }
            }

            Timber.tag(TAG).w("JAudioTagger result for ${file.name}: title=$title, artist=$artist, " +
                    "album=$album, genre=$genre, artwork=${artwork != null}")

            AudioMetadata(
                title = title,
                artist = artist,
                artists = artists,
                albumArtist = albumArtist,
                album = album,
                genre = genre,
                composer = composer,
                lyrics = lyrics,
                durationMs = durationMs,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                bitrate = bitrate,
                sampleRate = sampleRate,
                artwork = artwork,
                rating = rating
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "JAudioTagger fallback FAILED for: ${file.name}")
            null
        }
    }

    private fun extractReplayGainDb(
        propertyMap: Map<String, Array<String>>,
        keys: List<String>
    ): Float? {
        for (key in keys) {
            val rawValue = propertyMap[key]?.firstOrNull() ?: continue
            val parsedValue = parseReplayGainDb(rawValue)
            if (parsedValue != null) {
                return parsedValue
            }
        }
        return null
    }

    private fun parseReplayGainDb(rawValue: String?): Float? {
        val cleanedValue = rawValue
            ?.trim()
            ?.replace(',', '.')
            ?.replace(Regex("(?i)[dD][bB]"), "")
            ?.trim()
            ?: return null
        return cleanedValue.toFloatOrNull()
    }
}
