package com.lostf1sh.pixelplayeross.data.backup.format

import java.io.InputStream

class BackupFormatDetector {

    enum class Format {
        PXPL_V4_ENCRYPTED,
        PXPL_V3_ZIP,
        PXPL_V2_GZIP,
        LEGACY_GZIP,
        LEGACY_RAW,
        UNKNOWN
    }

    fun detect(header: ByteArray): Format {
        if (header.size < 4) return Format.UNKNOWN

        val hasPxplMagic = header[0] == 'P'.code.toByte() &&
            header[1] == 'X'.code.toByte() &&
            header[2] == 'P'.code.toByte() &&
            header[3] == 'L'.code.toByte()

        if (hasPxplMagic) {
            if (header.size < 8) return Format.PXPL_V2_GZIP
            val afterMagic0 = header[4]
            val afterMagic1 = header[5]
            if (afterMagic0 == 'P'.code.toByte() && afterMagic1 == 'K'.code.toByte()) {
                return Format.PXPL_V3_ZIP
            }
            // Encrypted container marker: EC01 (see BackupCrypto)
            if (afterMagic0 == 'E'.code.toByte() && afterMagic1 == 'C'.code.toByte()) {
                return Format.PXPL_V4_ENCRYPTED
            }
            if (afterMagic0 == 0x1f.toByte() && afterMagic1 == 0x8b.toByte()) {
                return Format.PXPL_V2_GZIP
            }
            return Format.PXPL_V2_GZIP
        }

        if (header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
            return Format.LEGACY_GZIP
        }

        if (header[0] == '{'.code.toByte()) {
            return Format.LEGACY_RAW
        }

        return Format.UNKNOWN
    }

    fun readHeader(inputStream: InputStream, size: Int = 8): ByteArray {
        val buffer = ByteArray(size)
        val bytesRead = inputStream.read(buffer)
        return if (bytesRead < size) buffer.copyOf(bytesRead) else buffer
    }

    companion object {
        val PXPL_MAGIC = byteArrayOf(
            'P'.code.toByte(),
            'X'.code.toByte(),
            'P'.code.toByte(),
            'L'.code.toByte()
        )
        const val PXPL_MAGIC_SIZE = 4
    }
}
