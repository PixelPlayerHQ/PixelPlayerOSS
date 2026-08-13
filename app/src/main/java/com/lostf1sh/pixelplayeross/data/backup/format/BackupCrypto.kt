package com.lostf1sh.pixelplayeross.data.backup.format

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when an encrypted backup is opened without providing a passphrase. */
class BackupEncryptedException :
    Exception("This backup is encrypted and requires a password.")

/** Thrown when decryption fails, which almost always means a wrong passphrase. */
class BackupWrongPassphraseException :
    Exception("Incorrect password, or the backup file is corrupted.")

/**
 * Passphrase-based encryption for .pxpl archives.
 *
 * Encrypted layout: `PXPL` magic, then the [ENC_MARKER] bytes, a 16-byte
 * random salt, a 12-byte random GCM IV, a big-endian iteration count, and
 * finally the AES-256-GCM ciphertext of the exact byte stream a plain v3
 * archive would carry after its magic (the ZIP). Decrypting therefore yields
 * a byte-identical v3 archive, so the whole validation/restore pipeline runs
 * unchanged on the decrypted result.
 *
 * Key derivation is PBKDF2-HMAC-SHA256; the iteration count is stored in the
 * header so it can be raised later without breaking old backups.
 */
object BackupCrypto {
    /** Bytes 4..5 after the PXPL magic identify the encrypted container. */
    val ENC_MARKER = byteArrayOf('E'.code.toByte(), 'C'.code.toByte(), '0'.code.toByte(), '1'.code.toByte())

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val MAX_HEADER_ITERATIONS = 5_000_000

    const val HEADER_BYTES_AFTER_MAGIC = 4 + SALT_BYTES + IV_BYTES + 4

    private val secureRandom = SecureRandom()

    /**
     * Writes the encryption header to [raw] (which must already be positioned
     * right after the PXPL magic) and returns a stream that encrypts
     * everything written to it.
     */
    fun encryptingStream(raw: OutputStream, passphrase: String): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)

        raw.write(ENC_MARKER)
        raw.write(salt)
        raw.write(iv)
        raw.write(ByteBuffer.allocate(4).putInt(PBKDF2_ITERATIONS).array())

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, PBKDF2_ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherOutputStream(raw, cipher)
    }

    /**
     * Reads the encryption header from [raw] (which must already be positioned
     * right after the PXPL magic) and returns a stream that decrypts the rest.
     * GCM authentication failures surface when the stream is fully consumed.
     */
    fun decryptingStream(raw: InputStream, passphrase: String): InputStream {
        val marker = readFully(raw, ENC_MARKER.size)
        require(marker.contentEquals(ENC_MARKER)) { "Not an encrypted PixelPlayer backup." }
        val salt = readFully(raw, SALT_BYTES)
        val iv = readFully(raw, IV_BYTES)
        val iterations = ByteBuffer.wrap(readFully(raw, 4)).int
        require(iterations in 1..MAX_HEADER_ITERATIONS) { "Corrupted encryption header." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherInputStream(raw, cipher)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        spec.clearPassword()
        return SecretKeySpec(key.encoded, "AES")
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read == -1) throw IllegalArgumentException("Encrypted backup header is truncated.")
            offset += read
        }
        return buffer
    }
}
