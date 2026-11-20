package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioMetaUtilsCodecMimeTypeTest {

    @Test
    fun `ogg container with opus extension resolves to opus`() {
        assertEquals("audio/opus", AudioMetaUtils.resolveCodecMimeType("audio/ogg", "/music/a.opus"))
        assertEquals("audio/opus", AudioMetaUtils.resolveCodecMimeType("application/ogg", "/music/B.OPUS"))
    }

    @Test
    fun `ogg vorbis files keep their mime type`() {
        assertEquals("audio/ogg", AudioMetaUtils.resolveCodecMimeType("audio/ogg", "/music/a.ogg"))
        assertEquals("audio/ogg", AudioMetaUtils.resolveCodecMimeType("audio/ogg", "/music.opus/track"))
    }

    @Test
    fun `non ogg or missing mime types are unchanged`() {
        assertEquals("audio/flac", AudioMetaUtils.resolveCodecMimeType("audio/flac", "/music/a.opus"))
        assertNull(AudioMetaUtils.resolveCodecMimeType(null, "/music/a.opus"))
    }
}
