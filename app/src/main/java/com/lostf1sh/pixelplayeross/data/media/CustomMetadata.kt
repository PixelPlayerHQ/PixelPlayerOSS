package com.lostf1sh.pixelplayeross.data.media

import java.util.Locale
import kotlin.math.roundToInt

/** A text metadata field that can be shown in the single-song editor. */
data class CustomMetadataField(
    val key: String,
    val value: String
)

/** A null [value] removes the field from the file. */
data class CustomMetadataFieldUpdate(
    val key: String,
    val value: String?
)

/**
 * Separates an intentional clear from leaving an unread or untouched tag alone.
 * This distinction matters when Android cannot read the original file before requesting write access.
 */
sealed interface MetadataValueUpdate<out T> {
    data object Keep : MetadataValueUpdate<Nothing>
    data object Clear : MetadataValueUpdate<Nothing>
    data class Set<T>(val value: T) : MetadataValueUpdate<T>
}

data class CustomMetadataChanges(
    val rating: MetadataValueUpdate<Int> = MetadataValueUpdate.Keep,
    val fields: List<CustomMetadataFieldUpdate> = emptyList()
) {
    val hasChanges: Boolean
        get() = rating !is MetadataValueUpdate.Keep || fields.isNotEmpty()
}

enum class MetadataTagFamily {
    ID3,
    VORBIS,
    MP4,
    UNSUPPORTED
}

private const val MAX_CUSTOM_FIELD_COUNT = 24
private const val MAX_CUSTOM_FIELD_KEY_LENGTH = 64
private const val MAX_CUSTOM_FIELD_VALUE_LENGTH = 4_096

/** Fields already owned by the fixed editor, artwork pipeline, or ReplayGain editor. */
private val RESERVED_CUSTOM_METADATA_KEYS = setOf(
    "TITLE",
    "ARTIST",
    "ALBUM",
    "ALBUMARTIST",
    "ALBUM ARTIST",
    "BAND",
    "COMPOSER",
    "GENRE",
    "LYRICS",
    "UNSYNCEDLYRICS",
    "TRACK",
    "TRACKNUMBER",
    "DISC",
    "DISCNUMBER",
    "DISC_NO",
    "TRACK_NO",
    "SINGLE_DISC_TRACK_NO",
    "RATING",
    "POPULARIMETER",
    "METADATA_BLOCK_PICTURE",
    "COVERART",
    "COVERARTMIME",
    "PICTURE",
    "REPLAYGAIN_TRACK_GAIN",
    "REPLAYGAIN_TRACK_GAIN_DB",
    "REPLAYGAIN_ALBUM_GAIN",
    "REPLAYGAIN_ALBUM_GAIN_DB",
    "R128_TRACK_GAIN",
    "R128_ALBUM_GAIN"
)

private val RESERVED_CUSTOM_METADATA_COMPACT_KEYS =
    RESERVED_CUSTOM_METADATA_KEYS.mapTo(mutableSetOf()) { key ->
        key.filter { it in 'A'..'Z' || it in '0'..'9' }
    }

private val HIDDEN_TECHNICAL_METADATA_PREFIXES = listOf(
    "ACOUSTID_",
    "MUSICBRAINZ_"
)

private val HIDDEN_TECHNICAL_METADATA_KEYS = setOf(
    "ENCODER",
    "ENCODED_BY",
    "LENGTH"
)

internal fun metadataTagFamily(extension: String): MetadataTagFamily = when (
    extension.trim().removePrefix(".").lowercase(Locale.ROOT)
) {
    "mp3", "wav", "aif", "aiff" -> MetadataTagFamily.ID3
    "flac", "ogg", "oga", "opus" -> MetadataTagFamily.VORBIS
    "m4a", "m4b", "mp4" -> MetadataTagFamily.MP4
    else -> MetadataTagFamily.UNSUPPORTED
}

/**
 * Encodes a user-facing 0–5 rating using the convention native to the container.
 * ID3 POPM stores a byte, MP4 score uses 0–100, and Vorbis comments use the star value.
 */
internal fun encodeRatingForTag(rating: Int, family: MetadataTagFamily): String {
    require(rating in 0..5) { "Rating must be between 0 and 5" }
    return when (family) {
        MetadataTagFamily.ID3 -> when (rating) {
            0 -> "0"
            1 -> "1"
            2 -> "64"
            3 -> "128"
            4 -> "196"
            else -> "255"
        }
        MetadataTagFamily.MP4 -> (rating * 20).toString()
        MetadataTagFamily.VORBIS -> rating.toString()
        MetadataTagFamily.UNSUPPORTED -> error("This file format does not support custom metadata")
    }
}

internal fun decodeRatingFromTag(rawValue: String?, family: MetadataTagFamily): Int? {
    val numericValue = rawValue?.trim()?.toDoubleOrNull() ?: return null
    if (!numericValue.isFinite() || numericValue < 0) return null

    // Some taggers write literal stars even in ID3/MP4. Accept those values before scaling.
    if (numericValue <= 5.0) return numericValue.roundToInt().coerceIn(0, 5)

    return when (family) {
        MetadataTagFamily.ID3 -> when {
            numericValue < 32 -> 1
            numericValue < 96 -> 2
            numericValue < 162 -> 3
            numericValue < 226 -> 4
            else -> 5
        }
        MetadataTagFamily.MP4,
        MetadataTagFamily.VORBIS -> (numericValue / 20.0).roundToInt().coerceIn(0, 5)
        MetadataTagFamily.UNSUPPORTED -> null
    }
}

