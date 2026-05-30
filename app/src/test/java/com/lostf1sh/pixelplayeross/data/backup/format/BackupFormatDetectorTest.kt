package com.lostf1sh.pixelplayeross.data.backup.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupFormatDetectorTest {

    private val detector = BackupFormatDetector()

    @Test
    fun `detects v3 PXPL ZIP format`() {
        // PXPL magic + ZIP header (PK\x03\x04)
        val header = byteArrayOf(
            'P'.code.toByte(), 'X'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(),
            0x50, 0x4B, 0x03, 0x04
        )
        assertEquals(BackupFormatDetector.Format.PXPL_V3_ZIP, detector.detect(header))
    }

    @Test
    fun `detects v2 PXPL GZIP format`() {
        // PXPL magic + GZIP magic (1f 8b)
        val header = byteArrayOf(
            'P'.code.toByte(), 'X'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(),
            0x1f, 0x8b.toByte(), 0x08, 0x00
        )
        assertEquals(BackupFormatDetector.Format.PXPL_V2_GZIP, detector.detect(header))
    }

    @Test
    fun `detects legacy GZIP format`() {
        val header = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals(BackupFormatDetector.Format.LEGACY_GZIP, detector.detect(header))
    }

    @Test
    fun `detects legacy raw JSON format`() {
        val header = "{ \"formatVersion\": 1".toByteArray(Charsets.UTF_8)
        assertEquals(BackupFormatDetector.Format.LEGACY_RAW, detector.detect(header))
    }

    @Test
    fun `returns UNKNOWN for unrecognized format`() {
        val header = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(header))
    }

    @Test
    fun `returns UNKNOWN for empty input`() {
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(byteArrayOf()))
    }

    @Test
    fun `returns UNKNOWN for too-short input`() {
        val header = byteArrayOf('P'.code.toByte(), 'X'.code.toByte())
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(header))
    }
}
