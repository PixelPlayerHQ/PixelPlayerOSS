package com.lostf1sh.pixelplayeross.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag

class CustomMetadataTest {

    @Test
    fun `editing the displayed DATE updates the native release date`() {
        listOf(ID3v24Tag(), Mp4Tag()).forEach { tag ->
            tag.setField(FieldKey.YEAR, "2020")

            tag.applyCustomMetadataField(CustomMetadataFieldUpdate("DATE", "2024"))

            assertEquals("2024", tag.getFirst(FieldKey.YEAR), tag.javaClass.simpleName)
        }
    }

    @Test
    fun `removing the displayed DATE removes the native release date`() {
        listOf(ID3v24Tag(), Mp4Tag()).forEach { tag ->
            tag.setField(FieldKey.YEAR, "2020")

            tag.applyCustomMetadataField(CustomMetadataFieldUpdate("DATE", null))

            assertEquals("", tag.getFirst(FieldKey.YEAR), tag.javaClass.simpleName)
        }
    }

    @Test
    fun `rating uses the native scale of each supported tag family`() {
        assertEquals("0", encodeRatingForTag(0, MetadataTagFamily.ID3))
        assertEquals("255", encodeRatingForTag(5, MetadataTagFamily.ID3))
        assertEquals("80", encodeRatingForTag(4, MetadataTagFamily.MP4))
        assertEquals("3", encodeRatingForTag(3, MetadataTagFamily.VORBIS))

        assertEquals(5, decodeRatingFromTag("255", MetadataTagFamily.ID3))
        assertEquals(4, decodeRatingFromTag("80", MetadataTagFamily.MP4))
        assertEquals(3, decodeRatingFromTag("3", MetadataTagFamily.VORBIS))
    }

    @Test
    fun `container mapping limits custom writes to formats with a defined tag representation`() {
        assertEquals(MetadataTagFamily.ID3, metadataTagFamily("mp3"))
        assertEquals(MetadataTagFamily.VORBIS, metadataTagFamily("FLAC"))
        assertEquals(MetadataTagFamily.MP4, metadataTagFamily(".m4a"))
        assertEquals(MetadataTagFamily.UNSUPPORTED, metadataTagFamily("wma"))
    }

    @Test
    fun `validation normalizes safe custom keys and rejects built in fields`() {
        val valid = validateCustomMetadataChanges(
            CustomMetadataChanges(
                rating = MetadataValueUpdate.Set(4),
                fields = listOf(CustomMetadataFieldUpdate(" mood ", "  Reflective "))
            )
        ).getOrThrow()

        assertEquals(
            listOf(CustomMetadataFieldUpdate("MOOD", "Reflective")),
            valid.fields
        )

        val reserved = validateCustomMetadataChanges(
            CustomMetadataChanges(
                fields = listOf(CustomMetadataFieldUpdate("album_artist", "Do not overwrite me"))
            )
        )
        assertTrue(reserved.isFailure)
    }

    @Test
    fun `validation rejects duplicate keys and ratings outside zero to five`() {
        val duplicates = validateCustomMetadataChanges(
            CustomMetadataChanges(
                fields = listOf(
                    CustomMetadataFieldUpdate("MOOD", "Quiet"),
                    CustomMetadataFieldUpdate("mood", "Loud")
                )
            )
        )
        assertTrue(duplicates.isFailure)

        val invalidRating = validateCustomMetadataChanges(
            CustomMetadataChanges(rating = MetadataValueUpdate.Set(6))
        )
        assertTrue(invalidRating.isFailure)
    }

    @Test
    fun `editor diff keeps unread rating and emits removals for deleted custom fields`() {
        val unread = buildCustomMetadataChanges(
            metadataWasRead = false,
            originalRating = null,
            editedRating = null,
            originalFields = emptyList(),
            editedFields = emptyList()
        ).getOrThrow()
        assertInstanceOf(MetadataValueUpdate.Keep::class.java, unread.rating)

        val zeroRating = buildCustomMetadataChanges(
            metadataWasRead = true,
            originalRating = null,
            editedRating = 0,
            originalFields = emptyList(),
            editedFields = emptyList()
        ).getOrThrow()
        assertEquals(MetadataValueUpdate.Set(0), zeroRating.rating)

        val edited = buildCustomMetadataChanges(
            metadataWasRead = true,
            originalRating = 3,
            editedRating = null,
            originalFields = listOf(
                CustomMetadataField("MOOD", "Reflective"),
                CustomMetadataField("COMMENT", "Old")
            ),
            editedFields = listOf(CustomMetadataField("MOOD", "Energetic"))
        ).getOrThrow()

        assertInstanceOf(MetadataValueUpdate.Clear::class.java, edited.rating)
        assertEquals(
            listOf(
                CustomMetadataFieldUpdate("MOOD", "Energetic"),
                CustomMetadataFieldUpdate("COMMENT", null)
            ),
            edited.fields
        )
        assertTrue(edited.hasChanges)
    }

    @Test
    fun `property map exposes user fields without duplicating fixed metadata`() {
        val fields = extractEditableCustomMetadataFields(
            mapOf(
                "TITLE" to arrayOf("Song"),
                "ARTIST" to arrayOf("Artist"),
                "RATING" to arrayOf("4"),
                "MOOD" to arrayOf("Reflective"),
                "----:com.apple.iTunes:LISTENING_CONTEXT" to arrayOf("Commute"),
                "EMPTY" to arrayOf(""),
                "MULTI" to arrayOf("A", "B")
            )
        )

        assertEquals(
            listOf(
                CustomMetadataField("LISTENING_CONTEXT", "Commute"),
                CustomMetadataField("MOOD", "Reflective")
            ),
            fields
        )
        assertFalse(fields.any { it.key == "TITLE" || it.key == "RATING" })
    }
}