internal fun validateCustomMetadataChanges(
    changes: CustomMetadataChanges
): Result<CustomMetadataChanges> = runCatching {
    val rating = changes.rating
    if (rating is MetadataValueUpdate.Set && rating.value !in 0..5) {
        throw IllegalArgumentException("Rating must be between 0 and 5")
    }
    if (changes.fields.size > MAX_CUSTOM_FIELD_COUNT) {
        throw IllegalArgumentException("A maximum of $MAX_CUSTOM_FIELD_COUNT custom fields is supported")
    }

    val seenKeys = mutableSetOf<String>()
    val normalizedFields = changes.fields.map { field ->
        val normalizedKey = normalizeCustomMetadataKey(field.key)
        if (isReservedCustomMetadataKey(normalizedKey)) {
            throw IllegalArgumentException("$normalizedKey is managed by the standard metadata editor")
        }
        if (!seenKeys.add(normalizedKey)) {
            throw IllegalArgumentException("Duplicate custom metadata field: $normalizedKey")
        }

        val normalizedValue = field.value?.trim()
        if (normalizedValue != null) {
            if (normalizedValue.isEmpty()) {
                throw IllegalArgumentException("Custom metadata values cannot be empty")
            }
            if (normalizedValue.length > MAX_CUSTOM_FIELD_VALUE_LENGTH) {
                throw IllegalArgumentException("Custom metadata value is too long")
            }
        }
        CustomMetadataFieldUpdate(normalizedKey, normalizedValue)
    }

    changes.copy(fields = normalizedFields)
}

private fun normalizeCustomMetadataKey(rawKey: String): String {
    val normalized = rawKey.trim().uppercase(Locale.ROOT)
    if (normalized.isEmpty()) {
        throw IllegalArgumentException("Custom metadata field name cannot be empty")
    }
    if (normalized.length > MAX_CUSTOM_FIELD_KEY_LENGTH) {
        throw IllegalArgumentException("Custom metadata field name is too long")
    }
    if (normalized.any { character ->
            character !in 'A'..'Z' &&
                character !in '0'..'9' &&
                character != ' ' &&
                character != '_' &&
                character != '-' &&
                character != '.'
        }
    ) {
        throw IllegalArgumentException("Custom metadata field names may use A-Z, 0-9, spaces, dots, dashes, and underscores")
    }
    return normalized
}

private fun isReservedCustomMetadataKey(key: String): Boolean {
    if (key in RESERVED_CUSTOM_METADATA_KEYS) return true
    val compactKey = key.filter { it in 'A'..'Z' || it in '0'..'9' }
    return compactKey in RESERVED_CUSTOM_METADATA_COMPACT_KEYS
}

internal fun buildCustomMetadataChanges(
    metadataWasRead: Boolean,
    originalRating: Int?,
    editedRating: Int?,
    originalFields: List<CustomMetadataField>,
    editedFields: List<CustomMetadataField>
): Result<CustomMetadataChanges> = runCatching {
    val ratingUpdate = when {
        !metadataWasRead && editedRating == null -> MetadataValueUpdate.Keep
        editedRating == originalRating -> MetadataValueUpdate.Keep
        editedRating == null -> MetadataValueUpdate.Clear
        else -> MetadataValueUpdate.Set(editedRating)
    }

    val normalizedOriginal = originalFields.associate { field ->
        normalizeCustomMetadataKey(field.key) to field.value.trim()
    }
    val normalizedEdited = linkedMapOf<String, String>()
    editedFields.forEach { field ->
        val key = normalizeCustomMetadataKey(field.key)
        if (normalizedEdited.containsKey(key)) {
            throw IllegalArgumentException("Duplicate custom metadata field: $key")
        }
        normalizedEdited[key] = field.value.trim()
    }

    val updates = buildList {
        normalizedEdited.forEach { (key, value) ->
            if (normalizedOriginal[key] != value) {
                add(CustomMetadataFieldUpdate(key, value))
            }
        }
        normalizedOriginal.keys
            .filterNot(normalizedEdited::containsKey)
            .forEach { removedKey -> add(CustomMetadataFieldUpdate(removedKey, null)) }
    }

    validateCustomMetadataChanges(
        CustomMetadataChanges(rating = ratingUpdate, fields = updates)
    ).getOrThrow()
}

internal fun extractEditableCustomMetadataFields(
    propertyMap: Map<String, Array<String>>
): List<CustomMetadataField> = propertyMap.mapNotNull { (rawKey, values) ->
    val portableKey = if (rawKey.startsWith("----:com.apple.iTunes:", ignoreCase = true)) {
        rawKey.substringAfterLast(':')
    } else {
        rawKey
    }
    val key = runCatching { normalizeCustomMetadataKey(portableKey) }.getOrNull()
        ?: return@mapNotNull null
    if (isReservedCustomMetadataKey(key) ||
        key in HIDDEN_TECHNICAL_METADATA_KEYS ||
        HIDDEN_TECHNICAL_METADATA_PREFIXES.any(key::startsWith) ||
        values.size != 1
    ) {
        return@mapNotNull null
    }
    val value = values.single().trim()
    if (value.isEmpty() || value.length > MAX_CUSTOM_FIELD_VALUE_LENGTH) return@mapNotNull null
    CustomMetadataField(key, value)
}.sortedBy(CustomMetadataField::key)
