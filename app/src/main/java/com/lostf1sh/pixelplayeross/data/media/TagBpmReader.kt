package com.lostf1sh.pixelplayeross.data.media

import timber.log.Timber
import java.io.RandomAccessFile

/**
 * Reads an embedded BPM tag directly from a local audio file, without decoding any audio.
 * Near-free (a handful of small seeks/reads), but coverage is limited to files that were
 * tagged by DJ software (Serato/rekordbox/Mixed In Key, etc.) or a tagger that writes a
 * tempo field.
 *
 * Supports the two containers most likely to carry a BPM tag in a local library:
 *  - MP3 (ID3v2.3/2.4 `TBPM` frame)
 *  - FLAC (Vorbis comment `BPM`/`TBPM` field)
 *
 * M4A (`tmpo` atom) and Ogg Vorbis are not covered yet — both need container-specific box/page
 * parsing that isn't worth the complexity until there's evidence real libraries need it.
 */
object TagBpmReader {

    /** Best-effort: returns the tagged BPM, or null if absent/unsupported/unparsable. */
    fun readBpm(filePath: String): Float? {
        return try {
            RandomAccessFile(filePath, "r").use { raf ->
                val magic = ByteArray(4)
                if (raf.read(magic) != 4) return null
                when {
                    magic[0] == ID3_MAGIC[0] && magic[1] == ID3_MAGIC[1] && magic[2] == ID3_MAGIC[2] ->
                        // magic[3] is byte 3 of the 10-byte ID3v2 header: the major version.
                        readId3v2Bpm(raf, majorVersion = magic[3].toInt() and 0xFF)
                    magic.contentEquals(FLAC_MAGIC) -> readFlacBpm(raf)
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Tag BPM read failed for %s", filePath)
            null
        }
    }

    // region ID3v2 (MP3)

    private fun readId3v2Bpm(raf: RandomAccessFile, majorVersion: Int): Float? {
        // 10-byte ID3v2 header: "ID3" + major + revision + flags + syncsafe size. The caller
        // consumed the first 4 bytes ("ID3" + major); the remaining 6 are revision(1),
        // flags(1), size(4).
        val rest = ByteArray(6)
        if (raf.read(rest) != 6) return null
        val flags = rest[1].toInt() and 0xFF
        val tagSize = syncsafeInt(rest, 2)
        if (majorVersion < 3 || tagSize <= 0) return null // v2.2 uses 3-byte frame ids; unsupported

        val tagEnd = raf.filePointer + tagSize
        if (flags and 0x40 != 0) { // extended header present
            val extHeader = ByteArray(4)
            if (raf.read(extHeader) == 4) {
                val extSize = if (majorVersion >= 4) syncsafeInt(extHeader, 0) else beInt(extHeader, 0)
                if (extSize in 1 until tagSize) raf.seek(raf.filePointer + extSize - 4)
            }
        }

        val frameHeader = ByteArray(10)
        while (raf.filePointer + 10 <= tagEnd) {
            if (raf.read(frameHeader) != 10) break
            if (frameHeader[0] == ZERO_BYTE) break // padding reached

            val id = String(frameHeader, 0, 4, Charsets.US_ASCII)
            val frameSize = if (majorVersion >= 4) syncsafeInt(frameHeader, 4) else beInt(frameHeader, 4)
            if (frameSize <= 0 || raf.filePointer + frameSize > tagEnd) break

            if (id == "TBPM") {
                val content = ByteArray(frameSize)
                if (raf.read(content) != frameSize) return null
                return parseId3TextFrame(content)?.toFloatOrNull()
            } else {
                raf.seek(raf.filePointer + frameSize)
            }
        }
        return null
    }

    private fun parseId3TextFrame(content: ByteArray): String? {
        if (content.isEmpty()) return null
        val encoding = content[0].toInt() and 0xFF
        val body = content.copyOfRange(1, content.size)
        val text = when (encoding) {
            1 -> String(body, Charsets.UTF_16) // includes BOM
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }
        // Trim ID3 padding (NUL) and ordinary whitespace, then keep just the leading number —
        // some taggers write "128 BPM" or "128.00 bpm" instead of a bare value.
        val cleaned = text.trim { ch -> ch.code == 0 || ch.isWhitespace() }
        if (cleaned.isEmpty()) return null
        return NUMBER_PREFIX.find(cleaned)?.value
    }

    /** ID3v2.4 frame/tag sizes are "syncsafe": 7 significant bits per byte. */
    private fun syncsafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun beInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    // endregion

    // region FLAC

    private fun readFlacBpm(raf: RandomAccessFile): Float? {
        val blockHeader = ByteArray(4)
        while (true) {
            if (raf.read(blockHeader) != 4) return null
            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val blockLength = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                (blockHeader[3].toInt() and 0xFF)
            if (blockLength < 0) return null

            if (blockType == FLAC_VORBIS_COMMENT_BLOCK) {
                val block = ByteArray(blockLength)
                if (raf.read(block) != blockLength) return null
                return parseVorbisCommentBpm(block)
            }

            raf.seek(raf.filePointer + blockLength)
            if (isLast) return null
        }
    }

    private fun parseVorbisCommentBpm(block: ByteArray): Float? {
        var pos = 0
        fun readLeInt(): Int {
            val v = (block[pos].toInt() and 0xFF) or
                ((block[pos + 1].toInt() and 0xFF) shl 8) or
                ((block[pos + 2].toInt() and 0xFF) shl 16) or
                ((block[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }
        if (block.size < 8) return null
        val vendorLength = readLeInt()
        pos += vendorLength
        if (pos + 4 > block.size) return null
        val commentCount = readLeInt()
        repeat(commentCount) {
            if (pos + 4 > block.size) return null
            val len = readLeInt()
            if (len < 0 || pos + len > block.size) return null
            val comment = String(block, pos, len, Charsets.UTF_8)
            pos += len
            val eq = comment.indexOf(EQUALS_CHAR)
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                if (key == "BPM" || key == "TBPM") {
                    return comment.substring(eq + 1).trim().toFloatOrNull()
                }
            }
        }
        return null
    }

    // endregion

    private const val TAG = "TagBpmReader"
    private const val FLAC_VORBIS_COMMENT_BLOCK = 4
    private const val EQUALS_CHAR = '='
    private val ZERO_BYTE: Byte = 0
    private val ID3_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
    private val FLAC_MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
    private val NUMBER_PREFIX = Regex("^[0-9]+([.][0-9]+)?")
}
